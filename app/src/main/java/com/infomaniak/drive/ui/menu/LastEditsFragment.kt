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
import android.view.View.GONE
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import kotlinx.android.synthetic.main.fragment_file_list.*

class LastEditsFragment : FileSubTypeListFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles()
        folderID = Utils.OTHER_ROOT_ID
        super.onViewCreated(view, savedInstanceState)

        sortButton.visibility = GONE
        collapsingToolbarLayout.title = getString(R.string.lastEditsTitle)
        noFilesLayout.setup(
            icon = R.drawable.ic_clock,
            title = R.string.noFilesDescriptionNoNetwork, // TODO - Translate
            description = R.string.offlineFileNoFileDescription, // TODO - Translate
            initialListView = fileRecyclerView
        )
    }

    private inner class DownloadFiles : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            fileAdapter.setList(arrayListOf())
            mainViewModel.getLastModifiedFiles(AccountUtils.currentDriveId).observe(viewLifecycleOwner) { result ->
                populateFileList(result?.files ?: ArrayList(), isComplete = result?.isComplete ?: true, ignoreOffline = true)
            }
        }
    }
}