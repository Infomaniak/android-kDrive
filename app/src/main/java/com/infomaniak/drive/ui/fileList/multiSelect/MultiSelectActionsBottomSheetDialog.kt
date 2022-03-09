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
package com.infomaniak.drive.ui.fileList.multiSelect

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.liveData
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackBulkActionEvent
import com.infomaniak.drive.utils.MatomoUtils.trackEvent
import kotlinx.android.synthetic.main.fragment_bottom_sheet_multi_select_actions.*
import kotlinx.android.synthetic.main.view_file_info_actions.view.*
import kotlinx.coroutines.Dispatchers

abstract class MultiSelectActionsBottomSheetDialog : BottomSheetDialogFragment() {

    val navigationArgs: MultiSelectActionsBottomSheetDialogArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_multi_select_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val areIndividualActionsVisible = navigationArgs.fileIds.size in 1..BulkOperationsUtils.MIN_SELECTED
        configureColoredFolder(areIndividualActionsVisible)
        configureAddFavorites(areIndividualActionsVisible)
        configureAvailableOffline(areIndividualActionsVisible)
        configureDownloadFile()
        configureDuplicateFile()
    }

    abstract fun configureColoredFolder(areIndividualActionsVisible: Boolean)

    private fun configureAddFavorites(areIndividualActionsVisible: Boolean) = with(navigationArgs) {
        addFavorites.apply {
            addFavoritesIcon.isEnabled = onlyFavorite
            val (text, action) = if (onlyFavorite) {
                R.string.buttonRemoveFavorites to SelectDialogAction.REMOVE_FAVORITES
            } else {
                R.string.buttonAddFavorites to SelectDialogAction.ADD_FAVORITES
            }
            addFavoritesText.setText(text)
            setOnClickListener { onActionSelected(action) }
            isVisible = areIndividualActionsVisible
        }
    }

    private fun configureAvailableOffline(areIndividualActionsVisible: Boolean) = with(navigationArgs) {
        availableOfflineSwitch.apply {
            isChecked = onlyOffline
            setOnCheckedChangeListener { _, _ -> selectOfflineDialogActionCallBack() }
        }
        disabledAvailableOffline.isVisible = onlyFolders
        availableOffline.apply {
            setOnClickListener { selectOfflineDialogActionCallBack() }
            isVisible = areIndividualActionsVisible
        }
    }

    private fun selectOfflineDialogActionCallBack() {
        val action = if (navigationArgs.onlyOffline) SelectDialogAction.REMOVE_OFFLINE else SelectDialogAction.ADD_OFFLINE
        onActionSelected(action)
    }

    private fun configureDownloadFile() {
        val drivePermissions = DrivePermissions().apply {
            registerPermissions(this@MultiSelectActionsBottomSheetDialog) { authorized ->
                if (authorized) downloadFileArchive()
            }
        }
        downloadFile.apply {
            setOnClickListener {
                if (drivePermissions.checkWriteStoragePermission()) {
                    context?.applicationContext?.trackEvent("FileAction", TrackerAction.CLICK, "bulkDownload")
                    downloadFileArchive()
                }
            }
            isVisible = navigationArgs.fileIds.isNotEmpty()
        }
    }

    private fun configureDuplicateFile() {
        duplicateFile.setOnClickListener { onActionSelected(SelectDialogAction.DUPLICATE) }
    }

    private fun downloadFileArchive() {

        fun downloadArchive(fileIds: IntArray) = liveData(Dispatchers.IO) {
            emit(ApiRepository.getUUIDArchiveFiles(AccountUtils.currentDriveId, fileIds))
        }

        downloadArchive(navigationArgs.fileIds).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let {
                    val downloadURL = Uri.parse(ApiRoutes.downloadArchiveFiles(AccountUtils.currentDriveId, it.uuid))
                    requireContext().startDownloadFile(downloadURL, ARCHIVE_FILE_NAME)
                }
            } else {
                requireActivity().showSnackbar(apiResponse.translatedError)
            }
            onActionSelected()
        }
    }

    fun onActionSelected(type: SelectDialogAction? = null) {
        val finalType = when (type) {
            SelectDialogAction.ADD_FAVORITES -> BulkOperationType.ADD_FAVORITES
            SelectDialogAction.REMOVE_FAVORITES -> BulkOperationType.REMOVE_FAVORITES
            SelectDialogAction.ADD_OFFLINE -> BulkOperationType.ADD_OFFLINE
            SelectDialogAction.REMOVE_OFFLINE -> BulkOperationType.REMOVE_OFFLINE
            SelectDialogAction.DUPLICATE -> BulkOperationType.COPY
            SelectDialogAction.COLOR_FOLDER -> BulkOperationType.COLOR_FOLDER
            else -> null
        }

        (parentFragment as MultiSelectFragment).apply {
            if (finalType == null) {
                closeMultiSelect()
            } else {
                context?.applicationContext?.trackBulkActionEvent(finalType, navigationArgs.fileIds.size)
                when (finalType) {
                    BulkOperationType.COPY -> duplicateFiles()
                    BulkOperationType.COLOR_FOLDER -> colorFolders()
                    else -> performBulkOperation(finalType)
                }
            }
        }
        parentFragmentManager.beginTransaction().remove(this).commit()
    }

    enum class SelectDialogAction {
        ADD_FAVORITES, REMOVE_FAVORITES, ADD_OFFLINE, REMOVE_OFFLINE, DUPLICATE, COLOR_FOLDER
    }

    private companion object {
        const val ARCHIVE_FILE_NAME = "Archive.zip"
    }
}
