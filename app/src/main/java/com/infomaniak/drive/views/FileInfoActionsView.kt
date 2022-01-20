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
package com.infomaniak.drive.views

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.AttributeSet
import android.util.Log
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.work.*
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.*
import com.infomaniak.drive.data.models.File.VisibilityType.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.utils.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_file_info_actions.*
import kotlinx.android.synthetic.main.fragment_file_details.view.*
import kotlinx.android.synthetic.main.fragment_menu.view.*
import kotlinx.android.synthetic.main.fragment_preview_slider.*
import kotlinx.android.synthetic.main.fragment_preview_slider.view.*
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.android.synthetic.main.view_file_info_actions.view.*
import kotlinx.android.synthetic.main.view_share_link_container.view.*
import kotlinx.android.synthetic.main.view_url_display.view.*
import kotlinx.coroutines.*

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
    private var isSharedWithMe = false

    init {
        inflate(context, R.layout.view_file_info_actions, this)
    }

    fun init(
        ownerFragment: Fragment,
        mainViewModel: MainViewModel,
        onItemClickListener: OnItemClickListener,
        isSharedWithMe: Boolean = false
    ) {
        this.isSharedWithMe = isSharedWithMe
        this.mainViewModel = mainViewModel
        this.onItemClickListener = onItemClickListener
        this.ownerFragment = ownerFragment
        initOnClickListeners()
    }

    fun updateCurrentFile(file: File) {
        currentFile = file
        refreshBottomSheetUi(currentFile)
        manageCategories.isVisible = DriveInfosController.getCategoryRights()?.canPutCategoryOnFile ?: false

        if (currentFile.isFromActivities) {
            quickActionsLayout.isGone = true
            actionListLayout.isGone = true
        } else {
            quickActionsLayout.isVisible = true
            quickActionsLayout.isVisible = true

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
            }
        }
    }

    fun scrollToTop() {
        scrollView.fullScroll(View.FOCUS_UP)
    }

    private fun shareFile() {
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
        editDocument.setOnClickListener { onItemClickListener.editDocumentClicked(ownerFragment, currentFile) }
        displayInfo.setOnClickListener { onItemClickListener.displayInfoClicked() }
        fileRights.setOnClickListener { onItemClickListener.fileRightsClicked() }
        sendCopy.setOnClickListener { if (currentFile.isFolder()) openAddFileBottom() else shareFile() }
        copyPublicLink.setOnClickListener { onItemClickListener.copyPublicLink() }
        openWith.setOnClickListener { onItemClickListener.openWithClicked() }
        downloadFile.setOnClickListener { onItemClickListener.downloadFileClicked() }
        manageCategories.setOnClickListener { onItemClickListener.manageCategoriesClicked(currentFile.id) }
        addFavorites.setOnClickListener {
            addFavorites.isEnabled = false
            onItemClickListener.addFavoritesClicked()
        }
        leaveShare.setOnClickListener { onItemClickListener.leaveShare(context, currentFile) }
        availableOfflineSwitch.setOnCheckedChangeListener { _, isChecked ->
            onItemClickListener.availableOfflineSwitched(this, currentFile, isChecked)
        }
        availableOffline.setOnClickListener { availableOfflineSwitch.performClick() }
        moveFile.setOnClickListener {
            val currentFolder = FileController.getParentFile(currentFile.id)?.id ?: -42
            onItemClickListener.moveFileClicked(ownerFragment, currentFolder)
        }
        duplicateFile.setOnClickListener {
            onItemClickListener.duplicateFileClicked(
                ownerFragment.requireContext(),
                currentFile
            )
        }
        renameFile.setOnClickListener {
            onItemClickListener.renameFileClicked(
                ownerFragment.requireContext(),
                currentFile
            )
        }
        deleteFile.setOnClickListener { onItemClickListener.deleteFileClicked(ownerFragment.requireContext(), currentFile) }
    }

    fun downloadAsOfflineFile() {
        val cacheFile = currentFile.getCacheFile(context)
        if (cacheFile.exists()) {
            currentFile.getOfflineFile(context)?.let { offlineFile ->
                Utils.moveCacheFileToOffline(currentFile, cacheFile, offlineFile)
                CoroutineScope(Dispatchers.IO).launch { FileController.updateOfflineStatus(currentFile.id, true) }
                currentFile.isOffline = true
                onItemClickListener.onCacheAddedToOffline()
            }
        } else {
            Utils.downloadAsOfflineFile(context, currentFile)
            if (currentFile.isPendingOffline(context)) mainViewModel.updateOfflineFile.value = currentFile.id
        }
        refreshBottomSheetUi(currentFile)
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
                availableOfflineProgress.isGone = true // Before changing the type of progression it must first be gone
                if (isOfflineProgress) {
                    availableOfflineProgress.isIndeterminate = false
                    availableOfflineProgress.progress = file.currentProgress
                } else {
                    availableOfflineProgress.isIndeterminate = true
                }
                availableOfflineProgress.isVisible = true
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
        fun addFavoritesClicked()
        fun copyPublicLink()
        fun displayInfoClicked()
        fun downloadFileClicked()
        fun manageCategoriesClicked(fileId: Int)
        fun dropBoxClicked(isDropBox: Boolean) = Unit
        fun fileRightsClicked()
        fun onCacheAddedToOffline() = Unit
        fun onDeleteFile(onApiResponse: () -> Unit)
        fun onDuplicateFile(result: String, onApiResponse: () -> Unit)
        fun onLeaveShare(onApiResponse: () -> Unit)
        fun onMoveFile(destinationFolder: File)
        fun onRenameFile(newName: String, onApiResponse: () -> Unit)
        fun openWithClicked()
        fun removeOfflineFile(offlineLocalPath: java.io.File, cacheFile: java.io.File)

        fun editDocumentClicked(ownerFragment: Fragment, currentFile: File) {
            ownerFragment.openOnlyOfficeDocument(currentFile)
        }

        fun onSelectFolderResult(requestCode: Int, resultCode: Int, data: Intent?) {
            if (requestCode == SelectFolderActivity.SELECT_FOLDER_REQUEST && resultCode == AppCompatActivity.RESULT_OK) {
                val folderName = data?.extras?.getString(SelectFolderActivity.FOLDER_NAME_TAG).toString()
                data?.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)?.let {
                    onMoveFile(File(id = it, name = folderName, driveId = AccountUtils.currentDriveId))
                }
            }
        }

        fun availableOfflineSwitched(fileInfoActionsView: FileInfoActionsView, currentFile: File, isChecked: Boolean) {
            when {
                currentFile.isOffline && isChecked -> Unit
                !currentFile.isOffline && !isChecked -> Unit
                isChecked -> fileInfoActionsView.downloadAsOfflineFile()
                else -> {
                    val offlineLocalPath = currentFile.getOfflineFile(fileInfoActionsView.context)
                    val cacheFile = currentFile.getCacheFile(fileInfoActionsView.context)
                    offlineLocalPath?.let { removeOfflineFile(offlineLocalPath, cacheFile) }
                }
            }
        }

        fun moveFileClicked(ownerFragment: Fragment, idFolder: Int) = Utils.moveFileClicked(ownerFragment, idFolder)

        fun duplicateFileClicked(context: Context, currentFile: File) {
            currentFile.apply {
                val fileName = currentFile.getFileName()
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
                        dialog.dismiss()
                    }
                }
            }
        }

        fun leaveShare(context: Context, currentFile: File) {
            currentFile.apply {
                Utils.createConfirmation(
                    context = context,
                    title = context.getString(R.string.modalLeaveShareTitle),
                    message = context.getString(R.string.modalLeaveShareDescription, currentFile.name),
                    autoDismiss = false
                ) { dialog ->
                    onLeaveShare {
                        dialog.dismiss()
                    }
                }
            }
        }

        fun renameFileClicked(context: Context, currentFile: File) {
            currentFile.apply {
                Utils.createPromptNameDialog(
                    context = context,
                    title = R.string.buttonRename,
                    fieldName = if (isFolder()) R.string.hintInputDirName else R.string.hintInputFileName,
                    positiveButton = R.string.buttonSave,
                    fieldValue = name,
                    selectedRange = currentFile.getFileName().length
                ) { dialog, name ->
                    onRenameFile(name) {
                        dialog.dismiss()
                    }
                }
            }
        }

        fun deleteFileClicked(context: Context, currentFile: File) {
            Utils.confirmFileDeletion(context, fileName = currentFile.name) { dialog ->
                onDeleteFile {
                    dialog.dismiss()
                }
            }
        }
    }
}
