/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.drive.views

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.FrameLayout
import androidx.activity.result.ActivityResultLauncher
import androidx.annotation.CallSuper
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import com.google.android.material.switchmaterial.SwitchMaterial
import com.infomaniak.core.ksuite.data.KSuite
import com.infomaniak.drive.MatomoDrive.MatomoCategory
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.VisibilityType.IS_SHARED_SPACE
import com.infomaniak.drive.data.models.File.VisibilityType.IS_TEAM_SPACE
import com.infomaniak.drive.data.models.Rights
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.services.BaseDownloadWorker
import com.infomaniak.drive.databinding.ViewFileInfoActionsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.fileList.ShareLinkViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.SingleOperation
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.duplicateFilesClicked
import com.infomaniak.drive.utils.Utils.moveFileClicked
import com.infomaniak.drive.utils.openOnlyOfficeDocument
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setupFileProgress
import com.infomaniak.drive.utils.shareFile
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class FileInfoActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewFileInfoActionsBinding.inflate(LayoutInflater.from(context), this, true) }

    private var observeDownloadOffline: LiveData<List<WorkInfo>>? = null
    private lateinit var currentFile: File
    private lateinit var mainViewModel: MainViewModel
    private lateinit var shareLinkViewModel: ShareLinkViewModel

    private lateinit var ownerFragment: Fragment
    private lateinit var onItemClickListener: OnItemClickListener
    private lateinit var selectFolderResultLauncher: ActivityResultLauncher<Intent>
    private var isSharedWithMe = false

    private val canCreateDropbox by lazy { AccountUtils.getCurrentDrive(forceRefresh = true)?.canCreateDropbox == true }

    val openWith get() = binding.openWith

    fun init(
        ownerFragment: Fragment,
        mainViewModel: MainViewModel,
        shareLinkViewModel: ShareLinkViewModel,
        onItemClickListener: OnItemClickListener,
        selectFolderResultLauncher: ActivityResultLauncher<Intent>,
        isSharedWithMe: Boolean = false,
    ) {
        this.isSharedWithMe = isSharedWithMe
        this.mainViewModel = mainViewModel
        this.shareLinkViewModel = shareLinkViewModel
        this.onItemClickListener = onItemClickListener
        this.ownerFragment = ownerFragment
        this.selectFolderResultLauncher = selectFolderResultLauncher
        initOnClickListeners()
    }

    // TODO - Enhanceable code : Replace these let by an autonomous view with "enabled/disabled" method ?
    private fun computeFileRights(file: File, rights: Rights) = with(binding) {
        val hasNetwork = mainViewModel.hasNetwork
        displayInfo.isEnabled = hasNetwork
        disabledInfo.isGone = hasNetwork

        (rights.canShare && hasNetwork).let { rightsEnabled ->
            fileRights.isEnabled = rightsEnabled
            disabledFileRights.isGone = rightsEnabled
        }

        val isPublicLinkEnabled = rights.canBecomeShareLink && hasNetwork
                || currentFile.shareLink != null
                || !file.dropbox?.url.isNullOrBlank()

        sharePublicLink.isEnabled = isPublicLinkEnabled
        disabledPublicLink.isGone = isPublicLinkEnabled

        if (!file.dropbox?.url.isNullOrBlank()) {
            sharePublicLinkText.text = context.getString(R.string.buttonShareDropboxLink)
        }

        ((file.isFolder() && rights.canCreateFile && rights.canCreateDirectory) || !file.isFolder()).let { sendCopyEnabled ->
            sendCopy.isEnabled = sendCopyEnabled
            disabledSendCopy.isGone = sendCopyEnabled
        }

        addFavorites.isVisible = rights.canUseFavorite == true && !isSharedWithMe
        availableOffline.isGone = isSharedWithMe || currentFile.getOfflineFile(context) == null
        deleteFile.isVisible = rights.canDelete == true && !file.isImporting() && !isSharedWithMe
        downloadFile.isVisible = rights.canRead == true
        duplicateFile.isGone = rights.canRead == false
                || isSharedWithMe
                || currentFile.getVisibilityType() == IS_TEAM_SPACE
                || currentFile.getVisibilityType() == IS_SHARED_SPACE
        editDocument.isVisible = (currentFile.hasOnlyoffice && rights.canWrite)
                || (currentFile.conversion?.whenOnlyoffice == true)
        leaveShare.isVisible = rights.canLeave == true
        cancelExternalImport.isVisible = file.isImporting()
        moveFile.isVisible = rights.canMove == true && !isSharedWithMe && !file.isImporting()
        renameFile.isVisible = rights.canRename == true && !file.isImporting()
        goToFolder.isVisible = isGoToFolderVisible()
    }

    fun updateCurrentFile(file: File) = with(binding) {
        currentFile = file
        refreshBottomSheetUi(currentFile)
        manageCategories.isVisible = DriveInfosController.getCategoryRights(file.driveId).canPutOnFile
                && !file.isDisabled()
                && !isSharedWithMe

        if (currentFile.isFromActivities) {
            quickActionsLayout.isGone = true
            actionListLayout.isGone = true

            return@with
        }

        quickActionsLayout.isVisible = true
        actionListLayout.isVisible = true

        currentFile.rights?.let { rights ->
            computeFileRights(file, rights)
        }

        val drive = AccountUtils.getCurrentDrive() ?: return@with

        setupDropboxItem(drive)

        if (currentFile.isFolder()) {
            sendCopyIcon.setImageResource(R.drawable.ic_add)
            sendCopyText.setText(R.string.buttonAdd)
            availableOffline.isGone = true
            openWith.isGone = true
            setupColoredFolderVisibility(drive)
        }
    }

    fun setPrintVisibility(isGone: Boolean) {
        binding.print.isGone = isGone
    }

    fun scrollToTop() {
        binding.scrollView.fullScroll(FOCUS_UP)
    }

    private fun isGoToFolderVisible(): Boolean {
        val previousDestinationId = ownerFragment.findNavController().previousBackStackEntry?.destination?.id
        val parentFile = FileController.getParentFile(currentFile.id)
        return previousDestinationId != R.id.fileListFragment && parentFile != null
    }

    private fun openAddFileBottom() {
        mainViewModel.currentFolderOpenAddFileBottom.value = currentFile
        ownerFragment.safeNavigate(R.id.addFileBottomSheetDialog)
    }

    private fun setupDropboxItem(drive: Drive) = with(binding.dropBox) {
        if (currentFile.isDropBox() || currentFile.rights?.canBecomeDropbox == true) {
            text = context.getString(
                if (currentFile.isDropBox()) R.string.buttonManageDropBox else R.string.buttonConvertToDropBox
            )
            setOnClickListener {
                onItemClickListener.dropBoxClicked(currentFile.isDropBox(), canCreateDropbox, drive.kSuite, drive.isAdmin)
            }
            isVisible = true
            shouldShowMyKSuiteChip = drive.isKSuitePersoFree && !canCreateDropbox && !currentFile.isDropBox()
            shouldShowKSuiteProChip = drive.isKSuiteProUpgradable && !canCreateDropbox && !currentFile.isDropBox()
        } else {
            isGone = true
        }
    }

    private fun setupColoredFolderVisibility(drive: Drive) = with(binding.coloredFolder) {
        // Displays the item to change folder color for all folder if the user is in free tier to display My kSuite Ad.
        // But only displays it for the folder that can really be colored if it's a paid drive.
        isVisible = (drive.isKSuiteFreeTier || currentFile.isAllowedToBeColored()) && !isSharedWithMe
        shouldShowMyKSuiteChip = drive.isKSuitePersoFree
        shouldShowKSuiteProChip = drive.isKSuiteProUpgradable
    }

    private fun initOnClickListeners() = with(binding) {
        editDocument.setOnClickListener { onItemClickListener.editDocumentClicked(mainViewModel) }
        displayInfo.setOnClickListener { onItemClickListener.displayInfoClicked() }
        fileRights.setOnClickListener { onItemClickListener.fileRightsClicked() }
        sendCopy.setOnClickListener { shareFile() }
        sharePublicLink.setOnClickListener { view ->
            view.isClickable = false
            onItemClickListener.sharePublicLink { view.isClickable = true }
        }
        openWith.setOnClickListener { onItemClickListener.openWith() }
        manageCategories.setOnClickListener { onItemClickListener.manageCategoriesClicked(currentFile.id) }
        coloredFolder.setOnClickListener { onItemClickListener.colorFolderClicked(currentFile.color) }
        addFavorites.setOnClickListener {
            addFavorites.isEnabled = false
            onItemClickListener.addFavoritesClicked()
        }
        leaveShare.setOnClickListener { onItemClickListener.leaveShare() }
        cancelExternalImport.setOnClickListener { onItemClickListener.cancelExternalImportClicked() }
        // Use OnClickListener instead of OnCheckedChangeListener because the latter is unnecessarily called on every
        // refreshBottomSheetUI calls
        availableOfflineSwitch.setOnClickListener { view ->
            val downloadError = !onItemClickListener.availableOfflineSwitched(
                fileInfoActionsView = this@FileInfoActionsView,
                isChecked = (view as SwitchMaterial).isChecked,
            )

            with(ownerFragment) {
                val isBottomSheetFragmentView =
                    findNavController().currentBackStackEntry?.destination?.id == R.id.fileInfoActionsBottomSheetDialog
                if (downloadError) {
                    availableOfflineSwitch.isChecked = false
                    val fileName = currentFile.name
                    showSnackbar(
                        getString(R.string.snackBarInvalidFileNameError, Utils.getInvalidFileNameCharacter(fileName), fileName),
                        showAboveFab = isBottomSheetFragmentView,
                    )
                }
                if (isBottomSheetFragmentView) findNavController().popBackStack()
            }
        }
        availableOffline.setOnClickListener { availableOfflineSwitch.performClick() }
        print.setOnClickListener { onItemClickListener.printClicked() }
        downloadFile.setOnClickListener { onItemClickListener.downloadFileClicked() }
        moveFile.setOnClickListener {
            onItemClickListener.moveFileClicked(currentFile.parentId, selectFolderResultLauncher, mainViewModel)
        }
        duplicateFile.setOnClickListener { onItemClickListener.duplicateFileClicked(selectFolderResultLauncher, mainViewModel) }
        renameFile.setOnClickListener { onItemClickListener.renameFileClicked() }
        deleteFile.setOnClickListener { onItemClickListener.deleteFileClicked() }
        goToFolder.setOnClickListener { onItemClickListener.goToFolder() }
    }

    private fun shareFile() {
        if (currentFile.isFolder()) {
            openAddFileBottom()
        } else {
            ownerFragment.requireContext().apply {
                trackFileActionEvent(MatomoName.SendFileCopy)
                val userDrive = UserDrive(sharedWithMe = isSharedWithMe)
                shareFile { CloudStorageProvider.createShareFileUri(context, currentFile, userDrive) }
            }
        }
    }

    /**
     * Download [currentFile] to put it offline
     *
     * @return true if the file has been successfully downloaded, false if its name contains forbidden characters
     */
    fun downloadAsOfflineFile(): Boolean = with(currentFile) {

        if (Utils.getInvalidFileNameCharacter(name) != null) return@with false

        val cacheFile = getCacheFile(context)
        if (cacheFile.exists()) {
            getOfflineFile(context)?.let { offlineFile ->
                Utils.moveCacheFileToOffline(file = this, cacheFile, offlineFile)
                CoroutineScope(Dispatchers.IO).launch { FileController.updateOfflineStatus(id, isOffline = true) }
                isOffline = true
                onItemClickListener.onCacheAddedToOffline()
            }
        } else {
            Utils.downloadAsOfflineFile(context, file = this)
            if (isMarkedAsOffline) mainViewModel.updateOfflineFile.value = id
        }
        refreshBottomSheetUi(file = this)

        return@with true
    }

    fun createPublicShareLink(
        onSuccess: ((shareLinkUrl: String) -> Unit)? = null,
        onError: ((translatedError: String) -> Unit)? = null
    ) {
        when {
            currentFile.dropbox != null -> onSuccess?.invoke(currentFile.dropbox?.url!!)
            currentFile.shareLink != null -> onSuccess?.invoke(currentFile.shareLink?.url!!)
            else -> {
                showCopyPublicLinkLoader(true)
                shareLinkViewModel.createShareLink(currentFile).observe(ownerFragment) { postShareResponse ->
                    when {
                        postShareResponse?.isSuccess() == true -> {
                            postShareResponse.data?.let { shareLink ->
                                updateFilePublicLink(shareLink)
                                onSuccess?.invoke(shareLink.url)
                            }
                        }
                        else -> {
                            onError?.invoke(context.getString(postShareResponse.translateError()))
                        }
                    }
                    showCopyPublicLinkLoader(false)
                }
            }
        }
    }

    private fun updateFilePublicLink(shareLink: ShareLink) {
        CoroutineScope(Dispatchers.IO).launch {
            FileController.updateFile(currentFile.id) { it.shareLink = shareLink }
        }
        currentFile.shareLink = shareLink
        refreshBottomSheetUi(currentFile)
    }

    private fun showCopyPublicLinkLoader(show: Boolean) = with(binding) {
        sharePublicLinkLayout.isGone = show
        copyPublicLinkLoader.isVisible = show
    }

    /**
     * This allows you to update when necessary, each time you return to the application.
     * To be called only in the [Lifecycle.Event.ON_RESUME].
     */
    fun updateAvailableOfflineItem() {
        if (!binding.availableOffline.isEnabled && !currentFile.isMarkedAsOffline) {
            currentFile.isOffline = true
            refreshBottomSheetUi(currentFile)
        }
    }

    private fun enableAvailableOffline(isEnabled: Boolean) = with(binding) {
        availableOfflineSwitch.isEnabled = isEnabled
        availableOffline.isEnabled = isEnabled
    }

    fun observeOfflineProgression(lifecycleOwner: LifecycleOwner, updateFile: ((fileId: Int) -> Unit)? = null) {
        observeDownloadOffline = mainViewModel.observeDownloadOffline(context.applicationContext)
        observeDownloadOffline?.observe(lifecycleOwner) { workInfoList ->
            if (workInfoList.isEmpty()) return@observe

            val workInfo = workInfoList.firstOrNull { it.state == WorkInfo.State.RUNNING }
                ?: workInfoList.firstOrNull { workInfo -> workInfo.tags.any { it == currentFile.getWorkerTag() } }
                ?: return@observe

            val tag = workInfo.tags.firstOrNull { it == currentFile.getWorkerTag() }
            if (currentFile.getWorkerTag() != tag) return@observe

            val fileId: Int = currentFile.id
            val progress = workInfo.progress.getInt(BaseDownloadWorker.PROGRESS, 100)

            SentryLog.d("FileInfoActionsView", "observeOfflineProgression> $progress% file:$fileId state:${workInfo.state}")

            currentFile.currentProgress = progress
            // Check isOffline because progressing to 100 doesn't necessarily mean it's finish
            if (progress == 100 && workInfo.state.isFinished && currentFile.isOfflineFile(context)) {
                updateFile?.invoke(fileId)
                currentFile.isOffline = true
                refreshBottomSheetUi(currentFile)
            } else {
                refreshBottomSheetUi(currentFile, true)
            }
        }

        mainViewModel.updateVisibleFiles.observe(lifecycleOwner) {
            currentFile.currentProgress = Utils.INDETERMINATE_PROGRESS
            refreshBottomSheetUi(currentFile)
        }
    }

    fun removeOfflineObservations(lifecycleOwner: LifecycleOwner) {
        observeDownloadOffline?.removeObservers(lifecycleOwner)
    }

    fun refreshBottomSheetUi(file: File, isOfflineProgress: Boolean = false): Unit = with(binding) {
        addFavorites.apply {
            isEnabled = true
            isActivated = file.isFavorite
            text = context.getString(if (file.isFavorite) R.string.buttonRemoveFavorites else R.string.buttonAddFavorites)
        }
        sharePublicLinkText.setText(if (file.shareLink == null) R.string.buttonCreatePublicLink else R.string.buttonSharePublicLink)

        setOfflineItemUi(file, isOfflineProgress)
    }

    private val fileToShowFlow = MutableSharedFlow<File>(extraBufferCapacity = 1)

    private var viewAddedCoroutine: Job? = null

    override fun onViewAdded(child: View?) {
        super.onViewAdded(child)
        val scope = findViewTreeLifecycleOwner()!!.lifecycleScope
        viewAddedCoroutine = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            fileToShowFlow.collectLatest { file ->
                binding.fileView.setFileItem(file)
            }
        }
    }

    override fun onViewRemoved(child: View?) {
        super.onViewRemoved(child)
        viewAddedCoroutine?.cancel()
        viewAddedCoroutine = null
    }

    private fun setOfflineItemUi(file: File, isOfflineProgress: Boolean): Unit = with(binding) {
        // Update file item progress
        if (isOfflineProgress) fileView.progressLayout.setupFileProgress(file) else fileToShowFlow.tryEmit(file)

        val isPendingOffline = file.isMarkedAsOffline
        val isItemInteractable = !isPendingOffline || file.currentProgress == 100
        enableAvailableOffline(isItemInteractable)

        val isOfflineFile = file.isOfflineFile(context)
        if (isItemInteractable) availableOfflineSwitch.isChecked = isOfflineFile

        when {
            // We can have a currentProgress in [0,99] yet the file is not pending?
            isPendingOffline && file.currentProgress in 0..99 -> {
                availableOfflineComplete.isGone = true
                availableOfflineIcon.isGone = true

                availableOfflineProgress.apply {
                    isGone = true // We need to hide the view before updating its `isIndeterminate`
                    if (isOfflineProgress) {
                        isIndeterminate = false
                        progress = file.currentProgress
                    } else {
                        isIndeterminate = true
                    }
                    isVisible = true
                }
            }
            !isOfflineProgress -> {
                availableOfflineComplete.isVisible = isOfflineFile
                availableOfflineIcon.isGone = isOfflineFile

                availableOfflineProgress.isGone = true
            }
        }
    }

    fun onRenameFile(
        newName: String,
        onSuccess: ((action: CancellableAction) -> Unit)? = null,
        onError: ((translatedError: String) -> Unit)? = null
    ) {
        mainViewModel.renameFile(currentFile, newName).observe(ownerFragment) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse?.data?.let { action ->
                    action.driveId = currentFile.driveId
                    onSuccess?.invoke(action)
                }
            } else {
                onError?.invoke(context.getString(R.string.errorRename))
            }
        }
    }

    interface OnItemClickListener {

        val ownerFragment: Fragment?
        val currentContext: Context
        val currentFile: File?

        fun shareFile()
        fun saveToKDrive()
        fun openWith()
        fun printClicked()

        @CallSuper
        fun addFavoritesClicked() = trackFileActionEvent(MatomoName.Favorite, currentFile?.isFavorite == false)

        @CallSuper
        fun cancelExternalImportClicked() = trackFileActionEvent(MatomoName.CancelExternalImport)

        @CallSuper
        fun colorFolderClicked(color: String?) = trackEvent(MatomoCategory.ColorFolder, MatomoName.Switch)

        fun displayInfoClicked()

        fun downloadFileClicked() = trackFileActionEvent(MatomoName.Download)

        @CallSuper
        fun dropBoxClicked(isDropBox: Boolean, canCreateDropbox: Boolean, kSuite: KSuite?, isAdmin: Boolean) {
            trackFileActionEvent(MatomoName.ConvertToDropbox, isDropBox)
        }

        fun fileRightsClicked()
        fun goToFolder()
        fun manageCategoriesClicked(fileId: Int)
        fun onCacheAddedToOffline()
        fun onDeleteFile(onApiResponse: () -> Unit)
        fun onLeaveShare(onApiResponse: () -> Unit)
        fun onDuplicateFile(destinationFolder: File)
        fun onMoveFile(destinationFolder: File, isSharedWithMe: Boolean = false)
        fun onRenameFile(newName: String, onApiResponse: () -> Unit)
        fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile)

        @CallSuper
        fun sharePublicLink(onActionFinished: () -> Unit) = trackFileActionEvent(MatomoName.ShareLink)

        @CallSuper
        fun editDocumentClicked(mainViewModel: MainViewModel) {
            trackFileActionEvent(MatomoName.Edit)
            currentFile?.let { file ->
                ownerFragment?.openOnlyOfficeDocument(file, mainViewModel.hasNetwork)
            }
        }

        fun onSelectFolderResult(data: Intent?) {
            data?.extras?.let { bundle ->
                SelectFolderActivityArgs.fromBundle(bundle).apply {
                    val file = File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId)
                    when (customArgs?.getString(SINGLE_OPERATION_CUSTOM_TAG)) {
                        SingleOperation.COPY.name -> onDuplicateFile(file)
                        SingleOperation.MOVE.name -> onMoveFile(file, isSharedWithMe)
                        else -> Unit
                    }
                }
            }
        }

        @CallSuper
        fun availableOfflineSwitched(fileInfoActionsView: FileInfoActionsView, isChecked: Boolean): Boolean {
            currentFile?.apply {
                when {
                    isOffline && isChecked -> Unit
                    !isOffline && !isChecked -> Unit
                    isChecked -> {
                        trackFileActionEvent(MatomoName.Offline, true)
                        return fileInfoActionsView.downloadAsOfflineFile()
                    }
                    else -> {
                        trackFileActionEvent(MatomoName.Offline, false)
                        val offlineLocalPath = getOfflineFile(currentContext)
                        val cacheFile = getCacheFile(currentContext)
                        offlineLocalPath?.let { removeOfflineFile(offlineLocalPath, cacheFile) }
                    }
                }
            }

            return true
        }

        @CallSuper
        fun moveFileClicked(
            folderId: Int?,
            selectFolderResultLauncher: ActivityResultLauncher<Intent>,
            mainViewModel: MainViewModel
        ) {
            trackFileActionEvent(MatomoName.Move)
            currentContext.moveFileClicked(folderId, selectFolderResultLauncher, mainViewModel)
        }

        @CallSuper
        fun duplicateFileClicked(selectFolderResultLauncher: ActivityResultLauncher<Intent>, mainViewModel: MainViewModel) {
            trackFileActionEvent(MatomoName.Copy)
            mainViewModel.ignoreSyncOffline = true
            currentContext.duplicateFilesClicked(selectFolderResultLauncher, mainViewModel)
        }

        @CallSuper
        fun leaveShare() = currentFile?.apply {
            Utils.createConfirmation(
                context = currentContext,
                title = currentContext.getString(R.string.modalLeaveShareTitle),
                message = currentContext.getString(R.string.modalLeaveShareDescription, name),
                autoDismiss = false
            ) { dialog ->
                onLeaveShare {
                    trackFileActionEvent(MatomoName.StopShare)
                    dialog.dismiss()
                }
            }
        }

        @CallSuper
        fun renameFileClicked() = currentFile?.apply {
            Utils.createPromptNameDialog(
                context = currentContext,
                title = R.string.buttonRename,
                fieldName = if (isFolder()) R.string.hintInputDirName else R.string.hintInputFileName,
                positiveButton = R.string.buttonSave,
                fieldValue = name,
                selectedRange = getFileName().length
            ) { dialog, name ->
                onRenameFile(name) {
                    trackFileActionEvent(MatomoName.Rename)
                    dialog.dismiss()
                }
            }
        }

        @CallSuper
        fun deleteFileClicked() = currentFile?.let {
            Utils.confirmFileDeletion(currentContext, fileName = it.name) { dialog ->
                onDeleteFile {
                    trackFileActionEvent(MatomoName.PutInTrash)
                    dialog.dismiss()
                }
            }
        }

        companion object {
            fun Context.downloadFile(downloadPermissions: DrivePermissions, file: File, onSuccess: (() -> Unit)? = null) {
                if (downloadPermissions.hasNeededPermissions(requestIfNotGranted = true)) {
                    val fileName = if (file.isFolder()) "${file.name}.zip" else file.name
                    val userBearerToken = AccountUtils.currentUser?.apiToken?.accessToken
                    DownloadManagerUtils.scheduleDownload(
                        context = this,
                        url = ApiRoutes.getDownloadFileUrl(file),
                        name = fileName,
                        userBearerToken = userBearerToken,
                    )
                    onSuccess?.invoke()
                }
            }
        }
    }

    companion object {
        const val SINGLE_OPERATION_CUSTOM_TAG = "single_operation"
    }
}
