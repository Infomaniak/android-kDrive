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
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.work.*
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.cache.FileController.startDownloadFile
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.CancellableAction
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.VisibilityType.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.utils.*
import kotlinx.android.synthetic.main.fragment_file_details.view.*
import kotlinx.android.synthetic.main.fragment_preview_slider.*
import kotlinx.android.synthetic.main.fragment_preview_slider.view.*
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.android.synthetic.main.item_file_name.view.*
import kotlinx.android.synthetic.main.view_file_info_actions.view.*
import kotlinx.android.synthetic.main.view_share_link_container.view.*
import kotlinx.android.synthetic.main.view_url_display.view.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class FileInfoActionsView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private lateinit var currentFile: File
    private lateinit var mainViewModel: MainViewModel
    private var workManager: WorkManager = WorkManager.getInstance(context)

    private lateinit var ownerFragment: Fragment
    private lateinit var onItemClickListener: OnItemClickListener
    private var isSharedWithMe = false

    init {
        inflate(context, R.layout.view_file_info_actions, this)
        setBottomSheetFileInfos()
    }

    fun init(ownerFragment: Fragment, onItemClickListener: OnItemClickListener, isSharedWithMe: Boolean = false) {
        this.ownerFragment = ownerFragment
        this.onItemClickListener = onItemClickListener
        this.isSharedWithMe = isSharedWithMe
        mainViewModel = ViewModelProvider(ownerFragment.requireActivity())[MainViewModel::class.java]

        editDocument.setOnClickListener { onItemClickListener.editDocumentClicked(ownerFragment, currentFile) }
        displayInfo.setOnClickListener { onItemClickListener.displayInfoClicked() }
        fileRights.setOnClickListener { onItemClickListener.fileRightsClicked() }
        sendCopy.setOnClickListener { if (currentFile.isFolder()) openAddFileBottom() else shareFile() }
        copyPublicLink.setOnClickListener { onItemClickListener.copyPublicLink() }
        openWith.setOnClickListener { onItemClickListener.openWithClicked() }
        downloadFile.setOnClickListener { onItemClickListener.downloadFileClicked() }
        addFavorites.setOnClickListener {
            addFavorites.isEnabled = false
            onItemClickListener.addFavoritesClicked()
        }
        leaveShare.setOnClickListener { onItemClickListener.leaveShare(context, currentFile) }

        availableOfflineSwitch.setOnCheckedChangeListener { _, isChecked ->
            onItemClickListener.availableOfflineSwitched(this, currentFile, isChecked)
        }

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
                currentFile,
                this
            )
        }
        deleteFile.setOnClickListener { onItemClickListener.deleteFileClicked(ownerFragment.requireContext(), currentFile) }
    }

    fun updateCurrentFile(file: File) {
        currentFile = file

        refreshBottomSheetUi(currentFile)
        if (currentFile.isFromActivities) {
            quickActionsLayout.visibility = GONE
            actionListLayout.visibility = GONE
        } else {
            quickActionsLayout.visibility = VISIBLE
            quickActionsLayout.visibility = VISIBLE
            availableOfflineSwitch.isEnabled = true

            val isOnline = mainViewModel.isInternetAvailable.value == true
            val isCommonDocumentOrSharedSpace =
                currentFile.getVisibilityType() == IS_TEAM_SPACE || currentFile.getVisibilityType() == IS_SHARED_SPACE

            // TODO - Enhanceable code : Replace these let by an autonomous view with "enabled/disabled" method ?
            currentFile.rights.let { rights ->
                displayInfo.isEnabled = isOnline
                disabledInfo.visibility = if (isOnline) GONE else VISIBLE

                (rights?.share == true && isOnline).let { rightsEnabled ->
                    fileRights.isEnabled = rightsEnabled
                    disabledFileRights.visibility = if (rightsEnabled) GONE else VISIBLE
                }

                (rights?.canBecomeLink == true && isOnline || currentFile.shareLink != null || !file.collaborativeFolder.isNullOrBlank()).let { publicLinkEnabled ->
                    copyPublicLink.isEnabled = publicLinkEnabled
                    disabledPublicLink.visibility = if (publicLinkEnabled) GONE else VISIBLE
                    if (!file.collaborativeFolder.isNullOrBlank()) {
                        copyPublicLinkText.text = context.getString(R.string.buttonCopyLink)
                    }
                }

                ((file.isFolder() && rights?.newFile == true && rights.newFolder) || !file.isFolder()).let { sendCopyEnabled ->
                    sendCopy.isEnabled = sendCopyEnabled
                    disabledSendCopy.visibility = if (sendCopyEnabled) GONE else VISIBLE
                }

                addFavorites.visibility = if (rights?.canFavorite == true) VISIBLE else GONE
                editDocument.visibility =
                    if ((currentFile.onlyoffice && rights?.write == true) || currentFile.onlyofficeConvertExtension != null) VISIBLE else GONE
                moveFile.visibility = if (rights?.move == true && !isSharedWithMe) VISIBLE else GONE
                renameFile.visibility = if (rights?.rename == true && !isSharedWithMe) VISIBLE else GONE
                deleteFile.visibility = if (rights?.delete == true) VISIBLE else GONE
                leaveShare.visibility = if (rights?.leave == true) VISIBLE else GONE
                duplicateFile.visibility = if (isSharedWithMe || isCommonDocumentOrSharedSpace) GONE else VISIBLE
            }

            dropBox.visibility = when {
                currentFile.isDropBox() || currentFile.rights?.canBecomeCollab == true -> {
                    dropBoxText.text =
                        if (currentFile.isDropBox()) context.getString(R.string.buttonManageDropBox)
                        else context.getString(R.string.buttonConvertToDropBox)
                    dropBox.setOnClickListener { onItemClickListener.dropBoxClicked(isDropBox = currentFile.isDropBox()) }
                    VISIBLE
                }
                else -> GONE
            }

            if (currentFile.isFolder()) {
                sendCopyIcon.setImageResource(R.drawable.ic_add)
                sendCopyText.setText(R.string.buttonAdd)
                availableOffline.visibility = GONE
                openWith.visibility = GONE
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

    private fun setBottomSheetFileInfos() {
        availableOffline.setOnClickListener {
            availableOfflineSwitch.performClick()
        }
    }

    fun downloadAsOfflineFile() {
        val localFile = currentFile.localPath(context, File.LocalType.CLOUD_STORAGE)
        if (localFile.exists()) {
            val offlineFile = currentFile.localPath(context, File.LocalType.OFFLINE)
            Utils.moveCacheFileToOffline(currentFile, localFile, offlineFile)
            runBlocking(Dispatchers.IO) { FileController.updateOfflineStatus(currentFile.id, true) }
            currentFile.isOffline = true
            onItemClickListener.onCacheAddedToOffline()
            refreshBottomSheetUi(currentFile)
        } else Utils.downloadAsOfflineFile(context, currentFile)
    }

    fun downloadFile(owner: Fragment, onSuccess: () -> Unit) {
        if (owner.checkWriteStoragePermission()) {
            val downloadURL = Uri.parse(ApiRoutes.downloadFile(currentFile))
            context.startDownloadFile(downloadURL, "${currentFile.name}.zip")
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
        GlobalScope.launch(Dispatchers.IO) {
            FileController.updateFile(currentFile.id) { it.shareLink = url }
        }
        copyPublicLink(url)
        currentFile.shareLink = url
        refreshBottomSheetUi(currentFile)
    }

    private fun showCopyPublicLinkLoader(show: Boolean) {
        copyPublicLinkLayout.visibility = if (show) GONE else VISIBLE
        copyPublicLinkLoader.visibility = if (show) VISIBLE else GONE
    }

    private fun copyPublicLink(url: String) {
        showCopyPublicLinkLoader(false)
        Utils.copyToClipboard(context, url)
    }

    fun observeOfflineProgression(lifecycleOwner: LifecycleOwner, updateFile: (fileId: Int) -> Unit) {
        workManager.getWorkInfosForUniqueWorkLiveData(DownloadWorker.TAG).observe(lifecycleOwner) { workInfoList ->
            if (workInfoList.isEmpty()) return@observe
            val workInfo = workInfoList.firstOrNull {
                it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED
            } ?: workInfoList.first()

            if (!workInfo.state.isFinished) {
                val progress = workInfo.progress.getInt(DownloadWorker.PROGRESS, 0)
                val fileId = workInfo.progress.getInt(DownloadWorker.FILE_ID, 0)

                if (currentFile.id == fileId) {
                    if (progress == 100) {
                        availableOfflineSwitch.isEnabled = true
                        updateFile(fileId)
                        currentFile.isOffline = true
                        refreshBottomSheetUi(currentFile)
                    } else {
                        availableOfflineSwitch.isEnabled = false
                        refreshBottomSheetUi(currentFile, progress)
                    }
                } else {
                    if (progress == 100) updateFile(fileId)
                    availableOfflineSwitch.isEnabled = true
                }
            }
        }
    }

    fun refreshBottomSheetUi(file: File, offlineProgress: Int? = null) {
        apply {
            fileView.setFileItem(file, showProgress = false)
            if (availableOfflineSwitch.isEnabled) availableOfflineSwitch.isChecked = file.isOffline
            addFavoritesIcon.isEnabled = file.isFavorite
            addFavoritesText.setText(if (file.isFavorite) R.string.buttonRemoveFavorites else R.string.buttonAddFavorites)
            copyPublicLinkText.setText(if (file.shareLink == null) R.string.buttonCreatePublicLink else R.string.buttonCopyPublicLink)

            if (offlineProgress == null) {
                availableOfflineProgress.visibility = GONE
                availableOfflineComplete.visibility = if (file.isOffline) VISIBLE else GONE
                availableOfflineIcon.visibility = if (file.isOffline) GONE else VISIBLE
            } else {
                availableOfflineComplete.visibility = GONE
                availableOfflineIcon.visibility = GONE
                availableOfflineProgress.visibility = VISIBLE
                availableOfflineProgress.progress = offlineProgress
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
                    currentFile.name = newName
                    refreshBottomSheetUi(currentFile)
                    onSuccess?.invoke(action)
                }
            } else {
                onError?.invoke(context.getString(R.string.errorRename))
            }
        }
    }

    interface OnItemClickListener {
        fun copyPublicLink()
        fun openWithClicked()
        fun fileRightsClicked()
        fun displayInfoClicked()
        fun downloadFileClicked()
        fun addFavoritesClicked()
        fun onCacheAddedToOffline() = Unit
        fun onMoveFile(destinationFolder: File)
        fun onDeleteFile(onApiResponse: () -> Unit)
        fun onLeaveShare(onApiResponse: () -> Unit)
        fun dropBoxClicked(isDropBox: Boolean) = Unit
        fun onRenameFile(newName: String, onApiResponse: () -> Unit)
        fun onDuplicateFile(result: String, onApiResponse: () -> Unit)
        fun removeOfflineFile(offlineLocalPath: java.io.File, cacheFile: java.io.File)

        fun editDocumentClicked(ownerFragment: Fragment, currentFile: File) {
            ownerFragment.apply {
                requireContext().openOnlyOfficeDocument(findNavController(), currentFile)
            }
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
            val offlineLocalPath = currentFile.localPath(fileInfoActionsView.context, File.LocalType.OFFLINE)
            val cacheFile = currentFile.localPath(fileInfoActionsView.context, File.LocalType.CLOUD_STORAGE)
            when {
                currentFile.isOffline && isChecked -> Unit
                isChecked -> fileInfoActionsView.downloadAsOfflineFile()
                else -> removeOfflineFile(offlineLocalPath, cacheFile)
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
                    autoDismiss = true
                ) { dialog ->
                    onLeaveShare {
                        dialog.dismiss()
                    }
                }
            }
        }

        fun renameFileClicked(context: Context, currentFile: File, fileInfoActionsView: FileInfoActionsView) {
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
