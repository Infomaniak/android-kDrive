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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import android.view.View
import androidx.activity.addCallback
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.work.Data
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerProgress
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerSucceeded
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.navigateToUploadView
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import io.realm.RealmResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.launch

class UploadInProgressFragment : FileListFragment() {

    private val drivePermissions: DrivePermissions by lazy { DrivePermissions() }
    private val uploadInProgressViewModel: UploadInProgressViewModel by viewModels()

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override var showPendingFiles = false

    override val noItemsIcon = R.drawable.ic_upload
    override val noItemsTitle = R.string.uploadInProgressNoFile

    private var pendingUploadFiles = mutableListOf<UploadFile>()
    private var pendingFiles = mutableListOf<File>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles()
        drivePermissions.registerPermissions(this) { authorized ->
            if (!authorized) findNavController().popBackStack()
        }

        super.onViewCreated(view, savedInstanceState)

        setupCollapsingToolbarLayout()
        observeTrackUploadWorkerProgress()
        observeTrackUploadWorkerSucceeded()
        observeIndexUploadToDelete()
        mainViewModel.refreshActivities.removeObservers(super.getViewLifecycleOwner())
        setupOnStopUploadButtonClicked()

        if (isPendingFolders()) {
            fileAdapter.onFileClicked = { navigateToUploadView(it.id, it.name) }
        } else {
            binding.toolbar.apply {
                setNavigationOnClickListener { popBackStack() }
                menu.findItem(R.id.closeItem).isVisible = true
            }
            requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { popBackStack() }
        }

        binding.sortLayout.isGone = true
    }

    private fun setupCollapsingToolbarLayout() {
        val fromPendingFolders = findNavController().previousBackStackEntry?.destination?.id == R.id.uploadInProgressFragment
        binding.collapsingToolbarLayout.title = if (folderId > 0 && fromPendingFolders) {
            folderName
        } else {
            getString(R.string.uploadInProgressTitle)
        }
    }

    private fun observeTrackUploadWorkerProgress() {
        requireContext().trackUploadWorkerProgress().observe(viewLifecycleOwner) {
            val workInfo = it.firstOrNull() ?: return@observe
            val fileName = workInfo.progress.getString(UploadWorker.FILENAME) ?: return@observe
            val progress = workInfo.progress.getInt(UploadWorker.PROGRESS, 0)
            val isUploaded = workInfo.progress.getBoolean(UploadWorker.IS_UPLOADED, false)
            val remoteFolderId = workInfo.progress.getInt(UploadWorker.REMOTE_FOLDER_ID, 0)
            val position = fileAdapter.indexOf(fileName)

            if (folderId == remoteFolderId && position >= 0 || isPendingFolders()) {
                if (isUploaded) {
                    if (!isPendingFolders()) whenAnUploadIsDone(position, fileAdapter.fileList[position].id)
                    fileListViewModel.currentAdapterPendingFiles.value = fileAdapter.getFileObjectsList(null)
                } else {
                    fileAdapter.updateFileProgress(position, progress)
                }
            }

            SentryLog.d("uploadInProgress", "$progress%")
        }
    }

    private fun observeTrackUploadWorkerSucceeded() {
        requireContext().trackUploadWorkerSucceeded().observe(viewLifecycleOwner) {
            fileListViewModel.currentAdapterPendingFiles.value = fileAdapter.getFileObjectsList(null)
        }
    }

    private fun observeIndexUploadToDelete() {
        fileListViewModel.indexUploadToDelete.observe(viewLifecycleOwner) { list ->
            list?.forEach { (position, fileId) ->
                whenAnUploadIsDone(position, fileId)
            }
        }
    }

    private fun setupOnStopUploadButtonClicked() {
        fileAdapter.onStopUploadButtonClicked = { position, fileName ->
            pendingUploadFiles.find { it.fileName == fileName }?.let { syncFile ->
                val title = getString(R.string.uploadInProgressCancelFileUploadTitle, syncFile.fileName)
                Utils.createConfirmation(requireContext(), title) {
                    closeItemClicked(uploadFile = syncFile)
                    fileAdapter.deleteByFileName(fileName)
                }
            }
        }
    }

    override fun setupFileAdapter() {
        super.setupFileAdapter()
        fileAdapter.onFileClicked = null
        fileAdapter.uploadInProgress = true
        fileAdapter.checkIsPendingWifi(requireContext())
    }

    override fun onResume() {
        super.onResume()
        if (fileAdapter.fileList.isNotEmpty()) {
            fileListViewModel.currentAdapterPendingFiles.value = fileAdapter.getFileObjectsList(null)
        }
    }

    private fun whenAnUploadIsDone(position: Int, fileId: Int) {
        if (fileAdapter.fileList.getOrNull(position)?.id == fileId) {
            fileAdapter.deleteAt(position)
        }

        if (fileAdapter.fileList.isEmpty()) {
            if (isResumed) binding.noFilesLayout.toggleVisibility(true)
            activity?.showSnackbar(R.string.allUploadFinishedTitle)
            popBackStack()
        }
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
            closeItemClicked(folderId = folderId)
        }
    }

    private fun closeItemClicked(uploadFile: UploadFile? = null, folderId: Int? = null) {
        if (!isVisible) return

        viewLifecycleOwner.lifecycleScope.launch(Dispatchers.IO) {
            val progressDialog = Dispatchers.Main {
                Utils.createProgressDialog(requireContext(), R.string.allCancellationInProgress)
            }
            var needPopBackStack = false
            uploadFile?.let {
                it.uploadToken?.let { token ->
                    ApiRepository.cancelSession(AccountUtils.currentDriveId, token, it.okHttpClient)
                }
                UploadFile.deleteAll(listOf(it))
            }
            folderId?.let {
                UploadFile.cancelAllPendingFilesSessions(folderId = it)
                if (isPendingFolders()) UploadFile.deleteAll(null)
                else UploadFile.deleteAll(folderId = it)

                fileRecyclerView?.post { fileAdapter.setFiles(listOf()) }

                needPopBackStack = UploadFile.getCurrentUserPendingUploadsCount(folderId = it) == 0
            }

            Dispatchers.Main {
                progressDialog.dismiss()
                if (isResumed && needPopBackStack) {
                    val data = Data.Builder().putBoolean(UploadWorker.CANCELLED_BY_USER, true).build()
                    requireContext().syncImmediately(data, true)
                    popBackStack()
                }
            }
        }
    }

    private fun isPendingFolders() = folderId == Utils.OTHER_ROOT_ID

    private fun popBackStack() = with(findNavController()) {
        mainViewModel.refreshActivities.value = true

        fun notIgnorePendingFoldersIfNeeded(): Boolean {
            val isFromPendingFolders = previousBackStackEntry?.destination?.id == R.id.uploadInProgressFragment

            return if (UploadFile.getAllPendingFoldersCount(uploadInProgressViewModel.realmUpload) in 0..1 && isFromPendingFolders) {
                // TODO: Need refactor
                popBackStack()
                popBackStack()
                false
            } else true
        }

        if (notIgnorePendingFoldersIfNeeded()) findNavController().popBackStack()
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            if (!drivePermissions.checkWriteStoragePermission()) return
            if (ignoreCache) fileAdapter.setFiles(listOf())

            showLoadingTimer.start()
            fileAdapter.isComplete = false

            if (isPendingFolders()) {
                downloadPendingFolders()
            } else {
                downloadPendingFilesByFolderId()
            }
        }

        private fun downloadPendingFolders() {
            UploadFile.getAllPendingFolders(uploadInProgressViewModel.realmUpload)?.let { pendingFolders ->
                if (pendingFolders.count() == 1) navigateToFirstFolder(pendingFolders) else showPendingFolders()
            } ?: binding.noFilesLayout.toggleVisibility(true)
        }

        private fun navigateToFirstFolder(pendingFolders: RealmResults<UploadFile>) = with(binding) {
            val uploadFile = pendingFolders.first()!!
            val isSharedWithMe = AccountUtils.currentDriveId != uploadFile.driveId
            val userDrive = UserDrive(driveId = uploadFile.driveId, sharedWithMe = isSharedWithMe)

            swipeRefreshLayout.isRefreshing = true
            uploadInProgressViewModel.getFolder(uploadFile.remoteFolder, userDrive).observe(viewLifecycleOwner) {
                swipeRefreshLayout.isRefreshing = false
                it?.let { folder ->
                    navigateToUploadView(uploadFile.remoteFolder, folder.name)
                } ?: run {
                    popBackStack()
                    requireActivity().showSnackbar(
                        R.string.uploadFolderNotFoundError,
                        (requireActivity() as MainActivity).getMainFab()
                    )
                }
            }
        }

        private fun showPendingFolders() = with(binding) {
            swipeRefreshLayout.isRefreshing = true
            uploadInProgressViewModel.getPendingFolders().observe(viewLifecycleOwner) {
                it?.let { uploadFolders ->
                    pendingFiles = uploadFolders
                    fileAdapter.isComplete = true
                    fileAdapter.setFiles(uploadFolders)
                    noFilesLayout.toggleVisibility(uploadFolders.isEmpty())
                    showLoadingTimer.cancel()
                    swipeRefreshLayout.isRefreshing = false
                }
            }
        }

        private fun downloadPendingFilesByFolderId() = with(binding) {
            swipeRefreshLayout.isRefreshing = true
            uploadInProgressViewModel.getPendingFiles(folderId).observe(viewLifecycleOwner) {
                it?.let { (files, uploadFiles) ->
                    pendingUploadFiles = uploadFiles
                    pendingFiles = files

                    toolbar.menu.findItem(R.id.restartItem).isVisible = true
                    toolbar.menu.findItem(R.id.closeItem).isVisible = true
                    fileAdapter.setFiles(files)
                    fileAdapter.isComplete = true
                    showLoadingTimer.cancel()
                    swipeRefreshLayout.isRefreshing = false
                    noFilesLayout.toggleVisibility(files.isEmpty())
                } ?: noFilesLayout.toggleVisibility(true)
            }
        }
    }
}
