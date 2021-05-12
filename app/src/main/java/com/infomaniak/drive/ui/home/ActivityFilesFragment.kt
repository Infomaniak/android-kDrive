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
package com.infomaniak.drive.ui.home

import android.os.Bundle
import android.view.View.VISIBLE
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.fragment_pictures.collapsingToolbarLayout
import kotlinx.android.synthetic.main.fragment_pictures.swipeRefreshLayout

class ActivityFilesFragment : FileListFragment() {

    val navigationArgs: ActivityFilesFragmentArgs by navArgs()
    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        downloadFiles = DownloadFiles(navigationArgs.fileIdList)
        super.onActivityCreated(savedInstanceState)
        swipeRefreshLayout.isEnabled = false
        swipeRefreshLayout.isRefreshing = false
        initActivity()
    }

    private fun initActivity() {
        currentActivity.visibility = VISIBLE
        navigationArgs.activityUser?.let { user -> currentActivityAvatar.loadAvatar(user) }
        currentActivityContent.text =
            String.format("%s %s", navigationArgs.activityUser?.displayName, navigationArgs.activityTranslation)
    }

    private inner class DownloadFiles(private var fileIdList: IntArray) : (Boolean) -> Unit {
        override fun invoke(ignoreCache: Boolean) {
            collapsingToolbarLayout.title = getString(R.string.fileDetailsActivitiesTitle)
            val fileList = FileController.getFilesFromIdList(fileIdList.toTypedArray(), sortType).apply {
                map {
                    it.isFromActivities = true
                }
            }
            fileAdapter.apply {
                setList(fileList)
                isComplete = true
                onFileClicked = {
                    openFile(it)
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
                        folderID = file.id,
                        folderName = file.name
                    )
                )
            } else Utils.displayFile(mainViewModel, findNavController(), file, fileAdapter.getItems())
        }
    }
}
