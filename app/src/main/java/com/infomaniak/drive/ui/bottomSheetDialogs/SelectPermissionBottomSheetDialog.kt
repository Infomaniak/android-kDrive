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

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.gson.JsonObject
import com.infomaniak.core.legacy.utils.SnackbarUtils
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackShareRightsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.ShareLink.EditPermission
import com.infomaniak.drive.data.models.ShareLink.OfficeFilePermission
import com.infomaniak.drive.data.models.ShareLink.OfficeFolderPermission
import com.infomaniak.drive.data.models.ShareLink.ShareLinkDocumentPermission
import com.infomaniak.drive.data.models.ShareLink.ShareLinkFilePermission
import com.infomaniak.drive.data.models.ShareLink.ShareLinkFolderPermission
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.Shareable.ShareablePermission
import com.infomaniak.drive.databinding.FragmentSelectPermissionBinding
import com.infomaniak.drive.ui.fileList.fileShare.PermissionsAdapter
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import kotlinx.coroutines.Dispatchers
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
            currentPermission = currentPermission ?: navigationArgs.currentPermission
            currentFile = currentFile ?: FileController.getFileById(navigationArgs.currentFileId)
        }

        permissionsGroup = navigationArgs.permissionsGroup

        configurePermissionsAdapter()
        configureSaveButton()
    }

    private fun configurePermissionsAdapter() {
        adapter = PermissionsAdapter(
            isExternalUser = permissionsGroup == PermissionsGroup.EXTERNAL_USERS_RIGHTS,
            onPermissionChanged = { selectPermissionViewModel.currentPermission = it },
        ).apply {
            setAll(getPermissions())
            selectionPosition = permissionList.indexOf(selectPermissionViewModel.currentPermission)
        }
        binding.permissionsRecyclerView.adapter = adapter
    }

    private fun getPermissions(): List<Permission> {
        return when (permissionsGroup) {
            PermissionsGroup.SHARE_LINK_FILE_SETTINGS -> arrayListOf(
                ShareLinkFilePermission.RESTRICTED,
                ShareLinkFilePermission.PUBLIC
            )
            PermissionsGroup.SHARE_LINK_FOLDER_SETTINGS -> arrayListOf(
                ShareLinkFolderPermission.RESTRICTED,
                ShareLinkFolderPermission.PUBLIC
            )
            PermissionsGroup.SHARE_LINK_DOCUMENT_SETTINGS -> arrayListOf(
                ShareLinkDocumentPermission.RESTRICTED,
                ShareLinkDocumentPermission.PUBLIC
            )
            PermissionsGroup.FILE_SHARE_UPDATE -> arrayListOf(
                ShareablePermission.READ,
                ShareablePermission.WRITE,
                ShareablePermission.MANAGE
            )
            PermissionsGroup.EXTERNAL_USERS_RIGHTS -> arrayListOf(
                ShareablePermission.READ,
                ShareablePermission.WRITE_EXTERNAL,
                ShareablePermission.DELETE
            )
            PermissionsGroup.USERS_RIGHTS -> arrayListOf(
                ShareablePermission.READ,
                ShareablePermission.WRITE,
                ShareablePermission.DELETE
            )
            PermissionsGroup.SHARE_LINK_FILE_OFFICE -> arrayListOf(
                OfficeFilePermission.READ,
                OfficeFilePermission.WRITE
            )
            PermissionsGroup.SHARE_LINK_FOLDER_OFFICE -> arrayListOf(
                OfficeFolderPermission.READ,
                OfficeFolderPermission.WRITE
            )
        }
    }

    private fun configureSaveButton() {
        binding.saveButton.setOnClickListener {
            with(selectPermissionViewModel) {
                when (permissionsGroup) {
                    PermissionsGroup.EXTERNAL_USERS_RIGHTS, PermissionsGroup.USERS_RIGHTS -> {
                        currentFile?.let { file ->
                            updatePermission(file, navigationArgs.currentShareable, currentPermission as ShareablePermission?)
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
            file, canEdit = (permission as EditPermission).apiValue
        ).observe(viewLifecycleOwner) { apiResponse ->
            val bundle = bundleOf(PERMISSION_BUNDLE_KEY to permission)
            handleFileShareApiResponse(apiResponse, OFFICE_EDITING_RIGHTS_NAV_KEY, bundle, R.string.errorModification)
        }
    }

    private fun updatePermission(file: File, shareableItem: Shareable?, permission: ShareablePermission? = null) = with(binding) {
        saveButton.initProgress(viewLifecycleOwner)
        saveButton.showProgressCatching()
        shareableItem?.let { shareable ->
            if (permission == ShareablePermission.DELETE || permission == null) {
                trackShareRightsEvent(MatomoName.DeleteUser)
                deleteShare(file, shareable, permission)
            } else {
                trackShareRightsEvent(permission.name.lowercase() + "Right")
                editShare(file, shareable, permission)
            }
        }
    }

    private fun deleteShare(file: File, shareable: Shareable, permission: ShareablePermission?) {
        selectPermissionViewModel.deleteFileShare(file, shareable).observe(viewLifecycleOwner) { apiResponse ->
            val bundle = bundleOf(PERMISSION_BUNDLE_KEY to permission, SHAREABLE_BUNDLE_KEY to shareable)
            handleFileShareApiResponse(apiResponse, UPDATE_USERS_RIGHTS_NAV_KEY, bundle, R.string.errorDelete)
        }
    }

    private fun editShare(file: File, shareable: Shareable, permission: ShareablePermission) {
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
        if (apiResponse.data == true) {
            setBackNavigationResult(key, bundle)
        } else {
            SnackbarUtils.showSnackbar(requireView(), errorMessage)
        }
        binding.saveButton.hideProgressCatching(R.string.buttonSave)
    }

    internal class SelectPermissionViewModel : ViewModel() {

        var currentFile: File? = null
        var currentPermission: Permission? = null

        fun editFileShareLinkOfficePermission(file: File, canEdit: Boolean) = liveData(Dispatchers.IO) {
            val body = JsonObject().apply { addProperty("can_edit", canEdit) }
            val apiResponse = ApiRepository.updateShareLink(file, body)
            if (apiResponse.data == true) FileController.updateShareLinkWithRemote(file.id)
            emit(apiResponse)

        }

        fun deleteFileShare(file: File, shareable: Shareable) = liveData(Dispatchers.IO) {
            emit(ApiRepository.deleteFileShare(file, shareable))
        }

        fun editFileShare(file: File, shareableItem: Shareable, permission: ShareablePermission) = liveData(Dispatchers.IO) {
            emit(ApiRepository.putFileShare(file, shareableItem, mapOf("right" to permission.apiValue)))
        }
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
