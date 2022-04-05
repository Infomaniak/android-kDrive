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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.liveData
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.FileInfoActionsBottomSheetDialog.Companion.openColorFolderBottomSheetDialog
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackEvent
import kotlinx.android.synthetic.main.fragment_bottom_sheet_multi_select_actions.*
import kotlinx.coroutines.Dispatchers

abstract class MultiSelectActionsBottomSheetDialog(private val matomoCategory: String) : BottomSheetDialogFragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    val navigationArgs: MultiSelectActionsBottomSheetDialogArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_bottom_sheet_multi_select_actions, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val areIndividualActionsVisible = with(navigationArgs) {
            fileIds.size in 1..BulkOperationsUtils.MIN_SELECTED && !isAllSelected
        }

        configureColoredFolder(areIndividualActionsVisible)
        configureAddFavorites(areIndividualActionsVisible)
        configureAvailableOffline()
        configureDownload()
        configureDuplicateFile()
        configureRestoreFileIn()
        configureRestoreFileToOriginalPlace()
        configureDeletePermanently()
    }

    protected open fun configureColoredFolder(areIndividualActionsVisible: Boolean) {
        if (areIndividualActionsVisible) {
            disabledColoredFolder.isGone = computeColoredFolderAvailability(navigationArgs.fileIds)
            coloredFolder.apply {
                setOnClickListener { onActionSelected(SelectDialogAction.COLOR_FOLDER) }
                isVisible = true
            }
        }
    }

    private fun computeColoredFolderAvailability(fileIds: IntArray): Boolean {
        return fileIds.any {
            val file = FileController.getFileProxyById(fileId = it, customRealm = mainViewModel.realm)
            file?.isAllowedToBeColored() == true
        }
    }

    protected open fun configureAddFavorites(areIndividualActionsVisible: Boolean) {
        val (text, action) = with(navigationArgs) {
            addFavoritesIcon.isEnabled = onlyFavorite
            if (onlyFavorite) {
                R.string.buttonRemoveFavorites to SelectDialogAction.REMOVE_FAVORITES
            } else {
                R.string.buttonAddFavorites to SelectDialogAction.ADD_FAVORITES
            }
        }
        addFavoritesText.setText(text)
        addFavorites.apply {
            setOnClickListener { onActionSelected(action) }
            isVisible = areIndividualActionsVisible
        }
    }

    protected open fun configureAvailableOffline() {
        with(navigationArgs) {
            availableOfflineIcon.isGone = onlyOffline
            availableOfflineComplete.isVisible = onlyOffline
            disabledAvailableOffline.isVisible = onlyFolders

            availableOfflineSwitch.apply {
                isChecked = onlyOffline
                setOnCheckedChangeListener { _, _ -> selectOfflineDialogActionCallBack() }
            }

            availableOffline.apply {
                isGone = isAllSelected
                setOnClickListener { selectOfflineDialogActionCallBack() }
            }
        }
    }

    private fun selectOfflineDialogActionCallBack() {
        val action = if (navigationArgs.onlyOffline) SelectDialogAction.REMOVE_OFFLINE else SelectDialogAction.ADD_OFFLINE
        onActionSelected(action)
    }

    protected open fun configureDownload() {
        val drivePermissions = DrivePermissions().apply {
            registerPermissions(this@MultiSelectActionsBottomSheetDialog) { authorized ->
                if (authorized) download()
            }
        }

        downloadFile.apply {
            setOnClickListener {
                if (drivePermissions.checkWriteStoragePermission()) {
                    context?.applicationContext?.trackEvent(matomoCategory, TrackerAction.CLICK, "bulkDownload")
                    download()
                }
            }

            isVisible = navigationArgs.fileIds.isNotEmpty()
        }
    }

    protected open fun configureDuplicateFile() {
        duplicateFile.setOnClickListener { onActionSelected(SelectDialogAction.DUPLICATE) }
    }

    protected open fun configureRestoreFileIn() {
        restoreFileIn.isGone = true
    }

    protected open fun configureRestoreFileToOriginalPlace() {
        restoreFileToOriginalPlace.isGone = true
    }

    protected open fun configureDeletePermanently() {
        deletePermanently.isGone = true
    }

    private fun download() {
        if (navigationArgs.areAllFromTheSameFolder) downloadArchive() else downloadFiles()
    }

    private fun downloadArchive() {
        liveData(Dispatchers.IO) {
            emit(ApiRepository.getUUIDArchiveFiles(AccountUtils.currentDriveId, navigationArgs.fileIds))
        }.observe(viewLifecycleOwner) { apiResponse ->
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

    private fun downloadFiles() {
        navigationArgs.fileIds.forEach { fileId ->
            FileController.getFileProxyById(fileId = fileId, customRealm = mainViewModel.realm)?.let { file ->
                val downloadUrl = Uri.parse(ApiRoutes.downloadFile(file))
                val fileName = if (file.isFolder()) "${file.name}.zip" else file.name
                requireContext().startDownloadFile(downloadUrl, fileName)
            }
        }
        onActionSelected()
    }

    fun onActionSelected(type: SelectDialogAction? = null) {
        val finalType = when (type) {
            SelectDialogAction.COLOR_FOLDER -> BulkOperationType.COLOR_FOLDER
            SelectDialogAction.ADD_FAVORITES -> BulkOperationType.ADD_FAVORITES
            SelectDialogAction.REMOVE_FAVORITES -> BulkOperationType.REMOVE_FAVORITES
            SelectDialogAction.ADD_OFFLINE -> BulkOperationType.ADD_OFFLINE
            SelectDialogAction.REMOVE_OFFLINE -> BulkOperationType.REMOVE_OFFLINE
            SelectDialogAction.DUPLICATE -> BulkOperationType.COPY
            SelectDialogAction.RESTORE_IN -> BulkOperationType.RESTORE_IN
            SelectDialogAction.RESTORE_TO_ORIGIN -> BulkOperationType.RESTORE_TO_ORIGIN
            SelectDialogAction.DELETE_PERMANENTLY -> BulkOperationType.DELETE_PERMANENTLY
            else -> null
        }

        (parentFragment as MultiSelectFragment).apply {
            if (finalType == null) {
                closeMultiSelect()
            } else {
                when (finalType) {
                    BulkOperationType.COLOR_FOLDER -> openColorFolderBottomSheetDialog(null)
                    BulkOperationType.COPY -> duplicateFiles()
                    BulkOperationType.RESTORE_IN -> restoreIn()
                    BulkOperationType.RESTORE_TO_ORIGIN, BulkOperationType.DELETE_PERMANENTLY -> {
                        performBulkOperation(finalType, areAllFromTheSameFolder = false)
                    }
                    else -> performBulkOperation(finalType)
                }
            }
        }
        parentFragmentManager.beginTransaction().remove(this).commit()
    }

    enum class SelectDialogAction {
        ADD_FAVORITES, REMOVE_FAVORITES,
        ADD_OFFLINE, REMOVE_OFFLINE,
        DUPLICATE,
        COLOR_FOLDER,
        RESTORE_IN, RESTORE_TO_ORIGIN, DELETE_PERMANENTLY,
    }

    private companion object {
        const val ARCHIVE_FILE_NAME = "Archive.zip"
    }
}
