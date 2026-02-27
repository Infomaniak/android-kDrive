/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui.home

import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.loadAvatar

class ActivityFilesFragment : FileListFragment() {

    val navigationArgs: ActivityFilesFragmentArgs by navArgs()
    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override val isActionMenuHidden: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        downloadFiles = DownloadFiles(navigationArgs.fileIdList)
        super.onViewCreated(view, savedInstanceState)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = false
        initActivity()
    }

    private fun initActivity() = with(binding) {
        currentActivity.isVisible = true
        navigationArgs.activityUser?.let { user -> currentActivityAvatar.loadAvatar(user) }
        currentActivityContent.text =
            String.format("%s %s", navigationArgs.activityUser?.displayName, navigationArgs.activityTranslation)
    }

    private inner class DownloadFiles(private var fileIdList: IntArray) : (Boolean, Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            binding.collapsingToolbarLayout.title = getString(R.string.fileDetailsActivitiesTitle)
            val fileList = FileController.getFilesFromIdList(
                realm = mainViewModel.realm,
                idList = fileIdList.toTypedArray(),
                order = fileListViewModel.sortType
            )

            fileList?.let { files ->
                fileAdapter.apply {
                    updateFileList(files)
                    isComplete = true
                    onFileClicked = ::openFile
                }
            }
        }
    }

    private fun openFile(file: File) {
        if (file.isTrashed()) {
            safeNavigate(R.id.trashFragment)
        } else {
            if (file.isFolder()) {
                safeNavigate(
                    ActivityFilesFragmentDirections.actionActivityFilesFragmentToFileListFragment(
                        folderId = file.id,
                        folderName = file.name
                    )
                )
            } else {
                val fileList = fileAdapter.getFileObjectsList(mainViewModel.realm)
                Utils.displayFile(mainViewModel, findNavController(), file, fileList)
            }
        }
    }
}
