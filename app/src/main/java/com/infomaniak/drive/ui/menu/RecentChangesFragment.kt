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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.lifecycle.LiveData
import androidx.lifecycle.LiveDataScope
import androidx.lifecycle.liveData
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.home.HomeViewModel.Companion.DOWNLOAD_INTERVAL
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import java.util.*

class RecentChangesFragment : FileSubTypeListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles()
        setNoFilesLayout = SetNoFilesLayout()
        folderId = Utils.OTHER_ROOT_ID
        super.onViewCreated(view, savedInstanceState)

        sortButton.isGone = true
        collapsingToolbarLayout.title = getString(R.string.lastEditsTitle)
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_clock,
                title = R.string.homeNoActivities,
                initialListView = fileRecyclerView
            )
        }
    }

    private inner class DownloadFiles : (Boolean, Boolean) -> Unit {

        private var getRecentChangesJob = Job()
        private var lastModifiedTime: Long = 0

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            getRecentChanges(AccountUtils.currentDriveId, false).observe(viewLifecycleOwner) { result ->
                populateFileList(
                    files = result?.files ?: arrayListOf(),
                    folderId = FileController.RECENT_CHANGES_FILE_ID,
                    ignoreOffline = true,
                    isComplete = result?.isComplete ?: true,
                    realm = mainViewModel.realm,
                    isNewSort = isNewSort,
                )
            }
        }

        fun getRecentChanges(driveId: Int, onlyFirstPage: Boolean, forceDownload: Boolean = false): LiveData<FolderFilesResult?> {
            getRecentChangesJob.cancel()
            getRecentChangesJob = Job()
            val ignoreDownload = lastModifiedTime != 0L && (Date().time - lastModifiedTime) < DOWNLOAD_INTERVAL && !forceDownload
            return liveData(Dispatchers.IO + getRecentChangesJob) {
                if (ignoreDownload) {
                    emit(FolderFilesResult(files = FileController.getRecentChanges(), isComplete = true, page = 1))
                } else {
                    getRecentChangesRecursive(1, driveId, onlyFirstPage)
                }
            }
        }

        private suspend fun LiveDataScope<FolderFilesResult?>.getRecentChangesRecursive(
            page: Int,
            driveId: Int,
            onlyFirstPage: Boolean,
        ) {
            val isFirstPage = page == 1
            val apiResponse = ApiRepository.getLastModifiedFiles(driveId, page)
            when {
                apiResponse.isSuccess() -> apiResponse.data?.let { data ->
                    if (isFirstPage) emit(FolderFilesResult(files = data, isComplete = true, page = page))
                    if (data.size >= ApiRepository.PER_PAGE && !onlyFirstPage) {
                        getRecentChangesRecursive(page + 1, driveId, onlyFirstPage)
                    }
                    FileController.storeRecentChanges(data, isFirstPage)
                }
                isFirstPage -> emit(FolderFilesResult(files = FileController.getRecentChanges(), isComplete = true, page = 1))
                else -> emit(null)
            }
        }
    }
}
