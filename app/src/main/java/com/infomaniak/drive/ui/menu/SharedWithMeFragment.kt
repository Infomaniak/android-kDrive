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
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.bottomSheetDialogs.DriveMaintenanceBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.ui.fileList.multiSelect.SharedWithMeMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.isPositive
import com.infomaniak.drive.utils.safeNavigate
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_file_list.*

class SharedWithMeFragment : FileSubTypeListFragment() {

    private val navigationArgs: SharedWithMeFragmentArgs by navArgs()
    private lateinit var realm: Realm

    override var enabledMultiSelectMode: Boolean = true
    override var hideBackButtonWhenRoot: Boolean = false

    override fun initSwipeRefreshLayout(): SwipeRefreshLayout? = swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val inDriveList = folderId == ROOT_ID && !navigationArgs.driveId.isPositive()
        val inDriveRoot = folderId == ROOT_ID && navigationArgs.driveId.isPositive()
        mainViewModel.currentFolder.value = null
        userDrive = UserDrive(driveId = navigationArgs.driveId, sharedWithMe = true)
        realm = FileController.getRealmInstance(userDrive)
        downloadFiles = DownloadFiles(
            when {
                inDriveList -> {
                    enabledMultiSelectMode = false
                    null
                }
                inDriveRoot -> File(driveId = navigationArgs.driveId, type = File.Type.DRIVE.value)
                else -> File(id = folderId, name = folderName, driveId = navigationArgs.driveId)
            }
        )
        setNoFilesLayout = SetNoFilesLayout()

        fileListViewModel.isSharedWithMe = true
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = if (inDriveList) getString(R.string.sharedWithMeTitle) else navigationArgs.folderName

        sortButton.isGone = inDriveList
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
                else -> {
                    val fileList = fileAdapter.getFileObjectsList(realm)
                    Utils.displayFile(mainViewModel, findNavController(), file, fileList, isSharedWithMe = true)
                }
            }
        }

        setupMultiSelectLayout()
    }

    override fun onDestroy() {
        if (::realm.isInitialized) realm.close()
        super.onDestroy()
    }

    private fun openMaintenanceDialog(driveName: String) {
        safeNavigate(
            R.id.driveMaintenanceBottomSheetFragment,
            bundleOf(DriveMaintenanceBottomSheetDialog.DRIVE_NAME to driveName)
        )
    }

    private fun openSharedWithMeFolder(file: File) {
        safeNavigate(
            SharedWithMeFragmentDirections.actionSharedWithMeFragmentSelf(
                folderId = if (file.isDrive()) ROOT_ID else file.id,
                folderName = file.name,
                driveId = file.driveId
            )
        )
    }

    private fun setupMultiSelectLayout() {
        multiSelectLayout?.apply {
            moveButtonMultiSelect.isInvisible = true
            deleteButtonMultiSelect.isInvisible = true
        }
    }

    override fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        super.onMenuButtonClicked(
            multiSelectBottomSheet = SharedWithMeMultiSelectActionsBottomSheetDialog(),
            areAllFromTheSameFolder = false,
        )
    }

    companion object {
        const val MATOMO_CATEGORY = "sharedWithMeFileAction"
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                icon = R.drawable.ic_share,
                title = R.string.sharedWithMeNoFile,
                initialListView = fileRecyclerView
            )
        }
    }

    private inner class DownloadFiles() : (Boolean, Boolean) -> Unit {
        private var folder: File? = null

        constructor(folder: File?) : this() {
            this.folder = folder
        }

        override fun invoke(ignoreCache: Boolean, isNewSort: Boolean) {
            if (ignoreCache && !fileAdapter.fileList.isManaged) fileAdapter.setFiles(arrayListOf())
            showLoadingTimer.start()
            fileAdapter.isComplete = false

            folder?.let { folder ->
                fileListViewModel.getFiles(
                    parentId = if (folder.isDrive()) ROOT_ID else folder.id,
                    ignoreCache = true,
                    order = fileListViewModel.sortType,
                    userDrive = userDrive
                ).observe(viewLifecycleOwner) {
                    it?.let { (_, children, _) ->
                        mainViewModel.currentFolder.value = it.parentFolder
                        populateFileList(
                            ArrayList(children),
                            isComplete = true,
                            realm = realm,
                            isNewSort = isNewSort
                        )
                    }
                }
            } ?: run {
                val driveList = DriveInfosController.getDrives(userId = AccountUtils.currentUserId, sharedWithMe = true)
                populateFileList(
                    ArrayList(driveList.map { drive -> drive.convertToFile() }),
                    isComplete = true,
                    isNewSort = isNewSort
                )
            }
        }
    }
}