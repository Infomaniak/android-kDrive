/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.core.legacy.api.InternalTranslatedErrorCode
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.whenResultIsOk
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackTrashEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.FragmentBottomSheetTrashedFileActionsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.MainViewModel.FileResult
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.menu.TrashViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

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
                        .observe(this@TrashedFileActionsBottomSheetDialog) { fileRequest ->
                            restoreResult(fileRequest, originalPlace = false, folderName = folderName)
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

        viewLifecycleOwner.lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) {
            currentFile.setFileItem(currentTrashedFile)
        }
        restoreFileIn.setOnClickListener {
            trackTrashEvent(MatomoName.RestoreGivenFolder)
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
            trackTrashEvent(MatomoName.RestoreOriginFolder)
            mainViewModel.restoreTrashFile(currentTrashedFile).observe(this@TrashedFileActionsBottomSheetDialog) { fileRequest ->
                restoreResult(fileRequest, originalPlace = true)
            }
        }

        delete.setOnClickListener {
            Utils.confirmFileDeletion(requireContext(), fileName = currentTrashedFile.name, fromTrash = true) { dialog ->
                mainViewModel.deleteTrashFile(currentTrashedFile)
                    .observe(this@TrashedFileActionsBottomSheetDialog) { fileRequest ->
                        trackTrashEvent(MatomoName.DeleteFromTrash)
                        dialog.dismiss()
                        if (fileRequest.data == true) {
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

    private fun getErrorMessage(fileResult: FileResult) = when (fileResult.errorCode) {
        InternalTranslatedErrorCode.UnknownError.code -> R.string.errorRestore
        ErrorCode.CONFLICT_ERROR -> R.string.errorConflict
        else -> fileResult.errorResId
    }

    private fun restoreResult(fileResult: FileResult, originalPlace: Boolean, folderName: String? = null) {
        if (fileResult.isSuccess) {
            val title = if (originalPlace) R.plurals.trashedFileRestoreFileToOriginalPlaceSuccess
            else R.plurals.trashedFileRestoreFileInSuccess

            val args = arrayListOf(currentTrashedFile.name).apply {
                if (!originalPlace && folderName != null) add(folderName)
            }

            showSnackbar(resources.getQuantityString(title, 1, *args.toTypedArray()))
            dismissAndRemoveFileFromList()
        } else {
            val snackbarText = getErrorMessage(fileResult)

            snackbarText?.let { text -> showSnackbar(text) }
            findNavController().popBackStack()
        }
    }

    private fun dismissAndRemoveFileFromList() {
        findNavController().popBackStack()
        trashViewModel.removeFileId.value = currentTrashedFile.id
    }
}
