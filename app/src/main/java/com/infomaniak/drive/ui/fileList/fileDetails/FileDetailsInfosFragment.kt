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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.view.forEachIndexed
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.Share
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.UserAvatarView
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.fragment_file_details.*
import kotlinx.android.synthetic.main.fragment_file_details_infos.*

class FileDetailsInfosFragment : FileDetailsSubFragment() {

    private var shareLink: ShareLink? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_file_details_infos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileDetailsViewModel.currentFile.observe(viewLifecycleOwner) { currentFile ->
            setupShareLinkContainer(currentFile, fileDetailsViewModel.currentFileShare.value)
            displayUsersAvatars(currentFile)
            setupShareButton(currentFile)

            if (currentFile.createdAt.isPositive()) {
                addedDateValue.text = currentFile.getCreatedAt().format("dd MMM yyyy - HH:mm")
                addedDate.visibility = VISIBLE
            }

            if (currentFile.fileCreatedAt.isPositive()) {
                creationDateValue.text = currentFile.getFileCreatedAt().format("dd MMM yyyy - HH:mm")
                creationDate.visibility = VISIBLE
            }

            displayFileOwner(currentFile)

            if (currentFile.path.isNotBlank()) {
                pathValue.text = currentFile.path
                path.visibility = VISIBLE
            }

            currentFile.sizeWithVersions?.let {
                totalSizeValue.text = Formatter.formatFileSize(context, it)
                totalSize.visibility = VISIBLE
            }
            currentFile.size?.let {
                originalSizeValue.text = Formatter.formatFileSize(context, it)
                originalSize.visibility = VISIBLE
            }
        }

        fileDetailsViewModel.currentFileShare.observe(viewLifecycleOwner) { share ->
            pathValue.text = share.path
            setupShareLinkContainer(fileDetailsViewModel.currentFile.value, share)
        }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SELECT_PERMISSION_NAV_KEY) { bundle ->
            val permission = bundle.getParcelable<Permission>(SelectPermissionBottomSheetDialog.PERMISSION_BUNDLE_KEY)
            val isPublic = isPublicPermission(permission)

            fileDetailsViewModel.currentFile.value?.let { currentFile ->
                if ((isPublic && shareLink == null) || (!isPublic && shareLink != null)) {
                    mainViewModel.apply {
                        if (isPublic) createShareLink(currentFile) else deleteShareLink(currentFile)
                    }
                }
            }
        }
    }

    private fun setupShareButton(currentFile: File) {
        if (currentFile.rights?.share == true) {
            shareButton.visibility = VISIBLE
            shareButton.setOnClickListener {
                parentFragment?.safeNavigate(
                    FileDetailsFragmentDirections.actionFileDetailsFragmentToFileShareDetailsFragment(file = currentFile)
                )
            }
        } else {
            shareButton.visibility = GONE
        }
    }

    private fun setupShareLinkContainer(file: File?, share: Share?) {

        if (file?.rights?.canBecomeLink == true || file?.shareLink?.isNotBlank() == true) {
            shareLinkContainer.visibility = VISIBLE
            topShareDivider.visibility = VISIBLE
            botShareDivider.visibility = VISIBLE
            shareLinkContainer.setup(
                shareLink = share?.link,
                file = file,
                onTitleClicked = { shareLink, currentFileId ->
                    this.shareLink = shareLink
                    val (permissionsGroup, currentPermission) = selectPermissions(file.isFolder(), shareLink != null)
                    findNavController().navigate(
                        FileDetailsFragmentDirections.actionFileDetailsFragmentToSelectPermissionBottomSheetDialog(
                            currentFileId = currentFileId,
                            currentPermission = currentPermission,
                            permissionsGroup = permissionsGroup
                        )
                    )
                },
                onSettingsClicked = { shareLink, currentFile ->
                    this.shareLink = shareLink
                    findNavController().navigate(
                        FileDetailsFragmentDirections.actionFileDetailsFragmentToFileShareLinkSettings(
                            fileId = currentFile.id,
                            driveId = currentFile.driveId,
                            shareLink = this.shareLink!!, // cannot be null, if null, settings will not appear
                            isOnlyOfficeFile = currentFile.onlyoffice,
                            isFolder = currentFile.isFolder()
                        )
                    )
                })

        } else {
            shareLinkContainer.visibility = GONE
            topShareDivider.visibility = GONE
            botShareDivider.visibility = GONE
        }
    }

    private fun displayUsersAvatars(file: File) {
        val userIds = if (file.users.isEmpty()) arrayListOf(file.createdBy) else ArrayList(file.users)
        val userList = DriveInfosController.getUsers(userIds = userIds)
        if (userList.isEmpty()) {
            users.visibility = GONE
        } else {
            users.visibility = VISIBLE
            userListLayout.forEachIndexed { index, view ->
                (view as UserAvatarView).apply {
                    if (index < MAX_DISPLAYED_USERS) {
                        setUserAvatarOrHide(userList.getOrNull(index))
                    } else {
                        setUsersNumber(userList.size - MAX_DISPLAYED_USERS)
                    }
                }
            }
        }
    }

    private fun displayFileOwner(file: File) {
        val userList = DriveInfosController.getUsers(arrayListOf(file.createdBy))
        userList.firstOrNull()?.apply {
            owner.visibility = VISIBLE
            ownerAvatar.loadAvatar(this)
            ownerValue.text = displayName
        }
    }

    private fun createShareLink(currentFile: File) {
        mainViewModel.postFileShareLink(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess())
                shareLinkContainer.update(apiResponse.data)
            else
                requireActivity().showSnackbar(getString(R.string.errorShareLink))
        }
    }

    private fun deleteShareLink(currentFile: File) {
        mainViewModel.deleteFileShareLink(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            val success = apiResponse.data == true
            if (success)
                shareLinkContainer.update(null)
            else
                requireActivity().showSnackbar(apiResponse.translateError())
        }
    }

    override fun onResume() {
        super.onResume()
        requireParentFragment().addCommentButton.visibility = GONE
    }

    companion object {
        const val MAX_DISPLAYED_USERS = 4

        fun isPublicPermission(permission: Permission?): Boolean {
            return when (permission) {
                is ShareLink.ShareLinkFilePermission -> permission == ShareLink.ShareLinkFilePermission.PUBLIC
                is ShareLink.ShareLinkFolderPermission -> permission == ShareLink.ShareLinkFolderPermission.PUBLIC
                else -> false
            }
        }

        fun selectPermissions(
            isFolder: Boolean,
            shareLinkExiste: Boolean
        ): Pair<SelectPermissionBottomSheetDialog.PermissionsGroup, Permission> {
            val permissionsGroup: SelectPermissionBottomSheetDialog.PermissionsGroup
            val currentPermission = when {
                isFolder -> {
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_FOLDER_SETTINGS
                    if (shareLinkExiste) ShareLink.ShareLinkFolderPermission.PUBLIC
                    else ShareLink.ShareLinkFolderPermission.RESTRICTED
                }
                else -> {
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_FILE_SETTINGS
                    if (shareLinkExiste) ShareLink.ShareLinkFilePermission.PUBLIC
                    else ShareLink.ShareLinkFilePermission.RESTRICTED
                }
            }
            return Pair(permissionsGroup, currentPermission)
        }
    }
}
