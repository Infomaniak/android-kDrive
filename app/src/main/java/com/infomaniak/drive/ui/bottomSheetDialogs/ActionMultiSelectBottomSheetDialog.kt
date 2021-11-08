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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.utils.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_action_multi_select.*
import kotlinx.coroutines.Dispatchers

class ActionMultiSelectBottomSheetDialog : BottomSheetDialogFragment() {

    private val actionMultiSelectModel by viewModels<ActionMultiSelectModel>()
    private val navigationArgs: ActionMultiSelectBottomSheetDialogArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bottom_sheet_action_multi_select, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        addFavorites.setOnClickListener { onActionSelected(SelectDialogAction.ADD_FAVORITES) }

        availableOfflineSwitch.setOnCheckedChangeListener { _, _ -> onActionSelected(SelectDialogAction.OFFLINE) }
        availableOffline.setOnClickListener { onActionSelected(SelectDialogAction.OFFLINE) }
        duplicateFile.setOnClickListener { onActionSelected(SelectDialogAction.DUPLICATE) }

        disabledAvailableOffline.isVisible = navigationArgs.onlyFolders

        val otherActionsVisibility = navigationArgs.fileIds.size in 1..BulkOperationsUtils.MIN_SELECTED
        availableOffline.isVisible = otherActionsVisibility
        addFavorites.isVisible = otherActionsVisibility

        val drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { authorized -> if (authorized) downloadFileArchive() }
        downloadFile.setOnClickListener {
            if (drivePermissions.checkWriteStoragePermission()) downloadFileArchive()
        }
        downloadFile.isVisible = navigationArgs.fileIds.isNotEmpty()
    }

    private fun downloadFileArchive() {
        actionMultiSelectModel.downloadArchive(navigationArgs.fileIds).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let {
                    val downloadURL = Uri.parse(ApiRoutes.downloadArchiveFiles(AccountUtils.currentDriveId, it.uuid))
                    requireContext().startDownloadFile(downloadURL, "Archive.zip")
                }
                onActionSelected()
            } else {
                requireActivity().showSnackbar(apiResponse.translatedError)
            }
        }
    }

    private fun onActionSelected(type: SelectDialogAction? = null) {
        val finalType = when (type) {
            SelectDialogAction.ADD_FAVORITES -> BulkOperationType.ADD_FAVORITES
            SelectDialogAction.OFFLINE -> BulkOperationType.SET_OFFLINE
            SelectDialogAction.DUPLICATE -> BulkOperationType.COPY
            else -> null
        }

        if (finalType == null) setBackNavigationResult(DISABLE_SELECT_MODE, true)
        else setBackNavigationResult(SELECT_DIALOG_ACTION, finalType)
    }

    class ActionMultiSelectModel(app: Application) : AndroidViewModel(app) {
        fun downloadArchive(fileIds: IntArray) = liveData(Dispatchers.IO) {
            emit(ApiRepository.getUUIDArchiveFiles(AccountUtils.currentDriveId, fileIds))
        }
    }

    enum class SelectDialogAction {
        ADD_FAVORITES, OFFLINE, DUPLICATE
    }

    companion object {
        const val DISABLE_SELECT_MODE = "disable_select_mode"
        const val SELECT_DIALOG_ACTION = "select_dialog_action"
    }
}