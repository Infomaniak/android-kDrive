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
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.android.synthetic.main.fragment_file_list.*

class TrashFragment : FileSubTypeListFragment() {

    val trashViewModel: TrashViewModel by navGraphViewModels(R.id.trashFragment)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        sortType = File.SortType.RECENT_TRASHED
        downloadFiles =
            DownloadFiles(
                if (folderID != ROOT_ID) File(id = folderID, name = folderName, driveId = AccountUtils.currentDriveId)
                else null
            )

        super.onViewCreated(view, savedInstanceState)
        emptyTrash.apply {
            visibility = VISIBLE
            setOnClickListener {
                Utils.createConfirmation(
                    context = requireContext(),
                    title = getString(R.string.buttonEmptyTrash),
                    message = getString(R.string.modalEmptyTrashDescription),
                    isDeletion = true,
                    autoDismiss = false
                ) { dialog ->
                    trashViewModel.emptyTrash(AccountUtils.currentDriveId).observe(viewLifecycleOwner) { apiResponse ->
                        dialog.dismiss()
                        if (apiResponse.data == true) {
                            Utils.showSnackbar(requireView(), R.string.snackbarEmptyTrashConfirmation)
                            onRefresh()
                        } else requireActivity().showSnackbar(apiResponse.translateError())
                    }
                }
            }
        }

        if (folderID == ROOT_ID) collapsingToolbarLayout.title = getString(R.string.trashTitle)
        noFilesLayout.setup(icon = R.drawable.ic_delete, title = R.string.trashNoFile, initialListView = fileRecyclerView)

        fileAdapter.apply {
            showShareFileButton = false
            onFileClicked = { file ->
                trashViewModel.cancelTrashFileJob()
                if (file.isFolder()) safeNavigate(
                    TrashFragmentDirections.actionTrashFragmentSelf(
                        file.id,
                        file.name
                    )
                )
                else showTrashedFileActions(file)
            }
            onMenuClicked = { file -> showTrashedFileActions(file) }
        }

        trashViewModel.removeFileId.observe(viewLifecycleOwner) { fileToRemove ->
            removeFileFromAdapter(fileToRemove)
        }
    }

    private fun showTrashedFileActions(file: File) {
        trashViewModel.selectedFile.value = file
        safeNavigate(R.id.trashedFileActionsBottomSheetDialog)
    }

    private fun removeFileFromAdapter(fileId: Int) {
        fileAdapter.deleteByFileId(fileId)
        noFilesLayout.toggleVisibility(fileAdapter.getItems().isEmpty())
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
                trashViewModel.getTrashFile(folder, sortType).observe(viewLifecycleOwner) { pairResponse ->
                    val files = ArrayList(pairResponse?.first?.children ?: ArrayList())
                    populateFileList(files = files, isComplete = pairResponse?.second ?: true)
                }
            } ?: run {
                trashViewModel.getDriveTrash(AccountUtils.currentDriveId, sortType)
                    .observe(viewLifecycleOwner) { pairResponse ->
                        populateFileList(
                            files = pairResponse?.first ?: ArrayList(),
                            isComplete = pairResponse?.second ?: true
                        )
                    }
            }
        }
    }
}

