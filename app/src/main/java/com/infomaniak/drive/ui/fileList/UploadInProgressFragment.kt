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
import androidx.core.net.toFile
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.Data
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileInProgress
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.sync.UploadAdapter
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.showSnackbar
import io.sentry.Sentry
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UploadInProgressFragment : FileListFragment() {

    private lateinit var drivePermissions: DrivePermissions
    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override var showPendingFiles = false

    private var pendingFiles = arrayListOf<UploadFile>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles()
        drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { autorized ->
            if (!autorized) {
                findNavController().popBackStack()
            }
        }
        super.onViewCreated(view, savedInstanceState)
        fileAdapter.onFileClicked = null
        fileAdapter.uploadInProgress = true
        toolbar.menu.findItem(R.id.restartItem).isVisible = true
        toolbar.menu.findItem(R.id.closeItem).isVisible = true

        collapsingToolbarLayout.title = getString(R.string.uploadInProgressTitle)

        mainViewModel.fileInProgress.observe(viewLifecycleOwner) { fileInProgress ->
            val firstFile = fileAdapter.getItems().firstOrNull()
            if (fileInProgress != null && firstFile != null) {
                if (fileInProgress.parentId == folderID && fileInProgress.name == firstFile.name) {
                    fileAdapter.updateFileProgress(firstFile.id, fileInProgress.progress) {
                        whenAnUploadIsDone(fileInProgress)
                    }
                }
                Log.i("uploadInProgress", "${fileInProgress.name} ${fileInProgress.status} ${fileInProgress.progress}%")
            }
        }

        mainViewModel.refreshActivities.removeObservers(super.getViewLifecycleOwner())
        mainViewModel.refreshActivities.observe(viewLifecycleOwner) {
            fileListViewModel.getPendingFilesCount(folderID).observe(viewLifecycleOwner) { count ->
                if (count != fileAdapter.itemCount) downloadFiles(true)
            }
        }
        fileAdapter.onStopUploadButtonClicked = { fileName ->
            pendingFiles.find { it.fileName == fileName }?.let { syncFile ->
                val title = getString(R.string.uploadInProgressCancelFileUploadTitle, syncFile.fileName)
                Utils.createConfirmation(requireContext(), title) {
                    val position = fileAdapter.getItems().indexOfFirst { it.name == fileName }
                    if (fileAdapter.contains(syncFile.fileName)) {
                        closeItemClicked(arrayListOf(syncFile))
                        fileRecyclerView.post { fileAdapter.deleteAt(position) }
                    }
                }
            }
        }

        sortLayout.visibility = View.GONE
        noFilesLayout.setup(
            icon = R.drawable.ic_upload,
            title = R.string.uploadInProgressNoFile,
            initialListView = fileRecyclerView
        )
        downloadFiles(true)
    }


    private fun whenAnUploadIsDone(fileInProgress: FileInProgress) {
        if (fileInProgress.status == UploadAdapter.ProgressStatus.FINISHED) {
            fileAdapter.deleteAt(0)

            if (fileAdapter.getItems().isEmpty()) {
                noFilesLayout.toggleVisibility(true)
                requireActivity().showSnackbar(R.string.allUploadFinishedTitle)
                popBackStack()
            }
        }
    }

    override fun onRestartItemsClicked() {
        val title = getString(R.string.uploadInProgressRestartUploadTitle)
        val context = requireContext()
        Utils.createConfirmation(context, title) {
            if (fileAdapter.importContainsProgress) {
                context.syncImmediately()
            }
        }
    }

    override fun onCloseItemsClicked() {
        val title = getString(R.string.uploadInProgressCancelAllUploadTitle)
        Utils.createConfirmation(requireContext(), title) {
            closeItemClicked(pendingFiles)
            fileAdapter.setList(arrayListOf())
        }
    }

    private fun closeItemClicked(pendingFiles: ArrayList<UploadFile>) {
        lifecycleScope.launch(Dispatchers.IO) {
            fileListViewModel.cancelUploadingFiles(pendingFiles)
            withContext(Dispatchers.Main) {
                lifecycleScope.launchWhenResumed {
                    val data = Data.Builder().putBoolean(UploadWorker.CANCELLED_BY_USER, true).build()
                    requireContext().syncImmediately(data, true)
                    popBackStack()
                }
            }
        }
    }

    private fun popBackStack() {
        mainViewModel.refreshActivities.value = true
        findNavController().popBackStack()
    }

    private inner class DownloadFiles : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            if (!drivePermissions.checkWriteStoragePermission()) return
            if (ignoreCache) fileAdapter.setList(arrayListOf())

            timer.start()
            fileAdapter.isComplete = false
            fileListViewModel.getPendingFiles(folderID).observe(viewLifecycleOwner) { syncFiles ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val files = arrayListOf<File>()
                    syncFiles.forEach { uploadFile ->
                        val uri = uploadFile.getUriObject()

                        if (uri.scheme.equals(ContentResolver.SCHEME_CONTENT)) {
                            context?.apply {
                                try {
                                    SyncUtils.checkDocumentProviderPermissions(this, uri)
                                    contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
                                        if (cursor.moveToFirst()) {
                                            val size = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                                            files.add(
                                                File(
                                                    id = 0,
                                                    name = uploadFile.fileName,
                                                    size = size,
                                                    path = uploadFile.uri,
                                                    isFromUploads = true
                                                )
                                            )
                                        }
                                    }
                                } catch (exception: Exception) {
                                    exception.printStackTrace()
                                    Sentry.withScope { scope ->
                                        scope.setExtra("data", uploadFile.uri)
                                        scope.setExtra("stack trace", exception.stackTraceToString())
                                        exception.message?.let { Sentry.captureMessage(it) }
                                    }
                                    return@forEach
                                }
                            }
                        } else {
                            files.add(
                                File(
                                    id = 0,
                                    name = uploadFile.fileName,
                                    size = uri.toFile().length(),
                                    path = uploadFile.uri,
                                    isFromUploads = true
                                )
                            )
                        }
                    }
                    pendingFiles = syncFiles
                    withContext(Dispatchers.Main) {
                        fileAdapter.setList(files)
                        fileAdapter.isComplete = true
                        timer.cancel()
                        swipeRefreshLayout.isRefreshing = false
                        noFilesLayout.toggleVisibility(pendingFiles.isEmpty())
                    }
                }
            }
        }
    }
}
