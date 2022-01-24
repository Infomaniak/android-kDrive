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

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.forEachIndexed
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.Share
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.ShareLinkContainerView
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
            setupCategoriesContainer(currentFile?.id, currentFile.getCategories())
            displayUsersAvatars(currentFile)
            setupShareButton(currentFile)

            if (currentFile.createdAt.isPositive()) {
                addedDateValue.text = currentFile.getCreatedAt().format(ShareLinkContainerView.formatFullDate)
                addedDate.isVisible = true
            }

            if (currentFile.fileCreatedAt.isPositive()) {
                creationDateValue.text = currentFile.getFileCreatedAt().format(ShareLinkContainerView.formatFullDate)
                creationDate.isVisible = true
            }

            displayFileOwner(currentFile)

            if (currentFile.path.isNotBlank()) setPath(currentFile.driveId, currentFile.path)

            currentFile.sizeWithVersions?.let {
                totalSizeValue.text = Formatter.formatFileSize(context, it)
                totalSize.isVisible = true
            }
            currentFile.size?.let {
                originalSizeValue.text = Formatter.formatFileSize(context, it)
                originalSize.isVisible = true
            }
        }

        fileDetailsViewModel.currentFileShare.observe(viewLifecycleOwner) { share ->
            fileDetailsViewModel.currentFile.value?.let { file ->
                setPath(file.driveId, share.path)
                setupShareLinkContainer(file, share)
            }
        }

        setBackActionHandlers()
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SHARE_LINK_ACCESS_NAV_KEY) { bundle ->
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

        getBackNavigationResult<List<Int>>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) {
            val file = fileDetailsViewModel.currentFile.value
            setupCategoriesContainer(
                fileId = file?.id,
                categories = DriveInfosController.getCurrentDriveCategoriesFromIds(it.toTypedArray()),
            )
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setPath(driveId: Int, path: String) {
        val drive = DriveInfosController.getDrives(AccountUtils.currentUserId, driveId = driveId).first()
        driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
        pathValue.text = "${drive.name}$path"
        pathView.isVisible = true
    }

    private fun setupShareButton(currentFile: File) {
        if (currentFile.rights?.share == true) {
            shareButton.isVisible = true
            shareButton.setOnClickListener {
                parentFragment?.safeNavigate(
                    FileDetailsFragmentDirections.actionFileDetailsFragmentToFileShareDetailsFragment(fileId = currentFile.id)
                )
            }
        } else {
            shareButton.isGone = true
        }
    }

    private fun setupShareLinkContainer(file: File, share: Share?) {
        when {
            file.isDropBox() -> showDropBoxShareLinkView(file)
            file.rights?.canBecomeLink == true || file.shareLink?.isNotBlank() == true -> showShareLinkView(file, share)
            else -> hideShareLinkView()
        }
    }

    private fun showDropBoxShareLinkView(file: File) {
        shareLinkContainer.isVisible = true
        shareLinkDivider.isVisible = true
        shareLinkContainer.setup(file)
    }

    private fun showShareLinkView(file: File, share: Share?) {
        shareLinkContainer.isVisible = true
        shareLinkDivider.isVisible = true
        shareLinkContainer.setup(
            file = file,
            shareLink = share?.link,
            onTitleClicked = { newShareLink, currentFileId ->
                shareLink = newShareLink
                val (permissionsGroup, currentPermission) = selectPermissions(
                    isFolder = file.isFolder(),
                    isOnlyOffice = file.onlyoffice,
                    shareLinkExist = newShareLink != null,
                )
                findNavController().navigate(
                    FileDetailsFragmentDirections.actionFileDetailsFragmentToSelectPermissionBottomSheetDialog(
                        currentFileId = currentFileId,
                        currentPermission = currentPermission,
                        permissionsGroup = permissionsGroup,
                    )
                )
            },
            onSettingsClicked = { newShareLink, currentFile ->
                shareLink = newShareLink
                findNavController().navigate(
                    FileDetailsFragmentDirections.actionFileDetailsFragmentToFileShareLinkSettings(
                        fileId = currentFile.id,
                        driveId = currentFile.driveId,
                        shareLink = newShareLink,
                        isOnlyOfficeFile = currentFile.onlyoffice,
                        isFolder = currentFile.isFolder(),
                    )
                )
            })
    }

    private fun hideShareLinkView() {
        shareLinkContainer.isGone = true
        shareLinkDivider.isGone = true
    }

    private fun setupCategoriesContainer(fileId: Int?, categories: List<Category>) {
        val rights = DriveInfosController.getCategoryRights()
        if (fileId?.isPositive() == true && rights?.canReadCategoryOnFile == true) {
            categoriesDivider.isVisible = true
            categoriesContainer.apply {
                isVisible = true
                setup(categories, rights.canPutCategoryOnFile, layoutInflater, onClicked = {
                    runCatching {
                        findNavController().navigate(
                            FileDetailsFragmentDirections.actionFileDetailsFragmentToSelectCategoriesFragment(
                                fileId = fileId,
                                categoriesUsageMode = CategoriesUsageMode.MANAGED_CATEGORIES,
                            )
                        )
                    }
                })
            }
        } else {
            categoriesDivider.isGone = true
            categoriesContainer.isGone = true
        }
    }

    private fun displayUsersAvatars(file: File) {
        val userIds = if (file.users.isEmpty()) arrayListOf(file.createdBy) else ArrayList(file.users)
        val userList = DriveInfosController.getUsers(userIds = userIds)
        if (userList.isEmpty()) {
            users.isGone = true
            usersDivider.isGone = true
        } else {
            users.isVisible = true
            usersDivider.isVisible = true
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
            owner.isVisible = true
            ownerAvatar.loadAvatar(this)
            ownerValue.text = displayName
        }
    }

    private fun createShareLink(currentFile: File) {
        mainViewModel.postFileShareLink(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                shareLinkContainer.update(apiResponse.data)
            } else {
                requireActivity().showSnackbar(getString(R.string.errorShareLink))
            }
        }
    }

    private fun deleteShareLink(currentFile: File) {
        mainViewModel.deleteFileShareLink(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            val success = apiResponse.data == true
            if (success) {
                shareLinkContainer.update(null)
            } else {
                requireActivity().showSnackbar(apiResponse.translateError())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireParentFragment().addCommentButton.isGone = true
    }

    companion object {
        const val MAX_DISPLAYED_USERS = 4

        fun isPublicPermission(permission: Permission?): Boolean {
            return when (permission) {
                is ShareLink.ShareLinkFilePermission -> permission == ShareLink.ShareLinkFilePermission.PUBLIC
                is ShareLink.ShareLinkFolderPermission -> permission == ShareLink.ShareLinkFolderPermission.PUBLIC
                is ShareLink.ShareLinkDocumentPermission -> permission == ShareLink.ShareLinkDocumentPermission.PUBLIC
                else -> false
            }
        }

        fun selectPermissions(
            isFolder: Boolean,
            isOnlyOffice: Boolean,
            shareLinkExist: Boolean
        ): Pair<SelectPermissionBottomSheetDialog.PermissionsGroup, Permission> {
            val permissionsGroup: SelectPermissionBottomSheetDialog.PermissionsGroup
            val currentPermission = when {
                isFolder -> {
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_FOLDER_SETTINGS
                    if (shareLinkExist) ShareLink.ShareLinkFolderPermission.PUBLIC
                    else ShareLink.ShareLinkFolderPermission.RESTRICTED
                }
                isOnlyOffice -> {
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_DOCUMENT_SETTINGS
                    if (shareLinkExist) ShareLink.ShareLinkDocumentPermission.PUBLIC
                    else ShareLink.ShareLinkDocumentPermission.RESTRICTED
                }
                else -> {
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_FILE_SETTINGS
                    if (shareLinkExist) ShareLink.ShareLinkFilePermission.PUBLIC
                    else ShareLink.ShareLinkFilePermission.RESTRICTED
                }
            }
            return Pair(permissionsGroup, currentPermission)
        }
    }
}
