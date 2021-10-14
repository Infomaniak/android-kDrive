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
package com.infomaniak.drive.ui.menu

import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_file_list.*

/**
 * Is Used for all "subtypes" of list fragment like Trash, SharedWithMe, MyShares,
 */
open class FileSubTypeListFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false

    protected fun populateFileList(
        files: ArrayList<File>,
        isComplete: Boolean,
        folderId: Int? = null,
        forceClean: Boolean = false,
        ignoreOffline: Boolean = false,
        realm: Realm? = null,
    ) {
        if (fileAdapter.itemCount == 0 || forceClean || isNewSort) {
            if (realm == null) {
                fileAdapter.setFiles(files)
            } else {
                val order = when (findNavController().currentDestination?.id) {
                    R.id.recentChangesFragment -> null
                    else -> fileListViewModel.sortType
                }

                FileController.getRealmLiveFiles(folderId ?: this.folderID, realm, order).apply {
                    fileAdapter.updateFileList(this)
                }
            }
        }
        isNewSort = false
        fileAdapter.isComplete = isComplete
        showLoadingTimer.cancel()
        swipeRefreshLayout.isRefreshing = false
        changeNoFilesLayoutVisibility(
            files.isEmpty(),
            changeControlsVisibility = true,
            ignoreOffline = ignoreOffline
        )
    }
}