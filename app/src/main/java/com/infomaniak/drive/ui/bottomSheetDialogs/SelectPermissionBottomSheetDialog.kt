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

import android.os.Bundle
import android.os.Parcelable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.ui.fileList.fileShare.PermissionsAdapter
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.parcel.Parcelize
import kotlinx.android.synthetic.main.fragment_select_permission.*
import kotlinx.coroutines.Dispatchers

class SelectPermissionBottomSheetDialog : FullScreenBottomSheetDialog() {
    private lateinit var adapter: PermissionsAdapter
    private lateinit var permissionsGroup: PermissionsGroup
    private val navigationArgs: SelectPermissionBottomSheetDialogArgs by navArgs()
    private val selectPermissionViewModel: SelectPermissionViewModel by navGraphViewModels(R.id.selectPermissionBottomSheetDialog)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_select_permission, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        selectPermissionViewModel.apply {
            currentPermission = currentPermission ?: navigationArgs.currentPermission
            currentFile = currentFile ?: FileController.getFileById(navigationArgs.currentFileId)
        }

        permissionsGroup = navigationArgs.permissionsGroup
        adapter = PermissionsAdapter(isExternalUser = permissionsGroup == PermissionsGroup.EXTERNAL_USERS_RIGHTS,
            onUpgradeOfferClicked = {
                safeNavigate(R.id.secureLinkShareBottomSheetDialog)
            },
            onPermissionChanged = { newPermission ->
                selectPermissionViewModel.currentPermission = newPermission
            })

        permissionsRecyclerView.adapter = adapter.apply {
            permissionsGroup.let { permissionsGroup ->
                val newPermissions: ArrayList<Permission> = when (permissionsGroup) {
                    PermissionsGroup.SHARE_LINK_SETTINGS -> arrayListOf(
                        ShareLink.ShareLinkPermission.PUBLIC,
                        ShareLink.ShareLinkPermission.INHERIT,
                        ShareLink.ShareLinkPermission.PASSWORD
                    )
                    PermissionsGroup.EXTERNAL_USERS_RIGHTS,
                    PermissionsGroup.USERS_RIGHTS -> arrayListOf(
                        Shareable.ShareablePermission.READ,
                        Shareable.ShareablePermission.WRITE,
                        Shareable.ShareablePermission.MANAGE,
                        Shareable.ShareablePermission.DELETE
                    )
                    PermissionsGroup.FILE_SHARE_UPDATE -> arrayListOf(
                        Shareable.ShareablePermission.READ,
                        Shareable.ShareablePermission.WRITE,
                        Shareable.ShareablePermission.MANAGE
                    )
                    PermissionsGroup.SHARE_LINK_OFFICE -> arrayListOf(
                        ShareLink.OfficePermission.READ,
                        ShareLink.OfficePermission.WRITE
                    )
                }
                setAll(newPermissions)
                selectionPosition = permissionList.indexOf(selectPermissionViewModel.currentPermission)
            }
        }

        saveButton.setOnClickListener {
            when (permissionsGroup) {
                PermissionsGroup.USERS_RIGHTS, PermissionsGroup.EXTERNAL_USERS_RIGHTS -> {
                    selectPermissionViewModel.currentFile?.let { file ->
                        updatePermission(
                            file = file,
                            shareableItem = navigationArgs.currentShareable,
                            permission = selectPermissionViewModel.currentPermission as Shareable.ShareablePermission?
                        )
                    }
                }
                PermissionsGroup.SHARE_LINK_OFFICE -> {
                    selectPermissionViewModel.currentFile?.let { file ->
                        updateShareLinkOfficePermission(
                            file = file,
                            permission = selectPermissionViewModel.currentPermission
                        )
                    }
                }
                else -> {
                    setBackNavigationResult(
                        SELECT_PERMISSION_NAV_KEY,
                        bundleOf(
                            PERMISSIONS_GROUP_BUNDLE_KEY to navigationArgs.permissionsGroup,
                            PERMISSION_BUNDLE_KEY to selectPermissionViewModel.currentPermission,
                            SHAREABLE_BUNDLE_KEY to navigationArgs.currentShareable
                        )
                    )
                }
            }
        }
    }

    private fun updateShareLinkOfficePermission(file: File, permission: Permission?) {
        saveButton.initProgress(viewLifecycleOwner)
        saveButton.showProgress()
        selectPermissionViewModel.editFileShareLinkOfficePermission(
            file = file,
            canEdit = (permission as ShareLink.OfficePermission).apiValue
        ).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.data == true) {
                setBackNavigationResult(
                    SELECT_PERMISSION_NAV_KEY,
                    bundleOf(
                        PERMISSION_BUNDLE_KEY to permission, PERMISSIONS_GROUP_BUNDLE_KEY to navigationArgs.permissionsGroup
                    )
                )
            } else {
                Utils.showSnackbar(requireView(), R.string.errorModification)
            }
            saveButton.hideProgress(R.string.buttonSave)
        }
    }

    private fun updatePermission(file: File, shareableItem: Shareable?, permission: Shareable.ShareablePermission? = null) {
        saveButton.initProgress(viewLifecycleOwner)
        saveButton.showProgress()
        selectPermissionViewModel.apply {
            shareableItem?.let { shareable ->
                if (permission == Shareable.ShareablePermission.DELETE || permission == null) {
                    deleteShare(file, shareable, permission)
                } else {
                    editShare(file, shareable, permission)
                }
            }
        }
    }

    private fun editShare(file: File, shareable: Shareable, permission: Shareable.ShareablePermission) {
        selectPermissionViewModel.editFileShare(file, shareable, permission).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.data == true) {
                setBackNavigationResult(
                    SELECT_PERMISSION_NAV_KEY,
                    bundleOf(PERMISSION_BUNDLE_KEY to permission, SHAREABLE_BUNDLE_KEY to shareable)
                )
            } else {
                Utils.showSnackbar(requireView(), R.string.errorRightModification)
            }
            saveButton.hideProgress(R.string.buttonSave)
        }
    }

    private fun deleteShare(file: File, shareable: Shareable, permission: Shareable.ShareablePermission?) {
        selectPermissionViewModel.deleteFileShare(file, shareable).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.data == true) {
                setBackNavigationResult(
                    SELECT_PERMISSION_NAV_KEY,
                    bundleOf(PERMISSION_BUNDLE_KEY to permission, SHAREABLE_BUNDLE_KEY to shareable)
                )
            } else {
                Utils.showSnackbar(requireView(), R.string.errorDelete)
            }
            saveButton.hideProgress(R.string.buttonSave)
        }
    }

    internal class SelectPermissionViewModel : ViewModel() {
        var currentFile: File? = null
        var currentPermission: Permission? = null

        fun deleteFileShare(file: File, shareable: Shareable) = liveData(Dispatchers.IO) {
            emit(ApiRepository.deleteFileShare(file, shareable))
        }

        fun editFileShare(
            file: File,
            shareableItem: Shareable,
            permission: Shareable.ShareablePermission
        ): LiveData<ApiResponse<Boolean>> {
            return liveData(Dispatchers.IO) {
                val body = mapOf("permission" to permission.apiValue)
                emit(ApiRepository.putFileShare(file, shareableItem, body))
            }
        }

        fun editFileShareLinkOfficePermission(file: File, canEdit: Boolean): LiveData<ApiResponse<Boolean>> {
            return liveData(Dispatchers.IO) {
                val body = mapOf("can_edit" to canEdit)
                emit(ApiRepository.putFileShareLink(file, body))
            }
        }
    }

    companion object {
        const val SELECT_PERMISSION_NAV_KEY = "permission_dialog_key"
        const val PERMISSION_BUNDLE_KEY = "permission_bundle_key"
        const val SHAREABLE_BUNDLE_KEY = "shareable_bundle_key"
        const val PERMISSIONS_GROUP_BUNDLE_KEY = "permissions_group_bundle"

        const val PERMISSIONS_GROUP_ARG = "permissionsGroup"
        const val CURRENT_FILE_ID_ARG = "currentFileId"
        const val CURRENT_PERMISSION_ARG = "currentPermission"
    }

    @Parcelize
    enum class PermissionsGroup : Parcelable {
        SHARE_LINK_SETTINGS,
        SHARE_LINK_OFFICE,
        USERS_RIGHTS,
        EXTERNAL_USERS_RIGHTS,
        FILE_SHARE_UPDATE
    }
}
