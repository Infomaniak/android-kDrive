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

import android.content.ContentResolver
import android.os.Bundle
import android.provider.OpenableColumns
import android.util.Log
import android.view.View
import androidx.activity.addCallback
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toFile
import androidx.core.view.isGone
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.Data
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerProgress
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import io.realm.OrderedRealmCollectionChangeListener
import io.realm.Realm
import io.realm.RealmResults
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.android.synthetic.main.dialog_download_progress.view.*
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.fragment_file_list.toolbar
import kotlinx.android.synthetic.main.fragment_new_folder.*
import kotlinx.android.synthetic.main.item_file_name.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext


class UploadInProgressFragment : FileListFragment() {

    private lateinit var realmUpload: Realm
    private lateinit var drivePermissions: DrivePermissions
    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override var showPendingFiles = false

    private var pendingFiles = arrayListOf<UploadFile>()
    private var isCancelled = false
    private var progressDialog: AlertDialog? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        realmUpload = UploadFile.getRealmInstance()
        downloadFiles = DownloadFiles()
        setNoFilesLayout = SetNoFilesLayout()
        drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { authorized ->
            if (!authorized) {
                findNavController().popBackStack()
            }
        }
        super.onViewCreated(view, savedInstanceState)

        val fromPendingFolders = findNavController().previousBackStackEntry?.destination?.id == R.id.uploadInProgressFragment
        collapsingToolbarLayout.title =
            if (folderID > 0 && fromPendingFolders) folderName else getString(R.string.uploadInProgressTitle)

        requireContext().trackUploadWorkerProgress().observe(viewLifecycleOwner) {
            val workInfo = it.firstOrNull() ?: return@observe
            val fileName = workInfo.progress.getString(UploadWorker.FILENAME) ?: return@observe
            val progress = workInfo.progress.getInt(UploadWorker.PROGRESS, 0)
            val isUploaded = workInfo.progress.getBoolean(UploadWorker.IS_UPLOADED, false)
            val remoteFolderId = workInfo.progress.getInt(UploadWorker.REMOTE_FOLDER_ID, 0)
            val position = fileAdapter.indexOf(fileName)

            if (folderID == remoteFolderId && position >= 0) {
                if (!isUploaded) fileAdapter.updateFileProgress(position = position, progress = progress)
            }

            Log.d("uploadInProgress", "$fileName $progress%")
        }

        mainViewModel.refreshActivities.removeObservers(super.getViewLifecycleOwner())

        fileAdapter.onStopUploadButtonClicked = { fileName ->
            pendingFiles.find { it.fileName == fileName }?.let { syncFile ->
                val title = getString(R.string.uploadInProgressCancelFileUploadTitle, syncFile.fileName)
                Utils.createConfirmation(requireContext(), title) {
                    if (fileAdapter.contains(syncFile.fileName)) {
                        closeItemClicked(uploadFiles = arrayListOf(syncFile))
                    }
                }
            }
        }

        if (isPendingFolders()) {
            fileAdapter.onFileClicked = { navigateToUploadView(it.id, it.name) }
        } else {
            toolbar.setNavigationOnClickListener { popBackStack() }
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { popBackStack() }
        }

        sortLayout.isGone = true
    }

    override fun setupFileAdapter() {
        super.setupFileAdapter()
        fileAdapter.onFileClicked = null
        fileAdapter.uploadInProgress = true
        fileAdapter.checkIsPendingWifi(requireContext())
    }

    override fun onDestroy() {
        realmUpload.close()
        super.onDestroy()
    }

    private fun createListener(): OrderedRealmCollectionChangeListener<RealmResults<UploadFile>> {
        return OrderedRealmCollectionChangeListener<RealmResults<UploadFile>> { _, changeSet ->
            // For deletions, notify the UI in reverse order if removing elements the UI
            val deletions = changeSet.deletionRanges
            for (i in deletions.indices.reversed()) {
                val range = deletions[i]
                if (range.length.isPositive()) {
                    for (fileIndex in (range.length - 1) downTo range.startIndex) {
                        fileAdapter.deleteAt(fileIndex)
                    }
                    fileAdapter.notifyItemRangeRemoved(range.startIndex, range.length)
                }

                if (fileAdapter.fileList.isEmpty()) {
                    if (isCancelled) cancelledByUser() else whenAnUploadIsDone()
                }
            }
            progressDialog?.dismiss()
            isCancelled = false
        }
    }

    private fun cancelledByUser() {
        progressDialog?.dismiss()
        val data = Data.Builder().putBoolean(UploadWorker.CANCELLED_BY_USER, true).build()
        requireContext().syncImmediately(data, true)
        popBackStack()
    }

    private fun whenAnUploadIsDone() {
        if (isResumed) noFilesLayout?.toggleVisibility(true)
        activity?.showSnackbar(R.string.allUploadFinishedTitle)
        popBackStack()
    }

    override fun onRestartItemsClicked() {
        val title = getString(R.string.uploadInProgressRestartUploadTitle)
        val context = requireContext()
        Utils.createConfirmation(context, title) {
            if (fileAdapter.getFiles().isNotEmpty()) {
                context.syncImmediately()
            }
        }
    }

    override fun onCloseItemsClicked() {
        val title = getString(R.string.uploadInProgressCancelAllUploadTitle)
        Utils.createConfirmation(requireContext(), title) {
            closeItemClicked(folderId = folderID)
        }
    }

    private fun closeItemClicked(uploadFiles: ArrayList<UploadFile>? = null, folderId: Int? = null) {
        val progressDialog = Utils.createProgressDialog(requireContext(), R.string.allCancellationInProgress)
        isCancelled = true
        lifecycleScope.launch(Dispatchers.IO) {
            var needPopBackStack = false
            uploadFiles?.let {
                UploadFile.deleteAll(it)
                needPopBackStack = true
            }
            folderId?.let {
                UploadFile.deleteAll(it)
                needPopBackStack = UploadFile.getCurrentUserPendingUploadsCount(it) == 0
            }

            withContext(Dispatchers.Main) {
                if (isResumed && needPopBackStack) {
                    progressDialog.dismiss()
                    val data = Data.Builder().putBoolean(UploadWorker.CANCELLED_BY_USER, true).build()
                    requireContext().syncImmediately(data, true)
                    popBackStack()
                }
            }
        }
    }


    private fun isPendingFolders() = folderID == Utils.OTHER_ROOT_ID

    private fun popBackStack() {
        mainViewModel.refreshActivities.value = true

        fun notIgnorePendingFoldersIfNeeded(): Boolean {
            val isFromPendingFolders =
                findNavController().previousBackStackEntry?.destination?.id == R.id.uploadInProgressFragment

            return if (UploadFile.getAllPendingFoldersCount(realmUpload) in 0..1 && isFromPendingFolders) {
                val lastIndex = findNavController().backQueue.lastIndex
                val previousDestinationId = findNavController().backQueue[lastIndex - 2].destination.id
                findNavController().popBackStack(previousDestinationId, false)
                false
            } else true
        }

        if (notIgnorePendingFoldersIfNeeded()) findNavController().popBackStack()
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_upload,
                title = R.string.uploadInProgressNoFile,
                initialListView = fileRecyclerView
            )
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            if (!drivePermissions.checkWriteStoragePermission()) return
            if (ignoreCache) fileAdapter.setFiles(arrayListOf())

            showLoadingTimer.start()
            fileAdapter.isComplete = false

            if (isPendingFolders()) {
                downloadPendingFolders()
            } else {
                downloadPendingFilesByFolderId()
            }
        }

        private fun downloadPendingFolders() {
            UploadFile.getAllPendingFolders(realmUpload)?.let { pendingFolders ->
                if (pendingFolders.count() == 1) {
                    val uploadFile = pendingFolders.first()!!
                    val drive = DriveInfosController.getDrives(AccountUtils.currentUserId, uploadFile.driveId, null).first()
                    val userDrive = UserDrive(driveId = uploadFile.driveId, sharedWithMe = drive.sharedWithMe)
                    val folder = FileController.getFileById(uploadFile.remoteFolder, userDrive)!!
                    navigateToUploadView(uploadFile.remoteFolder, folder.name)
                } else {
                    val files = arrayListOf<File>()
                    pendingFolders.forEach { uploadFile ->
                        files.add(createFolderFile(uploadFile.remoteFolder, uploadFile.driveId))
                    }

                    fileAdapter.isComplete = true
                    fileAdapter.setFiles(files)
                    noFilesLayout.toggleVisibility(pendingFolders.isEmpty())
                    showLoadingTimer.cancel()
                    swipeRefreshLayout.isRefreshing = false
                    toolbar.menu.findItem(R.id.closeItem).isVisible = true
                }
            } ?: noFilesLayout.toggleVisibility(true)
        }

        private fun createFolderFile(fileId: Int, driveId: Int): File {
            val drive = DriveInfosController.getDrives(AccountUtils.currentUserId, driveId, null).first()
            val driveName = if (drive.sharedWithMe) drive.name else null
            val userDrive = UserDrive(driveId = driveId, sharedWithMe = drive.sharedWithMe, driveName = driveName)
            val folder = FileController.getFileById(fileId, userDrive)!!
            val name: String
            val type: String

            if (fileId == Utils.ROOT_ID) {
                name = Utils.getRootName(requireContext())
                type = File.Type.DRIVE.value
            } else {
                name = folder.name
                type = File.Type.FOLDER.value
            }

            return File(
                id = fileId,
                isFromUploads = true,
                name = name,
                path = folder.getRemotePath(userDrive),
                type = type
            )
        }

        private fun downloadPendingFilesByFolderId() {
            UploadFile.getCurrentUserPendingUploads(realmUpload, folderID)?.let { currentUserPendingUploads ->
                val files = arrayListOf<File>()
                currentUserPendingUploads.forEach { uploadFile ->
                    val uri = uploadFile.getUriObject()

                    if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                        context?.apply {
                            try {
                                SyncUtils.checkDocumentProviderPermissions(this, uri)
                                contentResolver?.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
                                    if (cursor.moveToFirst()) {
                                        val size = SyncUtils.getFileSize(cursor)
                                        files.add(
                                            File(
                                                id = uploadFile.uri.hashCode(),
                                                isFromUploads = true,
                                                name = uploadFile.fileName,
                                                path = uploadFile.uri,
                                                size = size,
                                            )
                                        )
                                    }
                                }
                            } catch (exception: Exception) {
                                exception.printStackTrace()
                                files.add(
                                    File(
                                        id = uploadFile.uri.hashCode(),
                                        isFromUploads = true,
                                        name = uploadFile.fileName,
                                        path = uploadFile.uri,
                                    )
                                )

                                Sentry.withScope { scope ->
                                    scope.level = SentryLevel.WARNING
                                    scope.setExtra("fileName", uploadFile.fileName)
                                    scope.setExtra("uri", uploadFile.uri)
                                    Sentry.captureException(exception)
                                }
                            }
                        }
                    } else {
                        files.add(
                            File(
                                id = uploadFile.uri.hashCode(),
                                isFromUploads = true,
                                name = uploadFile.fileName,
                                path = uploadFile.uri,
                                size = uri.toFile().length(),
                            )
                        )
                    }
                }

                pendingFiles = ArrayList(realmUpload.copyFromRealm(currentUserPendingUploads, 0))

                toolbar.menu.findItem(R.id.restartItem).isVisible = true
                toolbar.menu.findItem(R.id.closeItem).isVisible = true
                fileAdapter.setFiles(files)
                fileAdapter.isComplete = true
                showLoadingTimer.cancel()
                swipeRefreshLayout.isRefreshing = false
                noFilesLayout.toggleVisibility(currentUserPendingUploads.isEmpty())
            } ?: noFilesLayout.toggleVisibility(true)
        }
    }
}
