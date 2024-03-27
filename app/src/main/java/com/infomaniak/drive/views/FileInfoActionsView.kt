/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.navigation.fragment.findNavController
import androidx.work.WorkInfo
import com.google.android.material.switchmaterial.SwitchMaterial
import com.infomaniak.drive.MatomoDrive.toFloat
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
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.databinding.ViewFileInfoActionsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.moveFileClicked
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FileInfoActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val binding by lazy { ViewFileInfoActionsBinding.inflate(LayoutInflater.from(context), this, true) }

    private var observeDownloadOffline: LiveData<MutableList<WorkInfo>>? = null
    private lateinit var currentFile: File
    private lateinit var mainViewModel: MainViewModel

    private lateinit var ownerFragment: Fragment
    private lateinit var onItemClickListener: OnItemClickListener
    private lateinit var selectFolderResultLauncher: ActivityResultLauncher<Intent>
    private var isSharedWithMe = false

    val openWith get() = binding.openWith

    fun init(
        ownerFragment: Fragment,
        mainViewModel: MainViewModel,
        onItemClickListener: OnItemClickListener,
        selectFolderResultLauncher: ActivityResultLauncher<Intent>,
        isSharedWithMe: Boolean = false,
    ) {
        this.isSharedWithMe = isSharedWithMe
        this.mainViewModel = mainViewModel
        this.onItemClickListener = onItemClickListener
        this.ownerFragment = ownerFragment
        this.selectFolderResultLauncher = selectFolderResultLauncher
        initOnClickListeners()
    }

    fun updateCurrentFile(file: File) = with(binding) {
        currentFile = file
        refreshBottomSheetUi(currentFile)
        manageCategories.isVisible = DriveInfosController.getCategoryRights(file.driveId).canPutCategoryOnFile
                && !file.isDisabled()

        if (currentFile.isFromActivities) {
            quickActionsLayout.isGone = true
            actionListLayout.isGone = true
        } else {
            quickActionsLayout.isVisible = true
            actionListLayout.isVisible = true

            // TODO - Enhanceable code : Replace these let by an autonomous view with "enabled/disabled" method ?
            currentFile.rights.let { rights ->
                val isOnline = mainViewModel.isInternetAvailable.value == true

                displayInfo.isEnabled = isOnline
                disabledInfo.isGone = isOnline

                (rights?.canShare == true && isOnline).let { rightsEnabled ->
                    fileRights.isEnabled = rightsEnabled
                    disabledFileRights.isGone = rightsEnabled
                }

                (rights?.canBecomeShareLink == true && isOnline || currentFile.sharelink != null || !file.dropbox?.url.isNullOrBlank()).let { publicLinkEnabled ->
                    sharePublicLink.isEnabled = publicLinkEnabled
                    disabledPublicLink.isGone = publicLinkEnabled
                    if (!file.dropbox?.url.isNullOrBlank()) {
                        sharePublicLinkText.text = context.getString(R.string.buttonShareDropboxLink)
                    }
                }

                ((file.isFolder() && rights?.canCreateFile == true && rights.canCreateDirectory) || !file.isFolder()).let { sendCopyEnabled ->
                    sendCopy.isEnabled = sendCopyEnabled
                    disabledSendCopy.isGone = sendCopyEnabled
                }

                addFavorites.isVisible = rights?.canUseFavorite == true
                availableOffline.isGone = isSharedWithMe || currentFile.getOfflineFile(context) == null
                deleteFile.isVisible = rights?.canDelete == true && !file.isImporting()
                downloadFile.isVisible = rights?.canRead == true
                duplicateFile.isGone = rights?.canRead == false
                        || isSharedWithMe
                        || currentFile.getVisibilityType() == IS_TEAM_SPACE
                        || currentFile.getVisibilityType() == IS_SHARED_SPACE
                editDocument.isVisible = (currentFile.hasOnlyoffice && rights?.canWrite == true)
                        || (currentFile.conversion?.whenOnlyoffice == true)
                leaveShare.isVisible = rights?.canLeave == true
                cancelExternalImport.isVisible = file.isImporting()
                moveFile.isVisible = rights?.canMove == true && !isSharedWithMe && !file.isImporting()
                renameFile.isVisible = rights?.canRename == true && !isSharedWithMe && !file.isImporting()
                goToFolder.isVisible = isGoToFolderVisible()
            }

            if (currentFile.isDropBox() || currentFile.rights?.canBecomeDropbox == true) {
                dropBox.text = context.getString(
                    if (currentFile.isDropBox()) R.string.buttonManageDropBox else R.string.buttonConvertToDropBox
                )
                dropBox.setOnClickListener { onItemClickListener.dropBoxClicked(isDropBox = currentFile.isDropBox()) }
                dropBox.isVisible = true
            } else {
                dropBox.isGone = true
            }

            if (currentFile.isFolder()) {
                sendCopyIcon.setImageResource(R.drawable.ic_add)
                sendCopyText.setText(R.string.buttonAdd)
                availableOffline.isGone = true
                openWith.isGone = true
                coloredFolder.isVisible = currentFile.isAllowedToBeColored()
            }
        }
    }

    private fun isGoToFolderVisible(): Boolean {
        val previousDestinationId = ownerFragment.findNavController().previousBackStackEntry?.destination?.id
        val parentFile = FileController.getParentFile(currentFile.id)
        return previousDestinationId != R.id.fileListFragment && parentFile != null
    }

    fun scrollToTop() {
        binding.scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun openAddFileBottom() {
        mainViewModel.currentFolderOpenAddFileBottom.value = currentFile
        ownerFragment.safeNavigate(R.id.addFileBottomSheetDialog)
    }

    private fun initOnClickListeners() = with(binding) {
        editDocument.setOnClickListener { onItemClickListener.editDocumentClicked() }
        displayInfo.setOnClickListener { onItemClickListener.displayInfoClicked() }
        fileRights.setOnClickListener { onItemClickListener.fileRightsClicked() }
        sendCopy.setOnClickListener { shareFile() }
        sharePublicLink.setOnClickListener { view ->
            view.isClickable = false
            onItemClickListener.sharePublicLink { view.isClickable = true }
        }
        openWith.setOnClickListener { onItemClickListener.openWithClicked() }
        downloadFile.setOnClickListener { onItemClickListener.downloadFileClicked() }
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
                isChecked = (view as SwitchMaterial).isChecked
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
                if (isBottomSheetFragmentView) {
                    findNavController().popBackStack()
                }
            }
        }
        availableOffline.setOnClickListener { availableOfflineSwitch.performClick() }
        moveFile.setOnClickListener {
            onItemClickListener.moveFileClicked(currentFile.parentId, selectFolderResultLauncher, mainViewModel)
        }
        duplicateFile.setOnClickListener { onItemClickListener.duplicateFileClicked() }
        renameFile.setOnClickListener { onItemClickListener.renameFileClicked() }
        deleteFile.setOnClickListener { onItemClickListener.deleteFileClicked() }
        goToFolder.setOnClickListener { onItemClickListener.goToFolder() }
    }

    private fun shareFile() {
        if (currentFile.isFolder()) {
            openAddFileBottom()
        } else {
            ownerFragment.requireContext().shareFile { CloudStorageProvider.createShareFileUri(context, currentFile) }
        }
    }

    /**
     * Download [currentFile] to put it offline
     *
     * @return true if the file has been successfully downloaded, false if its name contains forbidden characters
     */
    fun downloadAsOfflineFile(): Boolean {
        with(currentFile) {
            if (Utils.getInvalidFileNameCharacter(name) != null) return false

            val cacheFile = getCacheFile(context)
            if (cacheFile.exists()) {
                getOfflineFile(context)?.let { offlineFile ->
                    Utils.moveCacheFileToOffline(this, cacheFile, offlineFile)
                    CoroutineScope(Dispatchers.IO).launch { FileController.updateOfflineStatus(id, true) }
                    isOffline = true
                    onItemClickListener.onCacheAddedToOffline()
                }
            } else {
                Utils.downloadAsOfflineFile(context, this)
                if (isPendingOffline(context)) mainViewModel.updateOfflineFile.value = id
            }
            refreshBottomSheetUi(this)
        }

        return true
    }

    fun downloadFile(drivePermissions: DrivePermissions, onSuccess: () -> Unit) {
        if (drivePermissions.checkWriteStoragePermission()) {
            val fileName = if (currentFile.isFolder()) "${currentFile.name}.zip" else currentFile.name
            DownloadManagerUtils.scheduleDownload(context, ApiRoutes.downloadFile(currentFile), fileName)
            onSuccess()
        }
    }

    fun createPublicShareLink(
        onSuccess: ((sharelinkUrl: String) -> Unit)? = null,
        onError: ((translatedError: String) -> Unit)? = null
    ) {
        when {
            currentFile.dropbox != null -> onSuccess?.invoke(currentFile.dropbox?.url!!)
            currentFile.sharelink != null -> onSuccess?.invoke(currentFile.sharelink?.url!!)
            else -> {
                showCopyPublicLinkLoader(true)
                mainViewModel.createShareLink(currentFile).observe(ownerFragment) { postShareResponse ->
                    when {
                        postShareResponse?.isSuccess() == true -> {
                            postShareResponse.data?.let { sharelink ->
                                updateFilePublicLink(sharelink)
                                onSuccess?.invoke(sharelink.url)
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
            FileController.updateFile(currentFile.id) { it.sharelink = shareLink }
        }
        currentFile.sharelink = shareLink
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
        if (!binding.availableOffline.isEnabled && !currentFile.isPendingOffline(context)) {
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
            val progress = workInfo.progress.getInt(DownloadWorker.PROGRESS, 100)

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
        sharePublicLinkText.setText(if (file.sharelink == null) R.string.buttonCreatePublicLink else R.string.buttonSharePublicLink)

        setOfflineItemUi(file, isOfflineProgress)
    }

    private fun setOfflineItemUi(file: File, isOfflineProgress: Boolean): Unit = with(binding) {
        // Update file item progress
        if (isOfflineProgress) fileView.progressLayout.setupFileProgress(file) else fileView.setFileItem(file)

        val isPendingOffline = file.isPendingOffline(context)
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
        mainViewModel: MainViewModel,
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

        val ownerFragment: Fragment
        val currentFile: File

        private val context: Context get() = ownerFragment.requireContext()

        private fun trackFileActionEvent(name: String, value: Boolean? = null) {
            context.trackFileActionEvent(name, value = value?.toFloat())
        }

        fun addFavoritesClicked() = trackFileActionEvent("favorite", !currentFile.isFavorite)
        fun cancelExternalImportClicked() = trackFileActionEvent("cancelExternalImport")
        fun colorFolderClicked(color: String?) = context.trackEvent("colorFolder", "switch")
        fun displayInfoClicked()
        fun downloadFileClicked() = trackFileActionEvent("download")
        fun dropBoxClicked(isDropBox: Boolean) = trackFileActionEvent("convertToDropbox", isDropBox)
        fun fileRightsClicked()
        fun goToFolder()
        fun manageCategoriesClicked(fileId: Int)
        fun onCacheAddedToOffline() = Unit
        fun onDeleteFile(onApiResponse: () -> Unit)
        fun onDuplicateFile(result: String, onApiResponse: () -> Unit)
        fun onLeaveShare(onApiResponse: () -> Unit)
        fun onMoveFile(destinationFolder: File)
        fun onRenameFile(newName: String, onApiResponse: () -> Unit)
        fun openWithClicked() = trackFileActionEvent("openWith")
        fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile)
        fun sharePublicLink(onActionFinished: () -> Unit) = trackFileActionEvent("shareLink")

        fun editDocumentClicked() {
            trackFileActionEvent("edit")
            ownerFragment.openOnlyOfficeDocument(currentFile)
        }

        fun onSelectFolderResult(data: Intent?) {
            data?.extras?.let { bundle ->
                SelectFolderActivityArgs.fromBundle(bundle).apply {
                    onMoveFile(File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId))
                }
            }
        }

        fun availableOfflineSwitched(fileInfoActionsView: FileInfoActionsView, isChecked: Boolean): Boolean {
            currentFile.apply {
                when {
                    isOffline && isChecked -> Unit
                    !isOffline && !isChecked -> Unit
                    isChecked -> {
                        trackFileActionEvent("offline", true)
                        return fileInfoActionsView.downloadAsOfflineFile()
                    }
                    else -> {
                        trackFileActionEvent("offline", false)
                        val offlineLocalPath = getOfflineFile(context)
                        val cacheFile = getCacheFile(context)
                        offlineLocalPath?.let { removeOfflineFile(offlineLocalPath, cacheFile) }
                    }
                }
            }

            return true
        }

        fun moveFileClicked(
            folderId: Int?,
            selectFolderResultLauncher: ActivityResultLauncher<Intent>,
            mainViewModel: MainViewModel
        ) {
            trackFileActionEvent("move")
            context.moveFileClicked(folderId, selectFolderResultLauncher, mainViewModel)
        }

        fun duplicateFileClicked() {
            currentFile.apply {
                Utils.createPromptNameDialog(
                    context = context,
                    title = R.string.buttonDuplicate,
                    fieldName = R.string.fileInfoInputDuplicateFile,
                    positiveButton = R.string.buttonCopy,
                    fieldValue = name,
                    selectedRange = getFileName().length
                ) { dialog, name ->
                    onDuplicateFile(name) {
                        trackFileActionEvent("copy")
                        dialog.dismiss()
                    }
                }
            }
        }

        fun leaveShare() {
            currentFile.apply {
                Utils.createConfirmation(
                    context = context,
                    title = context.getString(R.string.modalLeaveShareTitle),
                    message = context.getString(R.string.modalLeaveShareDescription, name),
                    autoDismiss = false
                ) { dialog ->
                    onLeaveShare {
                        trackFileActionEvent("stopShare")
                        dialog.dismiss()
                    }
                }
            }
        }

        fun renameFileClicked() {
            currentFile.apply {
                Utils.createPromptNameDialog(
                    context = context,
                    title = R.string.buttonRename,
                    fieldName = if (isFolder()) R.string.hintInputDirName else R.string.hintInputFileName,
                    positiveButton = R.string.buttonSave,
                    fieldValue = name,
                    selectedRange = getFileName().length
                ) { dialog, name ->
                    onRenameFile(name) {
                        trackFileActionEvent("rename")
                        dialog.dismiss()
                    }
                }
            }
        }

        fun deleteFileClicked() {
            Utils.confirmFileDeletion(context, fileName = currentFile.name) { dialog ->
                onDeleteFile {
                    trackFileActionEvent("putInTrash")
                    dialog.dismiss()
                }
            }
        }
    }
}
