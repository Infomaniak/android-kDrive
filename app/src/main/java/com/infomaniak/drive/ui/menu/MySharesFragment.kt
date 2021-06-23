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
import android.view.View.VISIBLE
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*

class MySharesFragment : FileSubTypeListFragment() {

    override var hideBackButtonWhenRoot: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (folderID == ROOT_ID) {
            downloadFiles = DownloadFiles()
            folderID = OTHER_ROOT_ID
        }
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = getString(R.string.mySharesTitle)
        noFilesLayout.setup(icon = R.drawable.ic_share, title = R.string.mySharesNoFile, initialListView = fileRecyclerView)
        sortButton.visibility = VISIBLE

        fileAdapter.onFileClicked = { file ->
            fileListViewModel.cancelDownloadFiles()
            if (file.isFolder()) safeNavigate(
                MySharesFragmentDirections.actionMySharesFragmentSelf(
                    file.id,
                    file.name
                )
            ) else Utils.displayFile(mainViewModel, findNavController(), file, fileAdapter.getItems())
        }
    }

    private inner class DownloadFiles : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            if (ignoreCache) fileAdapter.setList(arrayListOf())
            timer.start()
            fileAdapter.isComplete = false

            fileListViewModel.getMySharedFiles(sortType).observe(viewLifecycleOwner) {
                populateFileList(files = it?.first ?: ArrayList(), isComplete = true, forceClean = true)
            }
        }
    }
}