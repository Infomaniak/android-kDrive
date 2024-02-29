/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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

import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.google.android.material.appbar.MaterialToolbar
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FolderFilesProvider.SourceRestrictionType
import com.infomaniak.drive.data.cache.FolderFilesProvider.SourceRestrictionType.*
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.File.SortType
import com.infomaniak.drive.data.models.File.SortTypeUsage
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerProgress
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerSucceeded
import com.infomaniak.drive.databinding.FragmentFileListBinding
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.drive.ui.bottomSheetDialogs.ColorFolderBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.FileInfoActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment
import com.infomaniak.drive.ui.fileList.multiSelect.FileListMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openBookmark
import com.infomaniak.drive.utils.FilePresenter.openBookmarkIntent
import com.infomaniak.drive.utils.FilePresenter.openFolder
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.views.NoItemsLayoutView
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer
import kotlinx.coroutines.*

open class FileListFragment : MultiSelectFragment(MATOMO_CATEGORY), SwipeRefreshLayout.OnRefreshListener,
    NoItemsLayoutView.INoItemsLayoutView {

    private var _binding: FragmentFileListBinding? = null
    val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    protected lateinit var fileAdapter: FileAdapter
    protected val fileListViewModel: FileListViewModel by viewModels()

    private val navigationArgs: FileListFragmentArgs by navArgs()

    internal var folderId = ROOT_ID
    internal var folderName: String = "/"

    private lateinit var activitiesRefreshTimer: CountDownTimer
    private var isDownloading = false
    private var isLoadingActivities = false
    private var isUploading = false
    private var retryLoadingActivities = false

    protected val showLoadingTimer: CountDownTimer by lazy {
        createRefreshTimer { _binding?.let { it.swipeRefreshLayout.isRefreshing = true } }
    }

    protected open var downloadFiles: (ignoreCache: Boolean, isNewSort: Boolean) -> Unit = DownloadFiles()
    protected open var sortFiles: () -> Unit = SortFiles()
    protected open var enabledMultiSelectMode = true
    protected open var hideBackButtonWhenRoot: Boolean = true
    protected open var showPendingFiles = true
    protected open var allowCancellation = true
    protected open val sortTypeUsage = SortTypeUsage.FILE_LIST

    private val noItemsFoldersTitle: Int by lazy {
        if (mainViewModel.currentFolder.value?.rights?.canCreateFile == true
            && findNavController().currentDestination?.id != R.id.trashFragment
        ) {
            R.string.noFilesDescriptionWithCreationRights
        } else {
            R.string.noFilesDescription
        }
    }
    open val noItemsRootTitle: Int by lazy { noItemsFoldersTitle }
    override val noItemsTitle: Int by lazy { if (isCurrentFolderRoot()) noItemsRootTitle else noItemsFoldersTitle }

    private val noItemsFoldersIcon: Int = R.drawable.ic_folder_filled
    open val noItemsRootIcon: Int = noItemsFoldersIcon
    override val noItemsIcon: Int by lazy { if (isCurrentFolderRoot()) noItemsRootIcon else noItemsFoldersIcon }

    override val noItemsInitialListView: View
        get() = binding.fileRecyclerView

    val fileRecyclerView get() = _binding?.fileRecyclerView

    protected var userDrive: UserDrive? = null

    private val selectAllTimer: CountDownTimer by lazy {
        createRefreshTimer {
            multiSelectLayout?.selectAllButton?.showProgress(ContextCompat.getColor(requireContext(), R.color.primary))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (!fileListViewModel.sortTypeIsInitialized()) {
            fileListViewModel.sortType = UiSettings(requireContext()).sortType
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        folderId = navigationArgs.folderId
        folderName = navigationArgs.folderName
        _binding = FragmentFileListBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun initMultiSelectLayout(): MultiSelectLayoutBinding? = binding.multiSelectLayout
    override fun initMultiSelectToolbar(): CollapsingToolbarLayout? = binding.collapsingToolbarLayout
    override fun initSwipeRefreshLayout(): SwipeRefreshLayout? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupToolbars()

        activitiesRefreshTimer = createRefreshTimer(ACTIVITIES_REFRESH_DELAY) {
            isLoadingActivities = false

            if (retryLoadingActivities) {
                retryLoadingActivities = false
                isUploading = false
                if (isResumed) refreshActivities()
            }
        }

        mainViewModel.createDropBoxSuccess.observe(viewLifecycleOwner) { dropBox ->
            safeNavigate(
                FileListFragmentDirections.actionFileListFragmentToDropBoxResultBottomSheetDialog(
                    url = dropBox.url,
                    name = dropBox.name
                )
            )
        }

        binding.noFilesLayout.apply {
            iNoItemsLayoutView = this@FileListFragment
            onNetworkUnavailableRefresh = {
                fileListViewModel.cancelDownloadFiles()
                downloadFiles(false, false)
            }
        }

        binding.toolbar.apply {
            if (isCurrentFolderRoot() && hideBackButtonWhenRoot) navigationIcon = null

            setOnMenuItemClickListener { menuItem ->
                when (menuItem.itemId) {
                    R.id.searchItem -> {
                        ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SEARCH.id)
                        safeNavigate(FileListFragmentDirections.actionFileListFragmentToSearchFragment())
                    }
                    R.id.restartItem -> onRestartItemsClicked()
                    R.id.closeItem -> onCloseItemsClicked()
                }
                true
            }

            setNavigationOnClickListener { findNavController().popBackStack() }

            menu?.findItem(R.id.searchItem)?.isVisible = findNavController().currentDestination?.id == R.id.fileListFragment
        }

        if (homeClassName() == null) {
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
                if (multiSelectManager.isMultiSelectOn) {
                    closeMultiSelect()
                } else {
                    findNavController().popBackStack()
                }
            }
        }

        with(binding) {
            swipeRefreshLayout.setOnRefreshListener(this@FileListFragment)
            setToolbarTitle()
            ViewCompat.requestApplyInsets(fileListCoordinator) // Restore coordinator state
        }

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

            isUploading = if (remoteFolderId == folderId && isUploaded) {
                showUploadedFiles()
                false
            } else {
                true
            }
        }

        requireContext().trackUploadWorkerSucceeded().observe(viewLifecycleOwner) {
            if (isUploading) retryLoadingActivities = true
            if (!isDownloading || isUploading) activitiesRefreshTimer.start()
        }

        mainViewModel.refreshActivities.observe(viewLifecycleOwner) {
            showPendingFiles()
            when (findNavController().currentDestination?.id) {
                R.id.searchFragment, R.id.sharedWithMeFragment -> Unit
                else -> refreshActivities()
            }
        }

        MqttClientWrapper.observe(viewLifecycleOwner) { notification ->
            with(notification) {
                if (isExternalImportNotification()) fileListViewModel.updateExternalImport(this)
                if (driveId == AccountUtils.currentDriveId && isFileActionNotification()) refreshActivities()
            }
        }

        setupBackActionHandler()
    }

    private fun setupToolbars() {
        fun MaterialToolbar.removeInsets() = setContentInsetsRelative(0, 0)
        binding.toolbar.removeInsets()
        multiSelectLayout?.toolbarMultiSelect?.removeInsets()
    }

    private fun setupBackActionHandler() {
        getBackNavigationResult<Bundle>(CANCELLABLE_MAIN_KEY) { bundle ->
            bundle.getString(CANCELLABLE_TITLE_KEY)?.let { title ->
                bundle.getParcelable<CancellableAction>(CANCELLABLE_ACTION_KEY)?.let { action ->
                    checkIfNoFiles()

                    val onCancelActionClicked: (() -> Unit)? = if (allowCancellation) ({
                        lifecycleScope.launch(Dispatchers.IO) {
                            if (ApiRepository.undoAction(action).data == true && isResumed) {
                                withContext(Dispatchers.Main) { refreshActivities() }
                            }
                        }
                    }) else null

                    showSnackbar(title, true, onActionClicked = onCancelActionClicked)
                } ?: run { showSnackbar(title, true) }
            }
        }

        getBackNavigationResult<Int>(REFRESH_FAVORITE_FILE) {
            mainViewModel.refreshActivities.value = true
        }

        getBackNavigationResult<Int>(DownloadProgressDialog.OPEN_BOOKMARK) { fileId ->
            FileController.getFileProxyById(fileId, customRealm = mainViewModel.realm)?.let {
                openBookmarkIntent(it)
            }
        }

        getBackNavigationResult<String>(ColorFolderBottomSheetDialog.COLOR_FOLDER_NAV_KEY) {
            performBulkOperation(
                type = BulkOperationType.COLOR_FOLDER,
                allSelectedFilesCount = getAllSelectedFilesCount(),
                color = it,
            )
        }

        getBackNavigationResult<List<Int>>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) { closeMultiSelect() }
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

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fileListViewModel.isListMode.value?.let { isListMode ->
            if (!isListMode) fileRecyclerView?.layoutManager = createLayoutManager(isListMode)
        }
    }

    override fun onStop() {
        fileAdapter.removeRealmDataListener()
        super.onStop()
    }

    override fun onDestroyView() {
        isDownloading = false
        showLoadingTimer.cancel()
        super.onDestroyView()
        _binding = null
    }

    private fun showUploadedFiles() {
        when {
            findNavController().currentDestination?.id == R.id.sharedWithMeFragment
                    && folderId == ROOT_ID -> downloadFiles(true, false)
            else -> refreshActivities()
        }
    }

    private fun setupMultiSelect() {
        multiSelectManager.isMultiSelectAuthorized = true

        multiSelectLayout?.apply {

            toolbarMultiSelect.setNavigationOnClickListener { closeMultiSelect() }
            moveButtonMultiSelect.setOnClickListener { moveFiles(folderId) }
            deleteButtonMultiSelect.setOnClickListener { deleteFiles(getAllSelectedFilesCount()) }
            menuButtonMultiSelect.setOnClickListener {
                onMenuButtonClicked(
                    multiSelectBottomSheet = FileListMultiSelectActionsBottomSheetDialog(),
                    areAllFromTheSameFolder = true,
                )
            }

            selectAllButton.apply {
                initProgress(viewLifecycleOwner)
                setOnClickListener {
                    selectAllTimer.start()
                    if (multiSelectManager.isSelectAllOn) {
                        val isSelectedAll = multiSelectManager.exceptedItemsIds.isNotEmpty()
                        fileAdapter.configureAllSelected(isSelectedAll)
                        onUpdateMultiSelect()
                    } else {
                        fileAdapter.configureAllSelected(true)
                        enableMultiSelectButtons(false)

                        fileListViewModel.getFileCount(multiSelectManager.currentFolder!!)
                            .observe(viewLifecycleOwner) { fileCount ->
                                with(fileAdapter.getFiles()) {
                                    multiSelectManager.selectedItems = this
                                    multiSelectManager.selectedItemsIds = this.map { it.id }.toHashSet()
                                }
                                enableMultiSelectButtons(true)
                                onUpdateMultiSelect(fileCount.count)
                            }
                    }
                }
            }
        }
    }

    private fun setupDisplay() {
        setupToggleDisplayButton()
        setupListMode()
        setupSortButton()
        binding.uploadFileInProgress.setUploadFileInProgress(R.string.uploadInThisFolderTitle) {
            goToUploadInProgress(folderId)
        }
    }

    private fun setupToggleDisplayButton() {
        binding.toggleDisplayButton.setOnClickListener {
            val newListMode = !UiSettings(requireContext()).listMode
            trackEvent("displayStyle", if (newListMode) "viewList" else "viewGrid")
            UiSettings(requireContext()).listMode = newListMode
            fileListViewModel.isListMode.value = newListMode
        }
    }

    private fun setupListMode() {
        fileListViewModel.isListMode.apply {
            observe(viewLifecycleOwner) { setupDisplayMode(it) }
            value = UiSettings(requireContext()).listMode
        }
    }

    private fun setupSortButton() {
        binding.sortButton.apply {
            setText(fileListViewModel.sortType.translation)
            setOnClickListener { navigateToSortFilesDialog() }
        }
    }

    private fun navigateToSortFilesDialog() {
        safeNavigate(
            R.id.sortFilesBottomSheetDialog,
            SortFilesBottomSheetDialogArgs(sortType = fileListViewModel.sortType, sortTypeUsage = sortTypeUsage).toBundle(),
        )
    }

    protected open fun setupFileAdapter() {
        observeAndDisplayNetworkAvailability(
            mainViewModel = mainViewModel,
            noNetworkBinding = binding.noNetworkInclude,
            noNetworkBindingDirectParent = binding.fileListLayout,
            additionalChanges = { isInternetAvailable -> fileAdapter.toggleOfflineMode(requireContext(), !isInternetAvailable) }
        )

        multiSelectManager.apply {
            openMultiSelect = { openMultiSelect() }
            updateMultiSelect = { onUpdateMultiSelect() }
        }

        fileAdapter = FileAdapter(multiSelectManager, FileController.emptyList(mainViewModel.realm)).apply {
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            setHasStableIds(true)

            onEmptyList = { checkIfNoFiles() }

            onFileClicked = { file ->
                if (file.isUsable()) {
                    when {
                        file.isFolder() -> openFolder(
                            file,
                            navigationArgs.shouldHideBottomNavigation,
                            navigationArgs.shouldShowSmallFab,
                            fileListViewModel,
                        )
                        file.isBookmark() -> openBookmark(file)
                        else -> displayFile(file, mainViewModel, fileAdapter)
                    }
                } else {
                    refreshActivities()
                }
            }

            onMenuClicked = { file ->
                val fileObject = file.realm?.copyFromRealm(file, 1) ?: file
                val bundle = FileInfoActionsBottomSheetDialogArgs(
                    fileId = fileObject.id,
                    userDrive = UserDrive(driveId = file.driveId, sharedWithMe = fileListViewModel.isSharedWithMe),
                    shouldShowSmallFab = navigationArgs.shouldShowSmallFab,
                ).toBundle()
                safeNavigate(R.id.fileInfoActionsBottomSheetDialog, bundle, currentClassName = homeClassName())
            }

            adapter = this
        }

        binding.fileRecyclerView.apply {
            setHasFixedSize(true)
            adapter = fileAdapter
            setPagination({ if (!fileAdapter.isComplete) fileAdapter.showLoading() })
        }

        mainViewModel.updateOfflineFile.observe(viewLifecycleOwner) { fileId ->
            if (findNavController().currentDestination?.id == R.id.offlineFileFragment) {
                fileAdapter.deleteByFileId(fileId)
                checkIfNoFiles()
            }
        }
    }

    protected open fun homeClassName(): String? = null

    protected fun setToolbarTitle(@StringRes rootTitleRes: Int? = null) {
        binding.collapsingToolbarLayout.title =
            if (isCurrentFolderRoot() && rootTitleRes != null) getString(rootTitleRes) else folderName
    }

    private fun isCurrentFolderRoot() = folderId == ROOT_ID || folderId == OTHER_ROOT_ID

    private fun checkIfNoFiles() {
        changeNoFilesLayoutVisibility(
            hideFileList = fileAdapter.itemCount == 0,
            changeControlsVisibility = true,
            ignoreOffline = true
        )
    }

    private fun refreshActivities() {
        val isUploadInProgressNavigation = findNavController().currentDestination?.id == R.id.uploadInProgressFragment

        if (folderId == OTHER_ROOT_ID || isUploadInProgressNavigation || !fileAdapter.isComplete) return

        if (isLoadingActivities) {
            retryLoadingActivities = true
            return
        }

        activitiesRefreshTimer.cancel()
        isLoadingActivities = true
        mainViewModel.currentFolder.value?.let { localCurrentFolder ->
            FileController.getFileById(localCurrentFolder.id, userDrive)?.let { updatedFolder ->
                downloadFolderActivities(updatedFolder)
                activitiesRefreshTimer.start()
            } ?: run { activitiesRefreshTimer.start() }
        } ?: run { activitiesRefreshTimer.start() }
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
            binding.fileRecyclerView.post {
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
            SentryLog.i("isPendingOffline", "progress from fragment $progress% for file $fileId, state:${workInfo.state}")
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
        val isNotCurrentDriveRoot = folderId == ROOT_ID && findNavController().currentDestination?.id != R.id.fileListFragment
        if (!showPendingFiles || isNotCurrentDriveRoot) return
        fileListViewModel.getPendingFilesCount(folderId).observe(viewLifecycleOwner) { pendingFilesCount ->
            binding.uploadFileInProgress.updateUploadFileInProgress(pendingFilesCount, binding.uploadFileInProgressLayout)
        }
    }

    private fun goToUploadInProgress(folderId: Int) {
        safeNavigate(
            R.id.uploadInProgressFragment,
            FileListFragmentArgs(folderId = folderId, folderName = getString(R.string.uploadInProgressTitle)).toBundle(),
        )
    }

    private fun setupDisplayMode(isListMode: Boolean) = with(binding) {
        fileRecyclerView.layoutManager = createLayoutManager(isListMode)

        val listModeIconRes = when {
            isListMode -> R.drawable.ic_list
            fileAdapter.isHomeOffline -> R.drawable.ic_largelist
            else -> R.drawable.ic_grid
        }
        toggleDisplayButton.icon = ContextCompat.getDrawable(requireContext(), listModeIconRes)
        fileAdapter.viewHolderType = if (isListMode) FileAdapter.DisplayType.LIST else FileAdapter.DisplayType.GRID
        fileRecyclerView.adapter = fileAdapter
    }

    protected open fun createLayoutManager(isListMode: Boolean): LinearLayoutManager {
        val navController = findNavController()
        return if (isListMode) {
            SentryLinearLayoutManager(navController, requireContext())
        } else {
            val columnNumber = requireActivity().getAdjustedColumnNumber(200, maxColumns = 10)
            SentryGridLayoutManager(navController, requireContext(), columnNumber)
        }
    }

    private fun onUpdateMultiSelect(selectedNumber: Int? = null) {
        onItemSelected(selectedNumber)
        updateSelectAllButtonText()
    }

    private fun updateSelectAllButtonText() {
        multiSelectLayout?.selectAllButton?.apply {

            selectAllTimer.cancel()

            val textId = with(multiSelectManager) {
                if (isSelectAllOn && exceptedItemsIds.isEmpty()) R.string.buttonDeselectAll else R.string.buttonSelectAll
            }

            if (isClickable) setText(textId) else hideProgress(textId)
        }
    }

    private fun downloadFolderActivities(updatedFolder: File) {
        fileListViewModel.getFolderActivities(updatedFolder, userDrive).observe(viewLifecycleOwner) { isNotEmpty ->
            if (isNotEmpty == true) {
                getFolderFiles(
                    sourceRestrictionType = ONLY_FROM_LOCAL,
                    isNewSort = false,
                    onFinish = {
                        it?.let { (_, files, _) ->
                            changeNoFilesLayoutVisibility(
                                hideFileList = files.isEmpty(),
                                changeControlsVisibility = true,
                            )
                        }
                    },
                )
            }
        }
    }

    private fun getFolderFiles(
        sourceRestrictionType: SourceRestrictionType,
        isNewSort: Boolean,
        onFinish: ((FolderFilesResult?) -> Unit)? = null,
    ) {
        showPendingFiles()

        val isNetworkUnavailable = mainViewModel.isInternetAvailable.value == false

        fileListViewModel.getFiles(
            folderId,
            order = fileListViewModel.sortType,
            sourceRestrictionType = if (isNetworkUnavailable) ONLY_FROM_LOCAL else sourceRestrictionType,
            userDrive = userDrive,
            isNewSort = isNewSort,
        ).observe(viewLifecycleOwner) {
            onFinish?.invoke(it)
        }
    }

    private fun updateVisibleProgresses() {
        val layoutManager = binding.fileRecyclerView.layoutManager
        if (layoutManager is LinearLayoutManager) {
            val first = layoutManager.findFirstVisibleItemPosition()
            val count = layoutManager.findLastVisibleItemPosition() - first + 1
            fileAdapter.notifyItemRangeChanged(first, count, -1)
        }
    }

    override fun performBulkOperation(
        type: BulkOperationType,
        areAllFromTheSameFolder: Boolean,
        allSelectedFilesCount: Int?,
        destinationFolder: File?,
        color: String?,
    ) {
        super.performBulkOperation(type, areAllFromTheSameFolder, getAllSelectedFilesCount(), destinationFolder, color)
    }

    override fun getAllSelectedFilesCount(): Int? {
        return if (multiSelectManager.isSelectAllOn) {
            fileListViewModel.lastItemCount?.count ?: fileAdapter.itemCount
        } else {
            null
        }
    }

    override fun onIndividualActionSuccess(type: BulkOperationType, data: Any) {
        when (type) {
            BulkOperationType.TRASH,
            BulkOperationType.MOVE,
            BulkOperationType.RESTORE_IN,
            BulkOperationType.RESTORE_TO_ORIGIN,
            BulkOperationType.DELETE_PERMANENTLY -> {
                runBlocking(Dispatchers.Main) { fileAdapter.deleteByFileId(data as Int) }
            }
            BulkOperationType.ADD_FAVORITES -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    fileAdapter.notifyFileChanged(data as Int) { file -> if (!file.isManaged) file.isFavorite = true }
                }
            }
            BulkOperationType.MANAGE_CATEGORIES,
            BulkOperationType.COPY,
            BulkOperationType.COLOR_FOLDER,
            BulkOperationType.ADD_OFFLINE,
            BulkOperationType.REMOVE_OFFLINE,
            BulkOperationType.REMOVE_FAVORITES -> Unit
        }
    }

    override fun onAllIndividualActionsFinished(type: BulkOperationType) {
        refreshActivities()
    }

    override fun updateFileProgressByFileId(fileId: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)?) {
        fileAdapter.updateFileProgressByFileId(fileId, progress, onComplete)
    }

    private inner class SortFiles : () -> Unit {
        override fun invoke() {
            getBackNavigationResult<SortType>(SORT_TYPE_OPTION_KEY) { newSortType ->
                trackEvent("fileList", newSortType.name)
                fileListViewModel.sortType = newSortType
                _binding?.sortButton?.setText(fileListViewModel.sortType.translation)

                downloadFiles(fileListViewModel.isSharedWithMe, true)

                UiSettings(requireContext()).sortType = newSortType
                refreshActivities()
            }
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            isDownloading = true
            fileAdapter.isComplete = false

            val sourceRestrictionType = if (ignoreCache) ONLY_FROM_REMOTE else UNRESTRICTED

            getFolderFiles(sourceRestrictionType, isNewSort, onFinish = {
                it?.let { result ->

                    if (fileAdapter.itemCount == 0 || !result.isFirstPage || isNewSort) {

                        FileController.getRealmLiveFiles(
                            parentId = folderId,
                            order = fileListViewModel.sortType,
                            realm = mainViewModel.realm
                        ).apply { fileAdapter.updateFileList(this) }

                        multiSelectManager.currentFolder = result.parentFolder

                        mainViewModel.setCurrentFolder(multiSelectManager.currentFolder)
                        changeNoFilesLayoutVisibility(
                            hideFileList = fileAdapter.fileList.isEmpty(),
                            changeControlsVisibility = result.parentFolder != null
                        )
                    }

                    fileAdapter.isComplete = result.isComplete
                    fileAdapter.hideLoading()
                    refreshActivities()
                    result.parentFolder?.let { folder -> fileListViewModel.updateOfflineFilesIfNeeded(folder, result.files) }

                } ?: run {
                    changeNoFilesLayoutVisibility(
                        hideFileList = fileAdapter.itemCount == 0,
                        changeControlsVisibility = true
                    )
                    fileAdapter.isComplete = true
                }
                isDownloading = false
                showLoadingTimer.cancel()
                binding.swipeRefreshLayout.isRefreshing = false
            })
        }
    }

    data class FolderFilesResult(
        val parentFolder: File? = null,
        val files: ArrayList<File>,
        val isComplete: Boolean,
        val isFirstPage: Boolean,
        val isNewSort: Boolean,
        @StringRes val errorRes: Int? = null,
    )

    /**
     * Will change the noFilesLayout visibility
     * @param hideFileList will hide or show the fileList in order to replace (or not) by the `no-files` layout
     * @param changeControlsVisibility will determine if we need to touch the controls visibility based on no-network/no-files
     * @param ignoreOffline will allow to ignore if we're offline to show `No Files` instead of `No Connection` in all cases
     */
    internal fun changeNoFilesLayoutVisibility(
        hideFileList: Boolean,
        changeControlsVisibility: Boolean,
        ignoreOffline: Boolean = false
    ) {
        if (_binding == null) return

        with(binding) {
            val isOffline = mainViewModel.isInternetAvailable.value == false
            val hasFilesAndIsOffline = !hideFileList && isOffline

            sortLayout.isGone = hideFileList

            if (changeControlsVisibility) {
                val isFileListDestination = findNavController().currentDestination?.id == R.id.fileListFragment
                noNetworkInclude.noNetwork.isVisible = hasFilesAndIsOffline
                toolbar.menu?.findItem(R.id.searchItem)?.isVisible = !hideFileList && isFileListDestination
            }

            noFilesLayout.toggleVisibility(
                noNetwork = isOffline && !ignoreOffline,
                isVisible = hideFileList,
                showRefreshButton = changeControlsVisibility
            )
        }
    }

    open fun onRestartItemsClicked() = Unit

    open fun onCloseItemsClicked() = Unit

    companion object {
        const val REFRESH_FAVORITE_FILE = "force_list_refresh"
        const val CANCELLABLE_MAIN_KEY = "cancellable_main"
        const val CANCELLABLE_TITLE_KEY = "cancellable_message"
        const val CANCELLABLE_ACTION_KEY = "cancellable_action"
        const val SORT_TYPE_OPTION_KEY = "sort_type_option"

        const val ACTIVITIES_REFRESH_DELAY = 5_000L

        // Beware, if this value is modified, the Categories' layouts should be modified accordingly.
        const val MAX_DISPLAYED_CATEGORIES = 3

        const val MATOMO_CATEGORY = "fileListFileAction"
    }
}
