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
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.isPositive
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*

class SharedWithMeFragment : FileSubTypeListFragment() {

    private val navigationArgs: SharedWithMeFragmentArgs by navArgs()

    override var hideBackButtonWhenRoot: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val inDriveList = folderID == ROOT_ID && !navigationArgs.driveID.isPositive()
        val inDriveRoot = folderID == ROOT_ID && navigationArgs.driveID.isPositive()
        downloadFiles = DownloadFiles(
            when {
                inDriveList -> null
                inDriveRoot -> File(driveId = navigationArgs.driveID, type = File.Type.DRIVE.value)
                else -> File(id = folderID, name = folderName, driveId = navigationArgs.driveID)
            }
        )

        fileListViewModel.isSharedWithMe = true
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = if (inDriveList) getString(R.string.sharedWithMeTitle) else navigationArgs.folderName
        noFilesLayout.setup(
            icon = R.drawable.ic_share,
            title = R.string.sharedWithMeNoFile,
            initialListView = fileRecyclerView
        )

        sortButton.visibility = if (inDriveList) View.GONE else View.VISIBLE
        fileAdapter.onFileClicked = { file ->
            fileListViewModel.cancelDownloadFiles()
            when {
                file.isDrive() -> {
                    DriveInfosController.getDrives(AccountUtils.currentUserId, file.driveId, sharedWithMe = true).firstOrNull()
                        ?.let { currentDrive ->
                            if (currentDrive.maintenance) openMaintenanceDialog(currentDrive.name)
                            else openSharedWithMeFolder(file)
                        }
                }
                file.isFolder() -> openSharedWithMeFolder(file)
                else -> Utils.displayFile(mainViewModel, findNavController(), file, fileAdapter.getItems(), isSharedWithMe = true)
            }
        }
    }

    private fun openMaintenanceDialog(driveName: String) {
        safeNavigate(
            SharedWithMeFragmentDirections.actionSharedWithMeFragmentToDriveMaintenanceBottomSheetFragment(
                driveName = driveName
            )
        )
    }

    private fun openSharedWithMeFolder(file: File) {
        safeNavigate(
            SharedWithMeFragmentDirections.actionSharedWithMeFragmentSelf(
                folderID = if (file.isDrive()) ROOT_ID else file.id,
                folderName = file.name,
                driveID = file.driveId
            )
        )
    }

    private inner class DownloadFiles() : (Boolean) -> Unit {
        private var folder: File? = null

        constructor(folder: File?) : this() {
            this.folder = folder
        }

        override fun invoke(ignoreCache: Boolean) {
            if (ignoreCache) fileAdapter.setList(arrayListOf())
            timer.start()
            fileAdapter.isComplete = false

            folder?.let { folder ->
                fileListViewModel.getFiles(
                    parentId = if (folder.isDrive()) ROOT_ID else folder.id,
                    ignoreCache = true,
                    order = sortType,
                    userDrive = UserDrive(driveId = folder.driveId, sharedWithMe = true)
                ).observe(viewLifecycleOwner) {
                    it?.let { (_, children, isComplete) ->
                        populateFileList(ArrayList(children), isComplete)
                    }
                }
            } ?: run {
                val driveList = DriveInfosController.getDrives(userId = AccountUtils.currentUserId, sharedWithMe = true)
                populateFileList(ArrayList(driveList.map { drive -> drive.convertToFile() }), true)
            }
        }
    }
}