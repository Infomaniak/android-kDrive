/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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
import com.infomaniak.drive.MatomoDrive.trackTrashEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.FragmentBottomSheetTrashedFileActionsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.menu.TrashViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.ApiErrorCode
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.whenResultIsOk

class TrashedFileActionsBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: FragmentBottomSheetTrashedFileActionsBinding by safeBinding()

    private val trashViewModel: TrashViewModel by navGraphViewModels(R.id.trashFragment)
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var currentTrashedFile: File

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            data?.extras?.let { bundle ->
                SelectFolderActivityArgs.fromBundle(bundle).apply {
                    mainViewModel.restoreTrashFile(currentTrashedFile, folderId)
                        .observe(this@TrashedFileActionsBottomSheetDialog) { apiResponse ->
                            restoreResult(apiResponse, originalPlace = false, folderName = folderName)
                        }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetTrashedFileActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        currentTrashedFile = trashViewModel.selectedFile.value ?: File()

        currentFile.setFileItem(currentTrashedFile)
        restoreFileIn.setOnClickListener {
            trackTrashEvent("restoreGivenFolder")
            Intent(requireContext(), SelectFolderActivity::class.java).apply {
                putExtras(
                    SelectFolderActivityArgs(
                        userId = AccountUtils.currentUserId,
                        driveId = AccountUtils.currentDriveId
                    ).toBundle()
                )
                selectFolderResultLauncher.launch(this)
            }
        }

        restoreFileToOriginalPlace.setOnClickListener {
            trackTrashEvent("restoreOriginFolder")
            mainViewModel.restoreTrashFile(currentTrashedFile).observe(this@TrashedFileActionsBottomSheetDialog) { apiResponse ->
                restoreResult(apiResponse, originalPlace = true)
            }
        }

        delete.setOnClickListener {
            Utils.confirmFileDeletion(requireContext(), fileName = currentTrashedFile.name, fromTrash = true) { dialog ->
                mainViewModel.deleteTrashFile(currentTrashedFile)
                    .observe(this@TrashedFileActionsBottomSheetDialog) { apiResponse ->
                        trackTrashEvent("deleteFromTrash")
                        dialog.dismiss()
                        if (apiResponse.data == true) {
                            showSnackbar(getString(R.string.snackbarDeleteConfirmation, currentTrashedFile.name))
                            dismissAndRemoveFileFromList()
                        } else {
                            showSnackbar(R.string.errorDelete)
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

            showSnackbar(resources.getQuantityString(title, 1, *args.toTypedArray()))
            dismissAndRemoveFileFromList()
        } else {
            val title = if (apiResponse.error?.code == ApiErrorCode.AN_ERROR_HAS_OCCURRED) R.string.errorRestore
            else apiResponse.translateError()

            showSnackbar(title)
            findNavController().popBackStack()
        }
    }

    private fun dismissAndRemoveFileFromList() {
        findNavController().popBackStack()
        trashViewModel.removeFileId.value = currentTrashedFile.id
    }
}
