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
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import io.realm.Realm

/**
 * Is Used for all "subtypes" of list fragment like Trash, SharedWithMe, MyShares,
 */
open class FileSubTypeListFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        fileAdapter.onEmptyList = { changeNoFilesLayoutVisibility(hideFileList = true, changeControlsVisibility = true) }
        multiSelectLayout?.selectAllButton?.isGone = true
    }

    override fun performBulkOperation(
        type: BulkOperationType,
        folderId: Int?,
        areAllFromTheSameFolder: Boolean,
        allSelectedFilesCount: Int?,
        destinationFolder: File?,
        color: String?,
    ) {
        // API doesn't support bulk operations for files originating from
        // different parent folders, so we repeat the action for each file.
        // Hence the `areAllFromTheSameFolder` set at false.
        super.performBulkOperation(
            type,
            folderId,
            false,
            allSelectedFilesCount,
            destinationFolder,
            color
        )
    }

    protected fun populateFileList(
        files: ArrayList<File>,
        isComplete: Boolean,
        folderId: Int? = null,
        forceClean: Boolean = false,
        ignoreOffline: Boolean = false,
        realm: Realm? = null,
        isNewSort: Boolean,
    ) {
        if (fileAdapter.itemCount == 0 || forceClean || isNewSort) {
            if (realm == null) {
                fileAdapter.setFiles(files)
            } else {
                val order = when (findNavController().currentDestination?.id) {
                    R.id.recentChangesFragment -> null
                    else -> fileListViewModel.sortType
                }

                FileController.getRealmLiveFiles(folderId ?: this.folderId, realm, order).apply {
                    fileAdapter.updateFileList(this)
                }
            }
        }

        fileAdapter.isComplete = isComplete
        showLoadingTimer.cancel()
        binding.swipeRefreshLayout.isRefreshing = false

        changeNoFilesLayoutVisibility(
            files.isEmpty(),
            changeControlsVisibility = true,
            ignoreOffline = ignoreOffline,
        )
    }
}
