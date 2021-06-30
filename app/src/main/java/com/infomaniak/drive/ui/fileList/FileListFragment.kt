/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.infomaniak.drive.ui.fileList

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.ActionMultiSelectBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.ActionMultiSelectBottomSheetDialog.Companion.SELECT_DIALOG_ACTION
import com.infomaniak.drive.ui.bottomSheetDialogs.ActionMultiSelectBottomSheetDialog.SelectDialogAction
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.setPagination
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cardview_file_list.*
import kotlinx.android.synthetic.main.empty_icon_layout.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_add_file.*
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_new_folder.*
import kotlinx.android.synthetic.main.fragment_new_folder.toolbar
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.coroutines.*

open class FileListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    protected lateinit var fileAdapter: FileAdapter
    protected val fileListViewModel: FileListViewModel by viewModels()
    protected val mainViewModel: MainViewModel by activityViewModels()

    private val navigationArgs: FileListFragmentArgs by navArgs()

    internal var folderID = ROOT_ID
    internal var folderName: String = "/"
    private var currentFolder: File? = null

    private var ignoreCreateFolderStack: Boolean = false
    private var isLoadingActivities = false
    private var isDownloading = false

    protected lateinit var timer: CountDownTimer
    protected open var downloadFiles: (ignoreCache: Boolean) -> Unit = DownloadFiles()
    protected open var sortFiles: () -> Unit = SortFiles()
    protected open var enabledMultiSelectMode = true
    protected open var hideBackButtonWhenRoot: Boolean = true
    protected open var showPendingFiles = true

    protected var sortType: File.SortType = File.SortType.NAME_AZ
    protected var userDrive: UserDrive? = null

    companion object {
        const val FILE_ID = "file_id"
        const val REFRESH_FAVORITE_FILE = "force_list_refresh"
        const val CANCELLABLE_MAIN_KEY = "cancellable_main"
        const val CANCELLABLE_TITLE_KEY = "cancellable_message"
        const val CANCELLABLE_ACTION_KEY = "cancellable_action"
        const val SORT_TYPE_OPTION_KEY = "sort_type_option"
        const val DELETE_NOT_UPDATE_ACTION = "is_update_not_delete_action"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sortType = UISettings(requireContext()).sortType
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        folderID = navigationArgs.folderID
        folderName = if (folderID == ROOT_ID) AccountUtils.getCurrentDrive()?.name ?: "/" else navigationArgs.folderName
        ignoreCreateFolderStack = navigationArgs.ignoreCreateFolderStack
        return inflater.inflate(R.layout.fragment_file_list, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timer = Utils.createRefreshTimer {
            swipeRefreshLayout?.isRefreshing = true
        }

        mainViewModel.intentShowProgressByFolderId.observe(viewLifecycleOwner) {
            it?.let { intentFolderId ->
                FileController.getFileById(intentFolderId)?.let { file ->
                    safeNavigate(
                        FileListFragmentDirections
                            .fileListFragmentToFileListFragment(file.id, file.name)
                    )
                }
            }
        }

        getBackNavigationResult<Bundle>(CANCELLABLE_MAIN_KEY) { bundle ->
            bundle.getString(CANCELLABLE_TITLE_KEY)?.let { title ->
                bundle.getParcelable<CancellableAction>(CANCELLABLE_ACTION_KEY)?.let { action ->
                    val fileID = bundle.getInt(FILE_ID)

                    if (bundle.containsKey(DELETE_NOT_UPDATE_ACTION)) {
                        if (bundle.getBoolean(DELETE_NOT_UPDATE_ACTION)) {
                            fileAdapter.deleteByFileId(fileID)
                            checkIfNoFiles()
                        } else fileAdapter.notifyFileChanged(fileID)
                    }

                    requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab) {
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (ApiRepository.cancelAction(action).data == true && isResumed) {
                                withContext(Dispatchers.Main) {
                                    refreshActivities()
                                }
                            }
                        }
                    }
                } ?: also {
                    requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab)
                }
            }
        }

        getBackNavigationResult<ApiResponse.Status>(ManageDropboxFragment.MANAGE_DROPBOX_SUCCESS) { result ->
            if (result == ApiResponse.Status.SUCCESS) onRefresh()
        }

        getBackNavigationResult<Int>(REFRESH_FAVORITE_FILE) { fileID ->
            if (findNavController().currentDestination?.id == R.id.favoritesFragment) {
                fileAdapter.deleteByFileId(fileID)
            } else {
                fileAdapter.notifyFileChanged(fileID) { file ->
                    file.isFavorite = !file.isFavorite
                }
            }
        }

        mainViewModel.createDropBoxSuccess.observe(viewLifecycleOwner) { dropBox ->
            onRefresh()
            safeNavigate(
                FileListFragmentDirections.actionFileListFragmentToDropBoxResultBottomSheetDialog(
                    url = dropBox.url,
                    name = dropBox.alias
                )
            )
        }

        noFilesLayout.setup(title = R.string.noFilesDescription, initialListView = fileRecyclerView) {
            fileListViewModel.cancelDownloadFiles()
            downloadFiles(false)
        }

        if ((folderID == ROOT_ID || folderID == OTHER_ROOT_ID) && hideBackButtonWhenRoot) toolbar.navigationIcon = null
        toolbar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.searchItem -> safeNavigate(FileListFragmentDirections.actionFileListFragmentToSearchFragment())
                R.id.restartItem -> onRestartItemsClicked()
                R.id.closeItem -> onCloseItemsClicked()
            }
            true
        }
        toolbar.setNavigationOnClickListener {
            Utils.ignoreCreateFolderBackStack(findNavController(), ignoreCreateFolderStack)
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (fileAdapter.multiSelectMode) {
                closeMultiSelect()
            } else {
                Utils.ignoreCreateFolderBackStack(findNavController(), ignoreCreateFolderStack)
            }
        }

        swipeRefreshLayout.setOnRefreshListener(this)
        collapsingToolbarLayout.title = folderName
        ViewCompat.requestApplyInsets(fileListCoordinator) // Restore coordinator state

        setupFileAdapter()

        if (enabledMultiSelectMode) setupMultiSelect()

        sortFiles()
        setupDisplay()

        if (!isDownloading) downloadFiles(false)
        observeOfflineDownloadProgress()

        mainViewModel.refreshActivities.observe(viewLifecycleOwner) {
            it?.let {
                showPendingFiles()
                when (findNavController().currentDestination?.id) {
                    R.id.searchFragment -> Unit
                    R.id.sharedWithMeFragment -> onRefresh()
                    else -> refreshActivities()
                }
            }
        }

        toolbar?.menu?.findItem(R.id.searchItem)?.isVisible = findNavController().currentDestination?.id == R.id.fileListFragment
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        onSelectFolderResult(requestCode, resultCode, data)
    }

    private fun setupMultiSelect() {
        fileAdapter.enabledMultiSelectMode = true
        closeButtonMultiSelect.setOnClickListener { closeMultiSelect() }
        deleteButtonMultiSelect.setOnClickListener {
            val selectedFiles = fileAdapter.itemSelected
            val fileName = if (selectedFiles.size == 1) fileAdapter.itemSelected.first().getFileName() else null
            Utils.confirmFileDeletion(requireContext(), fileName = fileName, deletionCount = selectedFiles.size) { dialog ->
                val mediator = mainViewModel.createMultiSelectMediator()
                val selectedCount = selectedFiles.count()
                enableButtonMultiSelect(false)

                selectedFiles.forEach { file ->
                    val onSuccess: (Int) -> Unit = { fileID ->
                        runBlocking(Dispatchers.Main) { fileAdapter.deleteByFileId(fileID) }
                    }
                    mediator.addSource(
                        mainViewModel.deleteFile(requireContext(), file, onSuccess),
                        mainViewModel.updateMultiSelectMediator(mediator)
                    )
                }

                mediator.observe(viewLifecycleOwner) { (success, total) ->
                    dialog.dismiss()
                    if (total == selectedCount) {
                        val title = if (success == 0) {
                            getString(R.string.anErrorHasOccurred)
                        } else {
                            resources.getQuantityString(
                                R.plurals.snackbarMoveTrashConfirmation,
                                success,
                                success
                            )
                        }
                        requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab)
                        refreshActivities()
                        closeMultiSelect()
                    }
                }
            }
        }

        moveButtonMultiSelect.setOnClickListener { Utils.moveFileClicked(this, folderID) }
        menuButtonMultiSelect.setOnClickListener {
            safeNavigate(
                FileListFragmentDirections.actionFileListFragmentToActionMultiSelectBottomSheetDialog(
                    fileIds = fileAdapter.itemSelected.map { it.id }.toIntArray(),
                    onlyFolders = fileAdapter.itemSelected.all { it.isFolder() }
                )
            )
        }
        selectAllButton.setOnClickListener { /*TODO in future version*/ }

        getBackNavigationResult<Boolean>(ActionMultiSelectBottomSheetDialog.DISABLE_SELECT_MODE) {
            if (it) closeMultiSelect()
        }
    }

    private fun setupDisplay() {
        toggleDisplayButton.setOnClickListener {
            val newListMode = !UISettings(requireContext()).listMode
            UISettings(requireContext()).listMode = newListMode
            fileListViewModel.isListMode.value = newListMode
        }
        fileListViewModel.isListMode.observe(viewLifecycleOwner) {
            setupDisplayMode(it)
        }
        fileListViewModel.isListMode.value = UISettings(requireContext()).listMode

        sortButton.setText(sortType.translation)
        sortButton.setOnClickListener {
            safeNavigate(R.id.sortFilesBottomSheetDialog, bundleOf("sortType" to sortType))
        }
    }

    private fun setupFileAdapter() {
        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            fileAdapter.toggleOfflineMode(!isInternetAvailable)
            noNetwork.visibility = if (isInternetAvailable) GONE else VISIBLE
        }

        fileAdapter = FileAdapter()
        fileAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        fileAdapter.onFileClicked = { file ->
            when {
                file.isFolder() -> {
                    if (file.isDisabled()) {
                        safeNavigate(
                            FileListFragmentDirections.actionFileListFragmentToAccessDeniedBottomSheetFragment(
                                AccountUtils.getCurrentDrive()?.isUserAdmin() ?: false,
                                file.id
                            )
                        )
                    } else {
                        fileListViewModel.cancelDownloadFiles()
                        safeNavigate(
                            FileListFragmentDirections.fileListFragmentToFileListFragment(
                                file.id, file.name
                            )
                        )
                    }
                }
                else -> Utils.displayFile(mainViewModel, findNavController(), file, fileAdapter.getItems())
            }
        }

        fileAdapter.openMultiSelectMode = { openMultiSelect() }

        fileAdapter.updateMultiSelectMode = { onUpdateMultiSelect() }

        fileAdapter.onMenuClicked = { file ->
            val bundle = bundleOf(
                "fileId" to file.id,
                "userDrive" to UserDrive(driveId = file.driveId, sharedWithMe = fileListViewModel.isSharedWithMe)
            )
            safeNavigate(R.id.fileInfoActionsBottomSheetDialog, bundle)
        }

        onBackNavigationResult()
        fileRecyclerView.adapter = fileAdapter
        fileRecyclerView.setPagination({
            if (!fileAdapter.isComplete) fileAdapter.showLoading()
        })

        mainViewModel.updateOfflineFile.observe(viewLifecycleOwner) {
            it?.let { (fileId, isOffline) -> fileAdapter.notifyFileChanged(fileId) { file -> file.isOffline = isOffline } }
        }
    }

    private fun onBackNavigationResult() {
        getBackNavigationResult<SelectDialogAction>(SELECT_DIALOG_ACTION) { type ->
            val mediator = mainViewModel.createMultiSelectMediator()
            val itemSelectedCount = fileAdapter.itemSelected.count()
            enableButtonMultiSelect(false)

            fileAdapter.itemSelected.forEach { file ->
                val observer: (apiResponse: ApiResponse<*>) -> Unit =
                    mainViewModel.updateMultiSelectMediator(mediator)

                when (type) {
                    SelectDialogAction.ADD_FAVORITES -> {
                        mediator.addSource(mainViewModel.addFileToFavorites(file) {
                            runBlocking(Dispatchers.Main) { fileAdapter.notifyFileChanged(file.id) { it.isFavorite = true } }
                        }, observer)
                    }
                    SelectDialogAction.OFFLINE -> {
                        addSelectedFilesToOffline(file)
                    }
                    SelectDialogAction.DUPLICATE -> {
                        val duplicateFile = mainViewModel.duplicateFile(file, mainViewModel.currentFolder.value?.id, null)
                        mediator.addSource(duplicateFile, observer)
                    }
                }
            }

            if (type == SelectDialogAction.OFFLINE) {
                closeMultiSelect()
            }

            mediator.observe(viewLifecycleOwner) { (success, total) ->
                if (total == itemSelectedCount) {
                    val message = when (type) {
                        SelectDialogAction.ADD_FAVORITES -> R.plurals.fileListAddFavorisConfirmationSnackbar
                        SelectDialogAction.OFFLINE -> R.plurals.fileListAddOfflineConfirmationSnackbar
                        SelectDialogAction.DUPLICATE -> R.plurals.fileListDuplicationConfirmationSnackbar
                    }
                    val title = resources.getQuantityString(
                        message,
                        success,
                        success
                    )
                    if (success == 0) {
                        requireActivity().showSnackbar(getString(R.string.anErrorHasOccurred), requireActivity().mainFab)
                    } else {
                        requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab)
                    }
                    refreshActivities()
                    closeMultiSelect()
                }
            }
        }
    }

    private fun addSelectedFilesToOffline(file: File) {
        if (!file.isOffline && !file.isFolder()) {
            if (!file.isOldData(requireContext())) {
                val cacheFile = file.getCacheFile(requireContext())
                val offlineFile = file.getOfflineFile(requireContext())
                Utils.moveCacheFileToOffline(file, cacheFile, offlineFile)
                runBlocking(Dispatchers.IO) { FileController.updateOfflineStatus(file.id, true) }

                fileAdapter.updateFileProgress(file.id, 100) { currentFile ->
                    currentFile.isOffline = true
                    currentFile.currentProgress = 0
                }
            } else Utils.downloadAsOfflineFile(requireContext(), file)
        }
    }

    private fun onSelectFolderResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == SelectFolderActivity.SELECT_FOLDER_REQUEST && resultCode == AppCompatActivity.RESULT_OK) {
            val folderName = data?.extras?.getString(SelectFolderActivity.FOLDER_NAME_TAG).toString()

            data?.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)?.let { folderID ->
                val mediator = mainViewModel.createMultiSelectMediator()
                val selectedCount = fileAdapter.itemSelected.count()
                enableButtonMultiSelect(false)

                fileAdapter.itemSelected.forEach { file ->
                    val newParent = File(id = folderID, driveId = AccountUtils.currentDriveId)
                    val moveFile = mainViewModel.moveFile(file, newParent) { fileID ->
                        lifecycleScope.launchWhenResumed {
                            fileAdapter.deleteByFileId(fileID)
                            checkIfNoFiles()
                        }
                    }
                    mediator.addSource(moveFile, mainViewModel.updateMultiSelectMediator(mediator))
                }

                mediator.observe(viewLifecycleOwner) { (success, total) ->
                    if (total == selectedCount) {
                        val title = if (success == 0) {
                            getString(R.string.anErrorHasOccurred)
                        } else {
                            resources.getQuantityString(
                                R.plurals.fileListMoveFileConfirmationSnackbar,
                                success,
                                success, folderName
                            )
                        }
                        requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab)
                        refreshActivities()
                        closeMultiSelect()
                    }
                }
            }
        }
    }

    private fun checkIfNoFiles() {
        changeNoFilesLayoutVisibility(
            hideFileList = fileAdapter.itemCount == 0,
            changeControlsVisibility = folderID != ROOT_ID && folderID != OTHER_ROOT_ID,
            ignoreOffline = true
        )
    }

    private fun refreshActivities() {
        if (isLoadingActivities || folderID == OTHER_ROOT_ID) return
        isLoadingActivities = true
        mainViewModel.currentFolder.value?.let { currentFolder ->
            FileController.getFileById(currentFolder.id)?.let { updatedFolder ->
                downloadFolderActivities(updatedFolder)
                isLoadingActivities = false
            } ?: kotlin.run { isLoadingActivities = false }
        } ?: kotlin.run { isLoadingActivities = false }
    }

    private fun observeOfflineDownloadProgress() {
        WorkManager
            .getInstance(requireContext().applicationContext)
            .getWorkInfosForUniqueWorkLiveData(DownloadWorker.TAG).observe(viewLifecycleOwner) { workInfoList ->
                if (workInfoList.isEmpty()) return@observe
                val workInfo =
                    workInfoList.firstOrNull { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                        ?: workInfoList.first()

                if (!workInfo.state.isFinished) {
                    val progress = workInfo.progress.getInt(DownloadWorker.PROGRESS, 0)
                    val fileId = workInfo.progress.getInt(DownloadWorker.FILE_ID, 0)
                    fileAdapter.updateFileProgress(fileId, progress) { file ->
                        file.isOffline = true
                        file.currentProgress = 0
                    }
                    Log.i("kDrive", "progress from fragment $progress% for file $fileId")
                }
            }

        mainViewModel.fileCancelledFromDownload.observe(viewLifecycleOwner) { fileId ->
            fileAdapter.updateFileProgress(fileId, -1) { file ->
                file.isOffline = false
                file.currentProgress = 0
            }
        }
    }

    override fun onRefresh() {
        fileListViewModel.cancelDownloadFiles()
        downloadFiles(true)
    }

    private fun showPendingFiles() {
        if (!showPendingFiles) return
        uploadFileInProgress.apply {
            fileListViewModel.getPendingFilesCount(folderID).observe(viewLifecycleOwner) { pendingFilesCount ->
                val radius = resources.getDimension(R.dimen.cardViewRadius)

                if (pendingFilesCount > 0L) {
                    (this as MaterialCardView).shapeAppearanceModel = shapeAppearanceModel.toBuilder()
                        .setTopLeftCorner(CornerFamily.ROUNDED, radius)
                        .setTopRightCorner(CornerFamily.ROUNDED, radius)
                        .setBottomLeftCorner(CornerFamily.ROUNDED, radius)
                        .setBottomRightCorner(CornerFamily.ROUNDED, radius)
                        .build()

                    fileName.setText(R.string.uploadInThisFolderTitle)
                    fileSize.text = resources.getQuantityString(
                        R.plurals.uploadInProgressNumberFile,
                        pendingFilesCount,
                        pendingFilesCount
                    )
                    filePreview.visibility = GONE
                    fileProgression.visibility = VISIBLE
                    visibility = VISIBLE

                    setOnClickListener {
                        goToUploadInProgress(folderID)
                    }
                } else {
                    visibility = GONE
                }
            }
        }
    }

    private fun goToUploadInProgress(folderId: Int) {
        safeNavigate(
            R.id.uploadInProgressFragment, bundleOf(
                "folderID" to folderId,
                "folderName" to getString(R.string.uploadInProgressTitle)
            )
        )
    }

    private fun setupDisplayMode(isListMode: Boolean) {
        fileRecyclerView.layoutManager =
            if (isListMode) LinearLayoutManager(requireContext()) else GridLayoutManager(requireContext(), 2)
        toggleDisplayButton.icon = ContextCompat.getDrawable(
            requireContext(),
            if (isListMode) R.drawable.ic_list else R.drawable.ic_grid
        )
        fileAdapter.viewHolderType = if (isListMode) FileAdapter.DisplayType.LIST else FileAdapter.DisplayType.GRID
        fileRecyclerView.adapter = fileAdapter
    }

    private fun openMultiSelect() {
        fileAdapter.multiSelectMode = true
        fileAdapter.notifyItemRangeChanged(0, fileAdapter.itemCount)
        collapsingToolbarLayout.visibility = GONE
        multiSelectLayout.visibility = VISIBLE
    }

    private fun onUpdateMultiSelect() {
        val fileSelectedNumber = fileAdapter.itemSelected.size
        when (fileSelectedNumber) {
            0, 1 -> {
                val isEnabled = fileSelectedNumber == 1
                enableButtonMultiSelect(isEnabled)
            }
        }
        titleMultiSelect.text = resources.getQuantityString(
            R.plurals.fileListMultiSelectedTitle,
            fileSelectedNumber,
            fileSelectedNumber
        )
    }

    private fun enableButtonMultiSelect(isEnabled: Boolean) {
        deleteButtonMultiSelect.isEnabled = isEnabled
        moveButtonMultiSelect.isEnabled = isEnabled
        menuButtonMultiSelect.isEnabled = isEnabled
        selectAllButton.isEnabled = isEnabled
    }

    private fun closeMultiSelect() {
        fileAdapter.itemSelected.clear()
        fileAdapter.multiSelectMode = false
        fileAdapter.notifyItemRangeChanged(0, fileAdapter.itemCount)
        collapsingToolbarLayout.visibility = VISIBLE
        multiSelectLayout.visibility = GONE
    }

    private fun downloadFolderActivities(currentFolder: File) {
        fileListViewModel.getFolderActivities(currentFolder, userDrive).observe(viewLifecycleOwner) { activities ->
            if (activities?.isNotEmpty() == true) {
                getFolderFiles(
                    ignoreCache = false,
                    onFinish = {
                        it?.let { (_, files, _) ->
                            fileAdapter.addActivities(files, activities)
                            changeNoFilesLayoutVisibility(
                                hideFileList = files.isEmpty(),
                                changeControlsVisibility = !currentFolder.isRoot()
                            )
                        }
                    })
            }
        }
    }

    private fun getFolderFiles(
        ignoreCache: Boolean,
        onFinish: ((FolderFilesResult?) -> Unit)? = null
    ) {
        showPendingFiles()
        fileListViewModel.getFiles(
            folderID,
            ignoreCache = ignoreCache,
            ignoreCloud = mainViewModel.isInternetAvailable.value == false,
            order = sortType,
            userDrive = userDrive
        ).observe(viewLifecycleOwner) {
            onFinish?.invoke(it)
        }
    }

    private inner class SortFiles : () -> Unit {
        override fun invoke() {
            getBackNavigationResult<File.SortType>(SORT_TYPE_OPTION_KEY) { newSortType ->
                sortType = newSortType
                sortButton.setText(sortType.translation)
                downloadFiles(fileListViewModel.isSharedWithMe)
                UISettings(requireContext()).sortType = newSortType
            }
        }
    }

    private inner class DownloadFiles : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            if (ignoreCache) fileAdapter.setList(arrayListOf())
            timer.start()
            isDownloading = true
            fileAdapter.isComplete = false
            getFolderFiles(ignoreCache, onFinish = {
                it?.let { result ->
                    if (fileAdapter.itemCount == 0 || result.page == 1) {
                        currentFolder = if (result.parentFolder?.id == ROOT_ID) {
                            AccountUtils.getCurrentDrive()?.convertToFile(Utils.getRootName(requireContext()))
                        } else result.parentFolder
                        mainViewModel.currentFolder.value = currentFolder
                        changeNoFilesLayoutVisibility(
                            hideFileList = result.files.isEmpty(),
                            changeControlsVisibility = result.parentFolder?.isRoot() == false
                        )
                        fileAdapter.setList(result.files)
                        result.parentFolder?.let { parent -> downloadFolderActivities(parent) }

                    } else fileRecyclerView.post { fileAdapter.addFileList(result.files) }
                    fileAdapter.isComplete = result.isComplete
                } ?: run {
                    changeNoFilesLayoutVisibility(
                        hideFileList = fileAdapter.itemCount == 0,
                        changeControlsVisibility = folderID != ROOT_ID
                    )
                    fileAdapter.isComplete = true
                }
                isDownloading = false
                timer.cancel()
                swipeRefreshLayout.isRefreshing = false
            })
        }
    }

    data class FolderFilesResult(
        val parentFolder: File? = null,
        val files: ArrayList<File>,
        val isComplete: Boolean,
        val page: Int
    )

    /**
     * Will change the noFilesLayout visility
     * @param hideFileList will hide or show the fileList in order to replace (or not) by the `no-files` layout
     * @param changeControlsVisibility will determine if we need to touch the controls visibility based on no-network/no-files
     * @param hideNavbar will allow to hide or show the nav-bar (in case we can hide it)
     * @param ignoreOffline will allow to ignore if we're offline to show `No Files` instead of `No Connection` in all cases
     */
    internal fun changeNoFilesLayoutVisibility(
        hideFileList: Boolean,
        changeControlsVisibility: Boolean = true,
        hideNavbar: Boolean = false,
        ignoreOffline: Boolean = false
    ) {
        val isOffline = mainViewModel.isInternetAvailable.value == false
        val hasFilesAndIsOffline = !hideFileList && isOffline

        sortLayout?.visibility = if (hideFileList) GONE else VISIBLE
        val navBarVisibility = if ((hideFileList && isOffline) || hideNavbar) GONE else VISIBLE

        if (changeControlsVisibility) {
            val isFileListDestination = findNavController().currentDestination?.id == R.id.fileListFragment
            noNetwork.visibility = if (!hasFilesAndIsOffline) GONE else VISIBLE
            toolbar?.menu?.findItem(R.id.searchItem)?.isVisible = !hideFileList && isFileListDestination
            requireActivity().apply {
                bottomNavigation?.visibility = navBarVisibility
                bottomNavigationBackgroundView?.visibility = navBarVisibility
                mainFab?.visibility = navBarVisibility
            }
        }

        noFilesLayout.toggleVisibility(
            noNetwork = isOffline && !ignoreOffline,
            isVisible = hideFileList,
            showRefreshButton = changeControlsVisibility
        )
    }

    open fun onRestartItemsClicked() = Unit
    open fun onCloseItemsClicked() = Unit
}
