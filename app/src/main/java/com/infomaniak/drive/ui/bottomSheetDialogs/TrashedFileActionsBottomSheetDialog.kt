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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.api.ErrorCode.Companion.formatError
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.menu.TrashViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackTrashEvent
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_bottom_sheet_trashed_file_actions.*

class TrashedFileActionsBottomSheetDialog : BottomSheetDialogFragment() {

    private val trashViewModel: TrashViewModel by navGraphViewModels(R.id.trashFragment)
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var currentTrashedFile: File

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            val folderId = data?.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)
            val folderName = data?.extras?.getString(SelectFolderActivity.FOLDER_NAME_TAG)
            folderId?.let {
                mainViewModel.restoreTrashFile(currentTrashedFile, folderId)
                    .observe(this@TrashedFileActionsBottomSheetDialog) { apiResponse ->
                        restoreResult(apiResponse, originalPlace = false, folderName = folderName)
                    }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_trashed_file_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentTrashedFile = trashViewModel.selectedFile.value ?: File()

        currentFile.setFileItem(currentTrashedFile)
        restoreFileIn.setOnClickListener {
            trackTrashEvent("restoreGiveFolder")
            val intent = Intent(requireContext(), SelectFolderActivity::class.java).apply {
                putExtra(SelectFolderActivity.USER_ID_TAG, AccountUtils.currentUserId)
                putExtra(SelectFolderActivity.USER_DRIVE_ID_TAG, AccountUtils.currentDriveId)
            }
            selectFolderResultLauncher.launch(intent)
        }

        restoreFileToOriginalPlace.setOnClickListener {
            trackTrashEvent("restoreOriginFolder")
            mainViewModel.restoreTrashFile(currentTrashedFile).observe(this) { apiResponse ->
                restoreResult(apiResponse, originalPlace = true)
            }
        }

        delete.setOnClickListener {
            Utils.confirmFileDeletion(requireContext(), fileName = currentTrashedFile.name, fromTrash = true) { dialog ->
                mainViewModel.deleteTrashFile(currentTrashedFile).observe(this) { apiResponse ->
                    trackTrashEvent("deleteFromTrash")
                    dialog.dismiss()
                    if (apiResponse.data == true) {
                        val title = resources.getQuantityString(R.plurals.snackbarDeleteConfirmation, 1, currentTrashedFile.name)
                        requireActivity().showSnackbar(title)
                        dismissAndRemoveFileFromList()
                    } else {
                        requireActivity().showSnackbar(R.string.errorDelete)
                        findNavController().popBackStack()
                    }
                }
            }
        }
    }

    private fun restoreResult(apiResponse: ApiResponse<Any>, originalPlace: Boolean, folderName: String? = null) {
        if (apiResponse.isSuccess()) {
            val title = if (originalPlace) R.plurals.trashedFileRestoreFileToOriginalPlaceSuccess
            else R.plurals.trashedFileRestoreFileInSuccess

            val args = arrayListOf(currentTrashedFile.name).apply {
                if (!originalPlace && folderName != null) add(folderName)
            }

            requireActivity().showSnackbar(resources.getQuantityString(title, 1, *args.toTypedArray()))
            dismissAndRemoveFileFromList()
        } else {
            val title = if (apiResponse.formatError() == ErrorCode.AN_ERROR_HAS_OCCURRED) R.string.errorRestore
            else apiResponse.translateError()

            requireActivity().showSnackbar(title)
            findNavController().popBackStack()
        }
    }

    private fun dismissAndRemoveFileFromList() {
        findNavController().popBackStack()
        trashViewModel.removeFileId.value = currentTrashedFile.id
    }
}
