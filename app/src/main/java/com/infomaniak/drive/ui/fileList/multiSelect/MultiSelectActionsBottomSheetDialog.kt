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
package com.infomaniak.drive.ui.fileList.multiSelect

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
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.ArchiveUUID.ArchiveBody
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.databinding.FragmentBottomSheetMultiSelectActionsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.FileInfoActionsBottomSheetDialog.Companion.openColorFolderBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.FileInfoActionsBottomSheetDialog.Companion.openManageCategoriesBottomSheetDialog
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.BulkOperationsUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.DownloadManagerUtils
import com.infomaniak.lib.core.utils.safeBinding
import kotlinx.coroutines.Dispatchers

abstract class MultiSelectActionsBottomSheetDialog(private val matomoCategory: String) : BottomSheetDialogFragment() {

    protected var binding: FragmentBottomSheetMultiSelectActionsBinding by safeBinding()

    private val mainViewModel: MainViewModel by activityViewModels()
    val navigationArgs: MultiSelectActionsBottomSheetDialogArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetMultiSelectActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val areIndividualActionsVisible = with(navigationArgs) {
            fileIds.size in 1..BulkOperationsUtils.MIN_SELECTED && !isAllSelected
        }

        configureManageCategories(areIndividualActionsVisible)
        configureAddFavorites(areIndividualActionsVisible)
        configureColoredFolder(areIndividualActionsVisible)
        configureAvailableOffline()
        configureDownload()
        configureMoveFile()
        configureDuplicateFile()
        configureRestoreFileIn()
        configureRestoreFileToOriginalPlace()
        configureDeletePermanently()
    }

    protected open fun configureManageCategories(areIndividualActionsVisible: Boolean) = with(binding) {
        if (areIndividualActionsVisible) {
            manageCategories.apply {
                isEnabled = computeManageCategoriesAvailability()
                setOnClickListener { onActionSelected(SelectDialogAction.MANAGE_CATEGORIES) }
                isVisible = true
            }
        }
    }

    private fun computeManageCategoriesAvailability() = navigationArgs.fileIds.any {
        val file = FileController.getFileProxyById(fileId = it, customRealm = mainViewModel.realm)
        file?.isDisabled() == false
    }

    protected open fun configureAddFavorites(areIndividualActionsVisible: Boolean): Unit = with(binding) {
        val (textRes, action) = if (navigationArgs.onlyFavorite) {
            R.string.buttonRemoveFavorites to SelectDialogAction.REMOVE_FAVORITES
        } else {
            R.string.buttonAddFavorites to SelectDialogAction.ADD_FAVORITES
        }

        addFavorites.apply {
            text = getString(textRes)
            setOnClickListener { onActionSelected(action) }
            isActivated = navigationArgs.onlyFavorite
            isVisible = areIndividualActionsVisible
        }
    }

    protected open fun configureColoredFolder(areIndividualActionsVisible: Boolean) = with(binding) {
        if (areIndividualActionsVisible) {
            coloredFolder.apply {
                isEnabled = computeColoredFolderAvailability()
                setOnClickListener { onActionSelected(SelectDialogAction.COLOR_FOLDER) }
                isVisible = true
            }
        }
    }

    private fun computeColoredFolderAvailability() = navigationArgs.fileIds.any {
        val file = FileController.getFileProxyById(fileId = it, customRealm = mainViewModel.realm)
        file?.isAllowedToBeColored() == true
    }

    protected open fun configureAvailableOffline(): Unit = with(binding) {
        availableOfflineIcon.isGone = navigationArgs.onlyOffline
        availableOfflineComplete.isVisible = navigationArgs.onlyOffline
        availableOffline.isEnabled = !navigationArgs.onlyFolders

        availableOfflineSwitch.apply {
            isChecked = navigationArgs.onlyOffline
            setOnCheckedChangeListener { _, _ -> selectOfflineDialogActionCallBack() }
        }

        availableOffline.apply {
            setOnClickListener { selectOfflineDialogActionCallBack() }
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

        binding.downloadFile.apply {
            setOnClickListener {
                if (drivePermissions.checkWriteStoragePermission()) {
                    trackEvent(matomoCategory, "bulkDownload")
                    download()
                }
            }

            isVisible = navigationArgs.fileIds.isNotEmpty() || navigationArgs.isAllSelected
        }
    }

    protected open fun configureMoveFile() {
        binding.moveFile.setOnClickListener { onActionSelected(SelectDialogAction.MOVE) }
    }

    protected open fun configureDuplicateFile() {
        binding.duplicateFile.setOnClickListener { onActionSelected(SelectDialogAction.DUPLICATE) }
    }

    protected open fun configureRestoreFileIn() {
        binding.restoreFileIn.isGone = true
    }

    protected open fun configureRestoreFileToOriginalPlace() {
        binding.restoreFileToOriginalPlace.isGone = true
    }

    protected open fun configureDeletePermanently() {
        binding.deletePermanently.isGone = true
    }

    private fun download() {
        if (navigationArgs.areAllFromTheSameFolder) downloadArchive() else downloadFiles()
    }

    private fun downloadArchive() = with(navigationArgs) {
        val archiveBody = if (isAllSelected) ArchiveBody(parentId, exceptFileIds) else ArchiveBody(fileIds)
        liveData(Dispatchers.IO) {
            emit(ApiRepository.buildArchive(AccountUtils.currentDriveId, archiveBody))
        }.observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                apiResponse.data?.let { archiveUUID ->
                    val downloadURL = ApiRoutes.downloadArchiveFiles(AccountUtils.currentDriveId, archiveUUID.uuid)
                    DownloadManagerUtils.scheduleDownload(requireContext(), downloadURL, ARCHIVE_FILE_NAME)
                }
            } else {
                showSnackbar(apiResponse.translatedError)
            }
            onActionSelected()
        }
    }

    private fun downloadFiles() {
        navigationArgs.fileIds.forEach { fileId ->
            FileController.getFileProxyById(
                fileId = fileId,
                customRealm = FileController.getRealmInstance(navigationArgs.userDrive),
            )?.let { file ->
                val fileName = if (file.isFolder()) "${file.name}.zip" else file.name
                DownloadManagerUtils.scheduleDownload(requireContext(), ApiRoutes.downloadFile(file), fileName)
            }
        }
        onActionSelected()
    }

    fun onActionSelected(type: SelectDialogAction? = null) {
        val finalType = when (type) {
            SelectDialogAction.MANAGE_CATEGORIES -> BulkOperationType.MANAGE_CATEGORIES
            SelectDialogAction.ADD_FAVORITES -> BulkOperationType.ADD_FAVORITES
            SelectDialogAction.REMOVE_FAVORITES -> BulkOperationType.REMOVE_FAVORITES
            SelectDialogAction.COLOR_FOLDER -> BulkOperationType.COLOR_FOLDER
            SelectDialogAction.ADD_OFFLINE -> BulkOperationType.ADD_OFFLINE
            SelectDialogAction.REMOVE_OFFLINE -> BulkOperationType.REMOVE_OFFLINE
            SelectDialogAction.DUPLICATE -> BulkOperationType.COPY
            SelectDialogAction.MOVE -> BulkOperationType.MOVE
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
                    BulkOperationType.MANAGE_CATEGORIES -> openManageCategoriesBottomSheetDialog(navigationArgs.fileIds)
                    BulkOperationType.COLOR_FOLDER -> openColorFolderBottomSheetDialog(null)
                    BulkOperationType.COPY -> duplicateFiles()
                    BulkOperationType.MOVE -> {
                        moveFiles(if (navigationArgs.areAllFromTheSameFolder) mainViewModel.currentFolder.value?.id else null)
                    }
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
        MANAGE_CATEGORIES,
        ADD_FAVORITES, REMOVE_FAVORITES,
        ADD_OFFLINE, REMOVE_OFFLINE,
        DUPLICATE,
        MOVE,
        COLOR_FOLDER,
        RESTORE_IN, RESTORE_TO_ORIGIN, DELETE_PERMANENTLY,
    }

    private companion object {
        const val ARCHIVE_FILE_NAME = "Archive.zip"
    }
}
