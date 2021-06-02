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

import android.os.Bundle
import android.view.View
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import kotlinx.android.synthetic.main.fragment_file_list.*

/**
 * Is Used for all "subtypes" of list fragment like Trash, SharedWithMe, MyShares,
 */
open class FileSubTypeListFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        sortButton.visibility = View.GONE
    }

    protected fun populateFileList(files: ArrayList<File>, isComplete: Boolean, ignoreOffline: Boolean = false) {
        if (fileAdapter.itemCount == 0) fileAdapter.setList(files)
        else fileRecyclerView.post { fileAdapter.addFileList(files) }
        fileAdapter.isComplete = isComplete
        timer.cancel()
        swipeRefreshLayout.isRefreshing = false
        changeNoFilesLayoutVisibility(
            files.isEmpty(),
            changeControlsVisibility = true,
            hideNavbar = true,
            ignoreOffline = ignoreOffline
        )
    }
}