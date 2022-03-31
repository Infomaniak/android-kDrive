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
package com.infomaniak.drive.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
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
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.VisibilityType.IS_SHARED_SPACE
import com.infomaniak.drive.data.models.File.VisibilityType.IS_TEAM_SPACE
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackEvent
import com.infomaniak.drive.utils.Utils.moveFileClicked
import kotlinx.android.synthetic.main.view_file_info_actions.view.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class FileInfoActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var observeDownloadOffline: LiveData<MutableList<WorkInfo>>? = null
    private lateinit var currentFile: File
    private lateinit var mainViewModel: MainViewModel

    private lateinit var ownerFragment: Fragment
    private lateinit var onItemClickListener: OnItemClickListener
    private lateinit var selectFolderResultLauncher: ActivityResultLauncher<Intent>
    private var isSharedWithMe = false

    init {
        inflate(context, R.layout.view_file_info_actions, this)
    }

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

    fun updateCurrentFile(file: File) {
        currentFile = file
        refreshBottomSheetUi(currentFile)
        manageCategories.isVisible = DriveInfosController.getCategoryRights().canPutCategoryOnFile && !file.isDisabled()

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

                (rights?.share == true && isOnline).let { rightsEnabled ->
                    fileRights.isEnabled = rightsEnabled
                    disabledFileRights.isGone = rightsEnabled
                }

                (rights?.canBecomeLink == true && isOnline || currentFile.shareLink != null || !file.collaborativeFolder.isNullOrBlank()).let { publicLinkEnabled ->
                    copyPublicLink.isEnabled = publicLinkEnabled
                    disabledPublicLink.isGone = publicLinkEnabled
                    if (!file.collaborativeFolder.isNullOrBlank()) {
                        copyPublicLinkText.text = context.getString(R.string.buttonCopyLink)
                    }
                }

                ((file.isFolder() && rights?.newFile == true && rights.newFolder) || !file.isFolder()).let { sendCopyEnabled ->
                    sendCopy.isEnabled = sendCopyEnabled
                    disabledSendCopy.isGone = sendCopyEnabled
                }

                addFavorites.isVisible = rights?.canFavorite == true
                availableOffline.isGone = isSharedWithMe || currentFile.getOfflineFile(context) == null
                deleteFile.isVisible = rights?.delete == true
                downloadFile.isVisible = rights?.read == true
                duplicateFile.isGone = rights?.read == false
                        || isSharedWithMe
                        || currentFile.getVisibilityType() == IS_TEAM_SPACE
                        || currentFile.getVisibilityType() == IS_SHARED_SPACE
                editDocument.isVisible = (currentFile.onlyoffice && rights?.write == true)
                        || (currentFile.onlyofficeConvertExtension != null)
                leaveShare.isVisible = rights?.leave == true
                moveFile.isVisible = rights?.move == true && !isSharedWithMe
                renameFile.isVisible = rights?.rename == true && !isSharedWithMe
                goToFolder.isVisible = isGoToFolderVisible()
            }

            if (currentFile.isDropBox() || currentFile.rights?.canBecomeCollab == true) {
                dropBoxText.text = context.getString(
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
        scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun shareFile() {
        context.applicationContext?.trackFileActionEvent("sendFileCopy")

        val context = ownerFragment.requireContext()
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            putExtra(Intent.EXTRA_STREAM, CloudStorageProvider.createShareFileUri(context, currentFile))
            type = "*/*"
        }

        ownerFragment.startActivity(Intent.createChooser(shareIntent, ownerFragment.getString(R.string.buttonSendCopy)))
    }

    private fun openAddFileBottom() {
        mainViewModel.currentFolderOpenAddFileBottom.value = currentFile
        ownerFragment.safeNavigate(R.id.addFileBottomSheetDialog)
    }

    private fun initOnClickListeners() {
        editDocument.setOnClickListener { onItemClickListener.editDocumentClicked() }
        displayInfo.setOnClickListener { onItemClickListener.displayInfoClicked() }
        fileRights.setOnClickListener { onItemClickListener.fileRightsClicked() }
        sendCopy.setOnClickListener { if (currentFile.isFolder()) openAddFileBottom() else shareFile() }
        copyPublicLink.setOnClickListener { onItemClickListener.copyPublicLink() }
        openWith.setOnClickListener { onItemClickListener.openWithClicked() }
        downloadFile.setOnClickListener { onItemClickListener.downloadFileClicked() }
        manageCategories.setOnClickListener { onItemClickListener.manageCategoriesClicked(currentFile.id) }
        coloredFolder.setOnClickListener { onItemClickListener.colorFolderClicked(currentFile.color) }
        addFavorites.setOnClickListener {
            addFavorites.isEnabled = false
            onItemClickListener.addFavoritesClicked()
        }
        leaveShare.setOnClickListener { onItemClickListener.leaveShare() }
        // Use OnClickListener instead of OnCheckedChangeListener because the later is unnecessarily called on every 
        // refreshBottomSheetUI calls
        availableOfflineSwitch.setOnClickListener { view ->
            val downloadError = !onItemClickListener.availableOfflineSwitched(this, (view as SwitchMaterial).isChecked)
            with(ownerFragment) {
                if (downloadError) {
                    availableOfflineSwitch.isChecked = false
                    showSnackBarInvalidFileName(currentFile.name)
                }
                with(findNavController()) {
                    if (currentBackStackEntry?.destination?.label == "FileInfoActionsBottomSheetDialog") popBackStack()
                }
            }
        }
        availableOffline.setOnClickListener { availableOfflineSwitch.performClick() }
        moveFile.setOnClickListener {
            val currentFolderId = FileController.getParentFile(currentFile.id)?.id
            onItemClickListener.moveFileClicked(currentFolderId, selectFolderResultLauncher)
        }
        duplicateFile.setOnClickListener { onItemClickListener.duplicateFileClicked() }
        renameFile.setOnClickListener { onItemClickListener.renameFileClicked() }
        deleteFile.setOnClickListener { onItemClickListener.deleteFileClicked() }
        goToFolder.setOnClickListener { onItemClickListener.goToFolder() }
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
            val downloadURL = Uri.parse(ApiRoutes.downloadFile(currentFile))
            val fileName = if (currentFile.isFolder()) "${currentFile.name}.zip" else currentFile.name
            context.startDownloadFile(downloadURL, fileName)
            onSuccess()
        }
    }

    fun createPublicCopyLink(onSuccess: ((file: File?) -> Unit)? = null, onError: ((translatedError: String) -> Unit)? = null) {
        when {
            currentFile.collaborativeFolder != null -> {
                copyPublicLink(currentFile.collaborativeFolder!!)
                onSuccess?.invoke(currentFile)
            }
            currentFile.shareLink != null -> {
                copyPublicLink(currentFile.shareLink!!)
                onSuccess?.invoke(currentFile)
            }
            else -> {
                showCopyPublicLinkLoader(true)
                mainViewModel.postFileShareLink(currentFile).observe(ownerFragment) { postShareResponse ->
                    when {
                        postShareResponse?.isSuccess() == true -> {
                            postShareResponse.data?.url?.let { url ->
                                updateFilePublicLink(url)
                                onSuccess?.invoke(currentFile)
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

    private fun updateFilePublicLink(url: String) {
        CoroutineScope(Dispatchers.IO).launch {
            FileController.updateFile(currentFile.id) { it.shareLink = url }
        }
        copyPublicLink(url)
        currentFile.shareLink = url
        refreshBottomSheetUi(currentFile)
    }

    private fun showCopyPublicLinkLoader(show: Boolean) {
        copyPublicLinkLayout.isGone = show
        copyPublicLinkLoader.isVisible = show
    }

    private fun copyPublicLink(url: String) {
        showCopyPublicLinkLoader(false)
        Utils.copyToClipboard(context, url)
    }

    /**
     * This allows you to update when necessary, each time you return to the application.
     * To be called only in the [Lifecycle.Event.ON_RESUME].
     */
    fun updateAvailableOfflineItem() {
        if (!availableOffline.isEnabled && !currentFile.isPendingOffline(context)) {
            currentFile.isOffline = true
            refreshBottomSheetUi(currentFile)
        }
    }

    private fun enableAvailableOffline(isEnabled: Boolean) {
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

            Log.d("FileInfoActionsView", "observeOfflineProgression> $progress% file:$fileId state:${workInfo.state}")

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

    fun refreshBottomSheetUi(file: File, isOfflineProgress: Boolean = false) {
        val isPendingOffline = file.isPendingOffline(context)
        val isOfflineFile = file.isOfflineFile(context)
        enableAvailableOffline(!isPendingOffline || file.currentProgress == 100)
        if (isOfflineProgress) setupFileProgress(file) else fileView.setFileItem(file)
        if (availableOfflineSwitch.isEnabled && availableOffline.isVisible) {
            availableOfflineSwitch.isChecked = isOfflineFile
        }
        addFavorites.isEnabled = true
        addFavoritesIcon.isEnabled = file.isFavorite
        addFavoritesText.setText(if (file.isFavorite) R.string.buttonRemoveFavorites else R.string.buttonAddFavorites)
        copyPublicLinkText.setText(if (file.shareLink == null) R.string.buttonCreatePublicLink else R.string.buttonCopyPublicLink)

        when {
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
                availableOfflineProgress.isGone = true
                availableOfflineComplete.isVisible = isOfflineFile
                availableOfflineIcon.isGone = isOfflineFile
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
        private val application: Context? get() = context.applicationContext

        private fun trackActionEvent(name: String, value: Float? = null) = application?.trackFileActionEvent(name, value)

        fun addFavoritesClicked() = trackActionEvent("favorite", (!currentFile.isFavorite).toFloat())
        fun colorFolderClicked(color: String) = application?.trackEvent("colorFolder", TrackerAction.CLICK, "switch")
        fun copyPublicLink() = trackActionEvent("copyShareLink")
        fun displayInfoClicked()
        fun downloadFileClicked() = trackActionEvent("download")
        fun dropBoxClicked(isDropBox: Boolean) = trackActionEvent("convertToDropbox", isDropBox.toFloat())
        fun fileRightsClicked()
        fun goToFolder()
        fun manageCategoriesClicked(fileId: Int)
        fun onCacheAddedToOffline() = Unit
        fun onDeleteFile(onApiResponse: () -> Unit)
        fun onDuplicateFile(result: String, onApiResponse: () -> Unit)
        fun onLeaveShare(onApiResponse: () -> Unit)
        fun onMoveFile(destinationFolder: File)
        fun onRenameFile(newName: String, onApiResponse: () -> Unit)
        fun openWithClicked() = trackActionEvent("openWith")
        fun removeOfflineFile(offlineLocalPath: java.io.File, cacheFile: java.io.File)

        fun editDocumentClicked() {
            trackActionEvent("edit")
            ownerFragment.openOnlyOfficeDocument(currentFile)
        }

        fun onSelectFolderResult(data: Intent?) {
            data?.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)?.let {
                val folderName = data.extras?.getString(SelectFolderActivity.FOLDER_NAME_TAG).toString()
                onMoveFile(File(id = it, name = folderName, driveId = AccountUtils.currentDriveId))
            }
        }

        fun availableOfflineSwitched(fileInfoActionsView: FileInfoActionsView, isChecked: Boolean): Boolean {
            currentFile.apply {
                when {
                    isOffline && isChecked -> Unit
                    !isOffline && !isChecked -> Unit
                    isChecked -> {
                        trackActionEvent("offline", true.toFloat())
                        return fileInfoActionsView.downloadAsOfflineFile()
                    }
                    else -> {
                        trackActionEvent("offline", false.toFloat())
                        val offlineLocalPath = getOfflineFile(context)
                        val cacheFile = getCacheFile(context)
                        offlineLocalPath?.let { removeOfflineFile(offlineLocalPath, cacheFile) }
                    }
                }
            }

            return true
        }

        fun moveFileClicked(folderId: Int?, selectFolderResultLauncher: ActivityResultLauncher<Intent>) {
            trackActionEvent("move")
            context.moveFileClicked(folderId, selectFolderResultLauncher)
        }

        fun duplicateFileClicked() {
            currentFile.apply {
                val fileName = getFileName()
                val copyName = context.getString(R.string.allDuplicateFileName, fileName, getFileExtension() ?: "")

                Utils.createPromptNameDialog(
                    context = context,
                    title = R.string.buttonDuplicate,
                    fieldName = R.string.fileInfoInputDuplicateFile,
                    positiveButton = R.string.buttonCopy,
                    fieldValue = copyName,
                    selectedRange = fileName.length
                ) { dialog, name ->
                    onDuplicateFile(name) {
                        trackActionEvent("copy")
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
                        trackActionEvent("stopShare")
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
                        trackActionEvent("rename")
                        dialog.dismiss()
                    }
                }
            }
        }

        fun deleteFileClicked() {
            Utils.confirmFileDeletion(context, fileName = currentFile.name) { dialog ->
                onDeleteFile {
                    trackActionEvent("putInTrash")
                    dialog.dismiss()
                }
            }
        }
    }

    private companion object {
        fun Context.trackFileActionEvent(trackerName: String, trackerValue: Float? = null) {
            trackEvent("fileAction", TrackerAction.CLICK, trackerName, trackerValue)
        }
    }
}
