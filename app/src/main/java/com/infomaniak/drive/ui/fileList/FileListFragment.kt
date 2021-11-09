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

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerProgress
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.ActionMultiSelectBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.ActionMultiSelectBottomSheetDialog.Companion.SELECT_DIALOG_ACTION
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.Companion.BULK_OPERATION_CUSTOM_TAG
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.BulkOperationsUtils.generateWorkerData
import com.infomaniak.drive.utils.BulkOperationsUtils.launchBulkOperationWorker
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.setPagination
import com.infomaniak.lib.core.utils.showProgress
import io.realm.RealmList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cardview_file_list.*
import kotlinx.android.synthetic.main.empty_icon_layout.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_add_file.*
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_new_folder.*
import kotlinx.android.synthetic.main.fragment_new_folder.toolbar
import kotlinx.android.synthetic.main.fragment_select_permission.*
import kotlinx.android.synthetic.main.item_file.*
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.coroutines.*
import java.util.*

open class FileListFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    protected lateinit var fileAdapter: FileAdapter
    protected val fileListViewModel: FileListViewModel by viewModels()
    protected val mainViewModel: MainViewModel by activityViewModels()

    private val navigationArgs: FileListFragmentArgs by navArgs()

    internal var folderID = ROOT_ID
    internal var folderName: String = "/"
    private var currentFolder: File? = null

    private lateinit var activitiesRefreshTimer: CountDownTimer
    private var ignoreCreateFolderStack: Boolean = false
    private var isDownloading = false
    private var isLoadingActivities = false
    private var retryLoadingActivities = false

    protected lateinit var showLoadingTimer: CountDownTimer
    protected open var downloadFiles: (ignoreCache: Boolean, isNewSort: Boolean) -> Unit = DownloadFiles()
    protected open var sortFiles: () -> Unit = SortFiles()
    protected open var enabledMultiSelectMode = true
    protected open var hideBackButtonWhenRoot: Boolean = true
    protected open var showPendingFiles = true
    protected open var allowCancellation = true

    protected var userDrive: UserDrive? = null

    companion object {
        const val REFRESH_FAVORITE_FILE = "force_list_refresh"
        const val CANCELLABLE_MAIN_KEY = "cancellable_main"
        const val CANCELLABLE_TITLE_KEY = "cancellable_message"
        const val CANCELLABLE_ACTION_KEY = "cancellable_action"
        const val SORT_TYPE_OPTION_KEY = "sort_type_option"

        const val ACTIVITIES_REFRESH_DELAY = 5000L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!fileListViewModel.sortTypeIsInitialized()) {
            fileListViewModel.sortType = UISettings(requireContext()).sortType
        }
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

        showLoadingTimer = createRefreshTimer { swipeRefreshLayout?.isRefreshing = true }

        activitiesRefreshTimer = createRefreshTimer(ACTIVITIES_REFRESH_DELAY) {
            isLoadingActivities = false
            if (retryLoadingActivities) {
                retryLoadingActivities = false
                if (isResumed) refreshActivities()
            }
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

        mainViewModel.createDropBoxSuccess.observe(viewLifecycleOwner) { dropBox ->
            safeNavigate(
                FileListFragmentDirections.actionFileListFragmentToDropBoxResultBottomSheetDialog(
                    url = dropBox.url,
                    name = dropBox.alias
                )
            )
        }

        noFilesLayout.setup(title = R.string.noFilesDescription, initialListView = fileRecyclerView) {
            fileListViewModel.cancelDownloadFiles()
            downloadFiles(false, false)
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

        if (!isDownloading) downloadFiles(false, false)
        observeOfflineDownloadProgress()

        requireContext().trackUploadWorkerProgress().observe(viewLifecycleOwner) {
            val workInfo = it.firstOrNull() ?: return@observe
            val isUploaded = workInfo.progress.getBoolean(UploadWorker.IS_UPLOADED, false)
            val remoteFolderId = workInfo.progress.getInt(UploadWorker.REMOTE_FOLDER_ID, 0)


            if (remoteFolderId == folderID && isUploaded) {
                when {
                    findNavController().currentDestination?.id == R.id.sharedWithMeFragment
                            && folderID == ROOT_ID -> downloadFiles(true, false)
                    else -> refreshActivities()
                }
            }
        }

        mainViewModel.refreshActivities.observe(viewLifecycleOwner) {
            it?.let {
                showPendingFiles()
                when (findNavController().currentDestination?.id) {
                    R.id.searchFragment, R.id.sharedWithMeFragment -> Unit
                    else -> refreshActivities()
                }
            }
        }

        toolbar?.menu?.findItem(R.id.searchItem)?.isVisible = findNavController().currentDestination?.id == R.id.fileListFragment

        MqttClientWrapper.observe(viewLifecycleOwner) { notification ->
            if (notification is ActionNotification && notification.driveId == AccountUtils.currentDriveId) refreshActivities()
        }

        setupBackActionHandler()
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult<Bundle>(CANCELLABLE_MAIN_KEY) { bundle ->
            bundle.getString(CANCELLABLE_TITLE_KEY)?.let { title ->
                bundle.getParcelable<CancellableAction>(CANCELLABLE_ACTION_KEY)?.let { action ->
                    checkIfNoFiles()

                    val onCancelActionClicked: (() -> Unit)? = if (allowCancellation) ({
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (ApiRepository.cancelAction(action).data == true && isResumed) {
                                withContext(Dispatchers.Main) {
                                    refreshActivities()
                                }
                            }
                        }
                    }) else null

                    requireActivity().showSnackbar(
                        title,
                        anchorView = requireActivity().mainFab,
                        onActionClicked = onCancelActionClicked
                    )
                } ?: run { requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab) }
            }
        }

        getBackNavigationResult<Int>(REFRESH_FAVORITE_FILE) {
            mainViewModel.refreshActivities.value = true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data?.let { onSelectFolderResult(requestCode, resultCode, data) }
    }

    override fun onStart() {
        super.onStart()
        fileAdapter.resetRealmListener()
    }

    override fun onResume() {
        super.onResume()
        if (!isDownloading) refreshActivities()
        showPendingFiles()
        updateVisibleProgresses()
    }

    override fun onStop() {
        fileAdapter.removeRealmDataListener()
        super.onStop()
    }

    private fun performBulkOperation(type: BulkOperationType, destinationFolder: File? = null) {
        val selectedFiles = fileAdapter.getValidItemsSelected()
        val fileCount =
            if (fileAdapter.allSelected) fileListViewModel.lastItemCount?.count ?: fileAdapter.itemCount else selectedFiles.size

        val onActionApproved: (dialog: Dialog?) -> Unit = {
            if (fileAdapter.allSelected && fileCount > BulkOperationsUtils.MIN_SELECTED ||
                selectedFiles.size > BulkOperationsUtils.MIN_SELECTED
            ) {
                sendBulkAction(
                    fileCount,
                    BulkOperation(
                        action = type,
                        fileIds = if (!fileAdapter.allSelected) selectedFiles.map { it.id } else null,
                        parent = currentFolder!!,
                        destinationFolderId = destinationFolder?.id
                    )
                )
            } else {
                val mediator = mainViewModel.createMultiSelectMediator()
                enableButtonMultiSelect(false)

                selectedFiles.reversed().forEach {
                    val file = when {
                        it.isManagedAndValidByRealm() -> it.realm.copyFromRealm(it, 0)
                        it.isNotManagedByRealm() -> it
                        else -> return@forEach
                    }
                    val onSuccess: (Int) -> Unit = { fileID ->
                        runBlocking(Dispatchers.Main) { fileAdapter.deleteByFileId(fileID) }
                    }

                    when (type) {
                        BulkOperationType.TRASH -> {
                            mediator.addSource(
                                mainViewModel.deleteFile(file, onSuccess = onSuccess),
                                mainViewModel.updateMultiSelectMediator(mediator)
                            )
                        }
                        BulkOperationType.MOVE -> {
                            mediator.addSource(
                                mainViewModel.moveFile(file, destinationFolder!!, onSuccess),
                                mainViewModel.updateMultiSelectMediator(mediator)
                            )
                        }
                        BulkOperationType.COPY -> {
                            val fileName = file.getFileName()
                            mediator.addSource(
                                mainViewModel.duplicateFile(
                                    file,
                                    destinationFolder!!.id,
                                    requireContext().getString(R.string.allDuplicateFileName, fileName, file.getFileExtension())
                                ),
                                mainViewModel.updateMultiSelectMediator(mediator)
                            )
                        }
                        BulkOperationType.SET_OFFLINE -> {
                            addSelectedFilesToOffline(file)
                        }
                        BulkOperationType.ADD_FAVORITES -> {
                            mediator.addSource(mainViewModel.addFileToFavorites(file) {
                                runBlocking(Dispatchers.Main) {
                                    fileAdapter.notifyFileChanged(file.id) { file ->
                                        if (!file.isManaged) file.isFavorite = true
                                    }
                                }
                            }, mainViewModel.updateMultiSelectMediator(mediator))
                        }
                    }
                }

                mediator.observe(viewLifecycleOwner) { (success, total) ->
                    if (total == fileCount) {
                        val title = if (success == 0) {
                            getString(R.string.anErrorHasOccurred)
                        } else {
                            resources.getQuantityString(type.successMessage, success, success, destinationFolder?.name + "/")
                        }
                        requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab)
                        refreshActivities()
                        closeMultiSelect()
                    }
                }
            }
        }

        if (type == BulkOperationType.TRASH) {
            Utils.createConfirmation(
                context = requireContext(),
                title = getString(R.string.modalMoveTrashTitle),
                message = resources.getQuantityString(R.plurals.modalMoveTrashDescription, fileCount, fileCount),
                isDeletion = true,
                onConfirmation = onActionApproved
            )
        } else {
            onActionApproved(null)
        }
    }

    private fun sendBulkAction(fileCount: Int = 0, bulkOperation: BulkOperation) {
        fileListViewModel.performCancellableBulkOperation(bulkOperation).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { cancellableAction ->
                    requireContext().launchBulkOperationWorker(
                        generateWorkerData(cancellableAction.cancelId, fileCount, bulkOperation.action)
                    )
                }
            } else requireActivity().showSnackbar(apiResponse.translateError())
            closeMultiSelect()
        }
    }

    private fun setupMultiSelect() {
        fileAdapter.enabledMultiSelectMode = true
        closeButtonMultiSelect.setOnClickListener { closeMultiSelect() }
        deleteButtonMultiSelect.setOnClickListener { performBulkOperation(BulkOperationType.TRASH) }
        moveButtonMultiSelect.setOnClickListener { Utils.moveFileClicked(this, folderID) }
        menuButtonMultiSelect.setOnClickListener {
            safeNavigate(
                FileListFragmentDirections.actionFileListFragmentToActionMultiSelectBottomSheetDialog(
                    fileIds = fileAdapter.getValidItemsSelected().map { it.id }.toIntArray(),
                    onlyFolders = fileAdapter.getValidItemsSelected().all { it.isFolder() }
                )
            )
        }
        selectAllButton.initProgress(viewLifecycleOwner)
        selectAllButton.setOnClickListener {
            selectAllButton.showProgress(ContextCompat.getColor(requireContext(), R.color.primary))
            if (fileAdapter.allSelected) {
                fileAdapter.configureAllSelected(false)
                onUpdateMultiSelect()
            } else {
                fileAdapter.configureAllSelected(true)
                enableButtonMultiSelect(false)

                fileListViewModel.getFileCount(currentFolder!!).observe(viewLifecycleOwner) { fileCount ->
                    val fileNumber = fileCount.count
                    if (fileNumber < BulkOperationsUtils.MIN_SELECTED) fileAdapter.itemsSelected = fileAdapter.getFiles()
                    enableButtonMultiSelect(true)
                    onUpdateMultiSelect(fileNumber)
                }
            }
        }

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

        sortButton.setText(fileListViewModel.sortType.translation)
        sortButton.setOnClickListener {
            safeNavigate(R.id.sortFilesBottomSheetDialog, bundleOf("sortType" to fileListViewModel.sortType))
        }

        uploadFileInProgress.setUploadFileInProgress(R.string.uploadInThisFolderTitle) {
            goToUploadInProgress(folderID)
        }
    }

    private fun setupFileAdapter() {
        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            fileAdapter.toggleOfflineMode(requireContext(), !isInternetAvailable)
            noNetwork.isGone = isInternetAvailable
        }

        fileAdapter = FileAdapter(FileController.emptyList(mainViewModel.realm))
        fileAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        fileAdapter.setHasStableIds(true)
        fileAdapter.onFileClicked = { file ->
            if (file.isManagedAndValidByRealm() || file.isNotManagedByRealm()) {
                if (file.isFolder())
                    openFolder(file)
                else
                    displayFile(file)
            } else {
                refreshActivities()
            }
        }

        fileAdapter.openMultiSelectMode = { openMultiSelect() }

        fileAdapter.updateMultiSelectMode = { onUpdateMultiSelect() }

        fileAdapter.onMenuClicked = { file ->
            val fileObject = file.realm?.copyFromRealm(file, 1) ?: file
            val bundle = bundleOf(
                "file" to fileObject,
                "userDrive" to UserDrive(driveId = file.driveId, sharedWithMe = fileListViewModel.isSharedWithMe)
            )
            safeNavigate(R.id.fileInfoActionsBottomSheetDialog, bundle)
        }

        onBackNavigationResult()
        fileRecyclerView.setHasFixedSize(true)
        fileRecyclerView.adapter = fileAdapter
        fileRecyclerView.setPagination({
            if (!fileAdapter.isComplete) fileAdapter.showLoading()
        })

        mainViewModel.updateOfflineFile.observe(viewLifecycleOwner) {
            it?.let { fileId ->
                if (findNavController().currentDestination?.id == R.id.offlineFileFragment) {
                    fileAdapter.deleteByFileId(fileId)
                    checkIfNoFiles()
                }
            }
        }
    }

    private fun openFolder(file: File) {
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

    private fun displayFile(file: File) {
        val fileList = fileAdapter.getFileObjectsList(mainViewModel.realm)
        Utils.displayFile(mainViewModel, findNavController(), file, fileList)
    }

    private fun onBackNavigationResult() {
        // TODO - 2 - Implement download for multiselection !
        getBackNavigationResult<BulkOperationType>(SELECT_DIALOG_ACTION) { type ->
            if (type == BulkOperationType.COPY) {
                val intent = Intent(requireContext(), SelectFolderActivity::class.java).apply {
                    putExtra(SelectFolderActivity.USER_ID_TAG, AccountUtils.currentUserId)
                    putExtra(SelectFolderActivity.USER_DRIVE_ID_TAG, AccountUtils.currentDriveId)
                    putExtra(
                        SelectFolderActivity.CUSTOM_ARGS_TAG,
                        bundleOf(BULK_OPERATION_CUSTOM_TAG to BulkOperationType.COPY)
                    )
                }
                startActivityForResult(intent, SelectFolderActivity.SELECT_FOLDER_REQUEST)
            } else {
                performBulkOperation(type)
            }
        }
    }

    private fun addSelectedFilesToOffline(file: File) {
        if (!file.isOffline && !file.isFolder()) {
            val cacheFile = file.getCacheFile(requireContext())
            val offlineFile = file.getOfflineFile(requireContext())

            if (offlineFile != null && !file.isObsolete(cacheFile) && file.isIntactFile(cacheFile)) {
                Utils.moveCacheFileToOffline(file, cacheFile, offlineFile)
                runBlocking(Dispatchers.IO) { FileController.updateOfflineStatus(file.id, true) }

                fileAdapter.updateFileProgressByFileId(file.id, 100) { _, currentFile ->
                    currentFile.isOffline = true
                    currentFile.currentProgress = 0
                }
            } else Utils.downloadAsOfflineFile(requireContext(), file)
        }
    }

    private fun onSelectFolderResult(requestCode: Int, resultCode: Int, data: Intent) {
        if (requestCode == SelectFolderActivity.SELECT_FOLDER_REQUEST && resultCode == AppCompatActivity.RESULT_OK) {
            val folderName = data.extras?.getString(SelectFolderActivity.FOLDER_NAME_TAG).toString()
            val folderId = data.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)!!
            val customArgs = data.extras?.getBundle(SelectFolderActivity.CUSTOM_ARGS_TAG)
            val bulkOperationType = customArgs?.getParcelable<BulkOperationType>(BULK_OPERATION_CUSTOM_TAG)!!

            performBulkOperation(
                type = bulkOperationType,
                destinationFolder = File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId)
            )
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
        val isUploadInProgressNavigation = findNavController().currentDestination?.id == R.id.uploadInProgressFragment
        if (folderID == OTHER_ROOT_ID || isUploadInProgressNavigation) return
        if (isLoadingActivities) {
            retryLoadingActivities = true
            return
        }

        activitiesRefreshTimer.cancel()
        isLoadingActivities = true
        mainViewModel.currentFolder.value?.let { currentFolder ->
            FileController.getFileById(currentFolder.id, userDrive)?.let { updatedFolder ->
                downloadFolderActivities(updatedFolder)
                activitiesRefreshTimer.start()
            } ?: kotlin.run { activitiesRefreshTimer.start() }
        } ?: kotlin.run { activitiesRefreshTimer.start() }
    }

    private fun observeOfflineDownloadProgress() {
        mainViewModel.observeDownloadOffline(requireContext().applicationContext).observe(viewLifecycleOwner) { workInfoList ->
            if (workInfoList.isEmpty()) return@observe

            val workInfo = workInfoList.firstOrNull { it.state == WorkInfo.State.RUNNING }

            if (workInfo == null) {
                updateVisibleProgresses()
                return@observe
            }

            val fileId: Int = workInfo.progress.getInt(DownloadWorker.FILE_ID, 0)
            if (fileId == 0) return@observe

            val progress = workInfo.progress.getInt(DownloadWorker.PROGRESS, 100)
            fileRecyclerView.post {
                fileAdapter.updateFileProgressByFileId(fileId, progress) { _, file ->
                    val tag = workInfo.tags.firstOrNull { it == file.getWorkerTag() }
                    if (tag != null) {
                        CoroutineScope(Dispatchers.IO).launch {
                            FileController.updateOfflineStatus(fileId, true)
                        }
                        file.currentProgress = Utils.INDETERMINATE_PROGRESS
                    }
                }
            }
            Log.i("isPendingOffline", "progress from fragment $progress% for file $fileId, state:${workInfo.state}")
        }

        mainViewModel.updateVisibleFiles.observe(viewLifecycleOwner) {
            updateVisibleProgresses()
        }
    }

    override fun onRefresh() {
        fileListViewModel.cancelDownloadFiles()
        downloadFiles(true, false)
    }

    private fun showPendingFiles() {
        if (!showPendingFiles) return
        fileListViewModel.getPendingFilesCount(folderID).observe(viewLifecycleOwner) { pendingFilesCount ->
            uploadFileInProgress.updateUploadFileInProgress(pendingFilesCount)
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
        collapsingToolbarLayout.isGone = true
        multiSelectLayout.isVisible = true
        selectAllButton.isVisible = true
    }

    private fun onUpdateMultiSelect(selectedNumber: Int? = null) {
        val fileSelectedNumber = selectedNumber ?: fileAdapter.getValidItemsSelected().size
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
        selectAllButton.hideProgress(if (fileAdapter.allSelected) R.string.buttonDeselectAll else R.string.buttonSelectAll)
    }

    private fun enableButtonMultiSelect(isEnabled: Boolean) {
        deleteButtonMultiSelect.isEnabled = isEnabled
        moveButtonMultiSelect.isEnabled = isEnabled
        menuButtonMultiSelect.isEnabled = isEnabled
    }

    private fun closeMultiSelect() {
        fileAdapter.apply {
            itemsSelected = RealmList()
            multiSelectMode = false
            allSelected = false
            notifyItemRangeChanged(0, itemCount)
        }

        collapsingToolbarLayout.isVisible = true
        multiSelectLayout.isGone = true
        selectAllButton.isGone = true
    }

    private fun downloadFolderActivities(currentFolder: File) {
        fileListViewModel.getFolderActivities(currentFolder, userDrive).observe(viewLifecycleOwner) { activities ->
            if (activities?.isNotEmpty() == true) {
                getFolderFiles(
                    ignoreCache = false,
                    onFinish = {
                        it?.let { (_, files, _) ->
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
            order = fileListViewModel.sortType,
            userDrive = userDrive
        ).observe(viewLifecycleOwner) {
            onFinish?.invoke(it)
        }
    }

    private fun updateVisibleProgresses() {
        val layoutManager = fileRecyclerView.layoutManager
        if (layoutManager is LinearLayoutManager) {
            val first = layoutManager.findFirstVisibleItemPosition()
            val count = layoutManager.findLastVisibleItemPosition() - first + 1
            fileAdapter.notifyItemRangeChanged(first, count, -1)
        }
    }

    private inner class SortFiles : () -> Unit {
        override fun invoke() {
            getBackNavigationResult<File.SortType>(SORT_TYPE_OPTION_KEY) { newSortType ->
                fileListViewModel.sortType = newSortType
                sortButton?.setText(fileListViewModel.sortType.translation)

                downloadFiles(fileListViewModel.isSharedWithMe, true)

                UISettings(requireContext()).sortType = newSortType
                refreshActivities()
            }
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            isDownloading = true
            fileAdapter.isComplete = false
            getFolderFiles(ignoreCache, onFinish = {
                it?.let { result ->
                    if (fileAdapter.itemCount == 0 || result.page == 1 || isNewSort) {
                        FileController.getRealmLiveFiles(
                            parentId = folderID,
                            order = fileListViewModel.sortType,
                            realm = mainViewModel.realm
                        ).apply { fileAdapter.updateFileList(this) }

                        currentFolder = if (result.parentFolder?.id == ROOT_ID) {
                            AccountUtils.getCurrentDrive()?.convertToFile(Utils.getRootName(requireContext()))
                        } else result.parentFolder

                        mainViewModel.currentFolder.value = currentFolder
                        changeNoFilesLayoutVisibility(
                            hideFileList = fileAdapter.fileList.isEmpty(),
                            changeControlsVisibility = result.parentFolder?.isRoot() == false
                        )

                        refreshActivities()
                    }
                    fileAdapter.isComplete = result.isComplete
                } ?: run {
                    changeNoFilesLayoutVisibility(
                        hideFileList = fileAdapter.itemCount == 0,
                        changeControlsVisibility = folderID != ROOT_ID
                    )
                    fileAdapter.isComplete = true
                }
                isDownloading = false
                showLoadingTimer.cancel()
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
     * Will change the noFilesLayout visibility
     * @param hideFileList will hide or show the fileList in order to replace (or not) by the `no-files` layout
     * @param changeControlsVisibility will determine if we need to touch the controls visibility based on no-network/no-files
     * @param ignoreOffline will allow to ignore if we're offline to show `No Files` instead of `No Connection` in all cases
     */
    internal fun changeNoFilesLayoutVisibility(
        hideFileList: Boolean,
        changeControlsVisibility: Boolean = true,
        ignoreOffline: Boolean = false
    ) {
        val isOffline = mainViewModel.isInternetAvailable.value == false
        val hasFilesAndIsOffline = !hideFileList && isOffline

        sortLayout?.isGone = hideFileList

        if (changeControlsVisibility) {
            val isFileListDestination = findNavController().currentDestination?.id == R.id.fileListFragment
            noNetwork.isVisible = hasFilesAndIsOffline
            toolbar?.menu?.findItem(R.id.searchItem)?.isVisible = !hideFileList && isFileListDestination
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
