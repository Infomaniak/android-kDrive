/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui.selectPermission

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.SnackbarUtils
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.MatomoDrive
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.databinding.FragmentSelectPermissionBinding
import com.infomaniak.drive.ui.fileList.fileShare.PermissionsAdapter
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import kotlinx.parcelize.Parcelize

class SelectPermissionBottomSheetDialog : FullScreenBottomSheetDialog() {

    private var binding: FragmentSelectPermissionBinding by safeBinding()

    private lateinit var adapter: PermissionsAdapter
    private lateinit var permissionsGroup: PermissionsGroup
    private val navigationArgs: SelectPermissionBottomSheetDialogArgs by navArgs()
    private val selectPermissionViewModel: SelectPermissionViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSelectPermissionBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        selectPermissionViewModel.apply {
            currentFile = currentFile ?: FileController.getFileById(navigationArgs.currentFileId)
        }

        permissionsGroup = navigationArgs.permissionsGroup

        configurePermissionsAdapter()
        configureSaveButton()
    }

    private fun configurePermissionsAdapter() {
        binding.permissionsRecyclerView.adapter = PermissionsAdapter(
            isExternalUser = navigationArgs.permissionsGroup == PermissionsGroup.EXTERNAL_USERS_RIGHTS,
            permissionList = getPermissions(),
            initialSelectedPermission = navigationArgs.currentPermission,
        )
    }

    private fun getPermissions(): List<Permission> {
        return when (permissionsGroup) {
            PermissionsGroup.SHARE_LINK_FILE_SETTINGS -> listOf(
                ShareLink.ShareLinkFilePermission.RESTRICTED,
                ShareLink.ShareLinkFilePermission.PUBLIC
            )
            PermissionsGroup.SHARE_LINK_FOLDER_SETTINGS -> listOf(
                ShareLink.ShareLinkFolderPermission.RESTRICTED,
                ShareLink.ShareLinkFolderPermission.PUBLIC
            )
            PermissionsGroup.SHARE_LINK_DOCUMENT_SETTINGS -> listOf(
                ShareLink.ShareLinkDocumentPermission.RESTRICTED,
                ShareLink.ShareLinkDocumentPermission.PUBLIC
            )
            PermissionsGroup.FILE_SHARE_UPDATE -> listOfNotNull(
                Shareable.ShareablePermission.READ,
                Shareable.ShareablePermission.WRITE,
                Shareable.ShareablePermission.MANAGE.takeIf { selectPermissionViewModel.currentFile?.isChildOfCommonDirectory() == true }
            )
            PermissionsGroup.EXTERNAL_USERS_RIGHTS -> listOfNotNull(
                Shareable.ShareablePermission.READ,
                Shareable.ShareablePermission.WRITE_EXTERNAL,
                Shareable.ShareablePermission.DELETE,
                Shareable.ShareablePermission.REMOVE_DRIVE_ACCESS.takeIf { AccountUtils.getCurrentDrive()?.isOrganisationAdmin == true }
            )
            PermissionsGroup.USERS_RIGHTS -> listOfNotNull(
                Shareable.ShareablePermission.READ,
                Shareable.ShareablePermission.WRITE,
                Shareable.ShareablePermission.MANAGE.takeIf { selectPermissionViewModel.currentFile?.isChildOfCommonDirectory() == true },
                Shareable.ShareablePermission.DELETE
            )
            PermissionsGroup.SHARE_LINK_FILE_OFFICE -> listOf(
                ShareLink.OfficeFilePermission.READ,
                ShareLink.OfficeFilePermission.WRITE
            )
            PermissionsGroup.SHARE_LINK_FOLDER_OFFICE -> listOf(
                ShareLink.OfficeFolderPermission.READ,
                ShareLink.OfficeFolderPermission.WRITE
            )
        }
    }

    private fun configureSaveButton() {
        binding.saveButton.setOnClickListener {
            with(selectPermissionViewModel) {
                when (permissionsGroup) {
                    PermissionsGroup.EXTERNAL_USERS_RIGHTS, PermissionsGroup.USERS_RIGHTS -> {
                        currentFile?.let { file ->
                            updatePermission(
                                file,
                                navigationArgs.currentShareable,
                                currentPermission as Shareable.ShareablePermission?
                            )
                        }
                    }
                    PermissionsGroup.SHARE_LINK_FILE_OFFICE, PermissionsGroup.SHARE_LINK_FOLDER_OFFICE -> {
                        currentFile?.let { file -> updateShareLinkOfficePermission(file, currentPermission) }
                    }
                    else -> {
                        val key = if (permissionsGroup == PermissionsGroup.FILE_SHARE_UPDATE) {
                            ADD_USERS_RIGHTS_NAV_KEY
                        } else {
                            SHARE_LINK_ACCESS_NAV_KEY
                        }
                        setBackNavigationResult(key, bundleOf(PERMISSION_BUNDLE_KEY to currentPermission))
                    }
                }
            }
        }
    }

    private fun updateShareLinkOfficePermission(file: File, permission: Permission?) = with(binding) {
        saveButton.initProgress(viewLifecycleOwner)
        saveButton.showProgressCatching()
        selectPermissionViewModel.editFileShareLinkOfficePermission(
            file, canEdit = (permission as ShareLink.EditPermission).apiValue
        ).observe(viewLifecycleOwner) { apiResponse ->
            val bundle = bundleOf(PERMISSION_BUNDLE_KEY to permission)
            handleFileShareApiResponse(apiResponse, OFFICE_EDITING_RIGHTS_NAV_KEY, bundle, R.string.errorModification)
        }
    }

    private fun updatePermission(file: File, shareableItem: Shareable?, permission: Shareable.ShareablePermission? = null) =
        with(binding) {
            shareableItem?.let { shareable ->
                saveButton.initProgress(viewLifecycleOwner)
                saveButton.showProgressCatching()
                when (permission) {
                    Shareable.ShareablePermission.DELETE, null -> {
                        MatomoDrive.trackShareRightsEvent(MatomoDrive.MatomoName.DeleteUser)
                        deleteShare(file, shareable, permission)
                    }
                    Shareable.ShareablePermission.REMOVE_DRIVE_ACCESS -> {
                        MatomoDrive.trackShareRightsEvent(MatomoDrive.MatomoName.RemoveDriveUser)
                        removeDriveUser(file, shareable, permission)
                    }
                    else -> {
                        MatomoDrive.trackShareRightsEvent(permission.name.lowercase() + "Right")
                        editShare(file, shareable, permission)
                    }
                }
            }
        }

    private fun deleteShare(file: File, shareable: Shareable, permission: Shareable.ShareablePermission?) {
        selectPermissionViewModel.deleteFileShare(file, shareable).observe(viewLifecycleOwner) { apiResponse ->
            val bundle = bundleOf(PERMISSION_BUNDLE_KEY to permission, SHAREABLE_BUNDLE_KEY to shareable)
            handleFileShareApiResponse(apiResponse, UPDATE_USERS_RIGHTS_NAV_KEY, bundle, R.string.errorDelete)
        }
    }

    private fun removeDriveUser(file: File, shareable: Shareable, permission: Shareable.ShareablePermission?) {
        selectPermissionViewModel.removeDriveUser(file, shareable).observe(viewLifecycleOwner) { apiResponse ->
            val bundle = bundleOf(PERMISSION_BUNDLE_KEY to permission, SHAREABLE_BUNDLE_KEY to shareable)
            handleFileShareApiResponse(apiResponse, UPDATE_USERS_RIGHTS_NAV_KEY, bundle, R.string.errorRightModification)
        }
    }

    private fun editShare(file: File, shareable: Shareable, permission: Shareable.ShareablePermission) {
        selectPermissionViewModel.editFileShare(file, shareable, permission).observe(viewLifecycleOwner) { apiResponse ->
            val bundle = bundleOf(PERMISSION_BUNDLE_KEY to permission, SHAREABLE_BUNDLE_KEY to shareable)
            handleFileShareApiResponse(apiResponse, UPDATE_USERS_RIGHTS_NAV_KEY, bundle, R.string.errorRightModification)
        }
    }

    private fun handleFileShareApiResponse(
        apiResponse: ApiResponse<Boolean>,
        key: String,
        bundle: Bundle,
        @StringRes errorMessage: Int,
    ) {
        when {
            apiResponse.isSuccess() -> setBackNavigationResult(key, bundle)
            else -> SnackbarUtils.showSnackbar(requireView(), apiResponse.translateError(errorMessage))
        }
        binding.saveButton.hideProgressCatching(R.string.buttonSave)
    }

    companion object {
        const val SHARE_LINK_ACCESS_NAV_KEY = "share_link_access_nav_key"
        const val ADD_USERS_RIGHTS_NAV_KEY = "add_users_rights_nav_key"
        const val UPDATE_USERS_RIGHTS_NAV_KEY = "update_users_rights_nav_key"
        const val OFFICE_EDITING_RIGHTS_NAV_KEY = "office_editing_rights_nav_key"
        const val PERMISSION_BUNDLE_KEY = "permission_bundle_key"
        const val SHAREABLE_BUNDLE_KEY = "shareable_bundle_key"
    }

    @Parcelize
    enum class PermissionsGroup : Parcelable {
        SHARE_LINK_FILE_SETTINGS,
        SHARE_LINK_FOLDER_SETTINGS,
        SHARE_LINK_DOCUMENT_SETTINGS,
        SHARE_LINK_FILE_OFFICE,
        SHARE_LINK_FOLDER_OFFICE,
        USERS_RIGHTS,
        EXTERNAL_USERS_RIGHTS,
        FILE_SHARE_UPDATE,
    }
}
