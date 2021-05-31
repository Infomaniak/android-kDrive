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

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*

class FavoritesFragment : FileListFragment() {

    override var enabledMultiSelectMode: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (folderID == ROOT_ID) {
            downloadFiles = DownloadFiles()
            folderID = OTHER_ROOT_ID
        }
        super.onViewCreated(view, savedInstanceState)

        noFilesLayout.setup(
            icon = R.drawable.ic_star_filled,
            title = R.string.favoritesNoFile,
            initialListView = fileRecyclerView
        )
        collapsingToolbarLayout.title = getString(R.string.favoritesTitle)
        fileAdapter.onFileClicked = { file ->
            if (file.isFolder()) {
                fileListViewModel.cancelDownloadFiles()
                safeNavigate(FavoritesFragmentDirections.actionFavoritesFragmentSelf(file.id, file.name))
            } else Utils.displayFile(mainViewModel, findNavController(), file, fileAdapter.getItems())
        }
    }

    private inner class DownloadFiles : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            if (ignoreCache) fileAdapter.setList(arrayListOf())
            timer.start()
            fileAdapter.isComplete = false
            fileListViewModel.getFavoriteFiles(sortType).observe(viewLifecycleOwner) {
                it?.let { (files, isComplete) ->
                    if (fileAdapter.itemCount == 0) {
                        changeNoFilesLayoutVisibility(files.isEmpty(), false)
                        fileAdapter.setList(files)
                    } else fileRecyclerView.post { fileAdapter.addFileList(files) }
                    fileAdapter.isComplete = isComplete
                } ?: run {
                    changeNoFilesLayoutVisibility(fileAdapter.itemCount == 0, false)
                    fileAdapter.isComplete = true
                }
                timer.cancel()
                swipeRefreshLayout.isRefreshing = false
            }
        }
    }
}

