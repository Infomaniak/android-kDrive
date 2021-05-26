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
import android.view.View.VISIBLE
import com.infomaniak.drive.R
import kotlinx.android.synthetic.main.fragment_file_list.*

class OfflineFileFragment : FileSubTypeListFragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles()
        folderID = -1
        super.onActivityCreated(savedInstanceState)

        sortButton.visibility = VISIBLE
        collapsingToolbarLayout.title = getString(R.string.offlineFileTitle)
        noFilesLayout.setup(
            icon = R.drawable.ic_offline,
            title = R.string.offlineFileNoFile,
            description = R.string.offlineFileNoFileDescription,
            initialListView = fileRecyclerView
        )
    }

    private inner class DownloadFiles : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            fileAdapter.setList(arrayListOf())
            fileListViewModel.getOfflineFiles(sortType).observe(viewLifecycleOwner) { files ->
                populateFileList(files, isComplete = true, ignoreOffline = true)
            }
        }
    }
}