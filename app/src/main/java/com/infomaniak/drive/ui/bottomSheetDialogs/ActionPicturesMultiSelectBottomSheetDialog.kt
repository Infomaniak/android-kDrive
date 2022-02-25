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

import android.app.Application
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
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
import com.infomaniak.drive.ui.menu.PicturesFragment
import com.infomaniak.drive.utils.*
import kotlinx.android.synthetic.main.fragment_bottom_sheet_action_multi_select.*
import kotlinx.coroutines.Dispatchers

class ActionPicturesMultiSelectBottomSheetDialog : BottomSheetDialogFragment() {

    private val actionMultiSelectModel by viewModels<ActionMultiSelectModel>()
    private val navigationArgs: ActionMultiSelectBottomSheetDialogArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_action_multi_select, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val otherActionsVisibility = navigationArgs.fileIds.size in 1..BulkOperationsUtils.MIN_SELECTED
        configureColoredFolder()
        configureAddFavorites(otherActionsVisibility)
        configureAvailableOffline(otherActionsVisibility)
        configureDownloadFile()
        configureDuplicateFile()
    }

    private fun configureColoredFolder() {
        coloredFolder.isGone = true
    }

    private fun configureAddFavorites(otherActionsVisibility: Boolean) = with(navigationArgs) {
        addFavorites.apply {
            addFavoritesIcon.isEnabled = onlyFavorite
            val (text, action) = if (onlyFavorite) {
                R.string.buttonRemoveFavorites to SelectDialogAction.REMOVE_FAVORITES
            } else {
                R.string.buttonAddFavorites to SelectDialogAction.ADD_FAVORITES
            }
            addFavoritesText.setText(text)
            setOnClickListener { onActionSelected(action) }
            isVisible = otherActionsVisibility
        }
    }

    private fun configureAvailableOffline(otherActionsVisibility: Boolean) = with(navigationArgs) {
        availableOfflineSwitch.apply {
            isChecked = onlyOffline
            setOnCheckedChangeListener { _, _ -> selectOfflineDialogActionCallBack() }
        }
        disabledAvailableOffline.isVisible = onlyFolders
        availableOffline.apply {
            setOnClickListener { selectOfflineDialogActionCallBack() }
            isVisible = otherActionsVisibility
        }
    }

    private fun selectOfflineDialogActionCallBack() {
        val action = if (navigationArgs.onlyOffline) SelectDialogAction.REMOVE_OFFLINE else SelectDialogAction.ADD_OFFLINE
        onActionSelected(action)
    }

    private fun configureDownloadFile() {
        val drivePermissions = DrivePermissions().apply {
            registerPermissions(this@ActionPicturesMultiSelectBottomSheetDialog) { authorized ->
                if (authorized) downloadFileArchive()
            }
        }
        downloadFile.apply {
            setOnClickListener { if (drivePermissions.checkWriteStoragePermission()) downloadFileArchive() }
            isVisible = navigationArgs.fileIds.isNotEmpty()
        }
    }

    private fun configureDuplicateFile() {
        duplicateFile.setOnClickListener { onActionSelected(SelectDialogAction.DUPLICATE) }
    }

    private fun downloadFileArchive() {
        actionMultiSelectModel.downloadArchive(navigationArgs.fileIds).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let {
                    val downloadURL = Uri.parse(ApiRoutes.downloadArchiveFiles(AccountUtils.currentDriveId, it.uuid))
                    requireContext().startDownloadFile(downloadURL, "Archive.zip")
                }
            } else {
                requireActivity().showSnackbar(apiResponse.translatedError)
            }
            onActionSelected()
        }
    }

    private fun onActionSelected(type: SelectDialogAction? = null) {
        val finalType = when (type) {
            SelectDialogAction.ADD_FAVORITES -> BulkOperationType.ADD_FAVORITES
            SelectDialogAction.REMOVE_FAVORITES -> BulkOperationType.REMOVE_FAVORITES
            SelectDialogAction.ADD_OFFLINE -> BulkOperationType.ADD_OFFLINE
            SelectDialogAction.REMOVE_OFFLINE -> BulkOperationType.REMOVE_OFFLINE
            SelectDialogAction.DUPLICATE -> BulkOperationType.COPY
            else -> null
        }

        (parentFragment as PicturesFragment).apply {
            when (finalType) {
                null -> closeMultiSelect()
                BulkOperationType.COPY -> duplicateFiles()
                else -> performBulkOperation(finalType)
            }
        }

        parentFragmentManager.beginTransaction().remove(this).commit()
    }

    class ActionMultiSelectModel(app: Application) : AndroidViewModel(app) {
        fun downloadArchive(fileIds: IntArray) = liveData(Dispatchers.IO) {
            emit(ApiRepository.getUUIDArchiveFiles(AccountUtils.currentDriveId, fileIds))
        }
    }

    enum class SelectDialogAction {
        ADD_FAVORITES, REMOVE_FAVORITES, ADD_OFFLINE, REMOVE_OFFLINE, DUPLICATE
    }
}
