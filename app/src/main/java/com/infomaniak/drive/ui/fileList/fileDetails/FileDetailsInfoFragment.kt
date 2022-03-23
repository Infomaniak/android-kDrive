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
import com.infomaniak.drive.data.cache.FileController
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

class FileDetailsInfoFragment : FileDetailsSubFragment() {

    private lateinit var file: File
    private var shareLink: ShareLink? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_file_details_infos, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileDetailsViewModel.currentFile.observe(viewLifecycleOwner) { file ->

            this.file = file

            setupShareLink(fileDetailsViewModel.currentFileShare.value)
            setupCategoriesContainer(file.getCategories())
            displayUsersAvatars()
            setupShareButton()
            setupPathLocationButton()

            if (file.createdAt.isPositive()) {
                addedDateValue.text = file.getCreatedAt().format(ShareLinkContainerView.formatFullDate)
                addedDate.isVisible = true
            }

            if (file.fileCreatedAt.isPositive()) {
                creationDateValue.text = file.getFileCreatedAt().format(ShareLinkContainerView.formatFullDate)
                creationDate.isVisible = true
            }

            displayFileOwner()

            if (file.path.isNotBlank()) setPath(file.path)

            if (file.isFolder()) displayFolderContentCount(file)

            file.sizeWithVersions?.let {
                totalSizeValue.text = Formatter.formatFileSize(context, it)
                totalSize.isVisible = true
            }
            file.size?.let {
                originalSizeValue.text = Formatter.formatFileSize(context, it)
                originalSize.isVisible = true
            }
        }

        fileDetailsViewModel.currentFileShare.observe(viewLifecycleOwner) { share ->
            setPath(share.path)
            setupShareLink(share)
        }

        setBackActionHandlers()
    }

    private fun displayFolderContentCount(folder: File) {
        fileDetailsViewModel.getFileCounts(folder).observe(viewLifecycleOwner) { (files, count, folders) ->
            with(resources) {
                fileCountValue.text = when {
                    count == 0 -> getString(R.string.fileDetailsInfoEmptyFolder)
                    files == 0 && folders != 0 -> getQuantityString(R.plurals.fileDetailsInfoFolder, folders, folders)
                    files != 0 && folders == 0 -> getQuantityString(R.plurals.fileDetailsInfoFile, files, files)
                    else -> {
                        val folderText = getQuantityString(R.plurals.fileDetailsInfoFolder, folders, folders)
                        val fileText = getQuantityString(R.plurals.fileDetailsInfoFile, files, files)
                        getString(R.string.fileDetailsInfoFolderContentTemplate, folderText, fileText)
                    }
                }
            }

            fileCount.isVisible = true
        }
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SHARE_LINK_ACCESS_NAV_KEY) { bundle ->
            fileDetailsViewModel.currentFile.value?.let {
                file = it
                val permission = bundle.getParcelable<Permission>(SelectPermissionBottomSheetDialog.PERMISSION_BUNDLE_KEY)
                val isPublic = isPublicPermission(permission)
                if (isPublic && shareLink == null) {
                    createShareLink()
                } else if (!isPublic && shareLink != null) {
                    deleteShareLink()
                }
            }
        }

        getBackNavigationResult<List<Int>>(SelectCategoriesFragment.SELECT_CATEGORIES_NAV_KEY) { ids ->
            fileDetailsViewModel.currentFile.value?.let {
                file = it
                setupCategoriesContainer(DriveInfosController.getCurrentDriveCategoriesFromIds(ids.toTypedArray()))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setPath(path: String) {
        val drive = DriveInfosController.getDrives(AccountUtils.currentUserId, driveId = file.driveId).first()
        driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
        pathValue.text = "${drive.name}${path.substringBeforeLast("/")}"
        pathView.isVisible = true
    }

    private fun setupShareButton() {
        if (file.rights?.share == true) {
            shareButton.isVisible = true
            shareButton.setOnClickListener {
                parentFragment?.safeNavigate(
                    FileDetailsFragmentDirections.actionFileDetailsFragmentToFileShareDetailsFragment(fileId = file.id)
                )
            }
        } else {
            shareButton.isGone = true
        }
    }

    private fun setupPathLocationButton() {
        FileController.getParentFile(this.file.id)?.let { folder ->
            pathLocationButton.isVisible = true
            pathLocationButton.setOnClickListener { navigateToParentFolder(folder, mainViewModel) }
        }
    }

    private fun setupShareLink(share: Share?) {
        when {
            file.isDropBox() -> setupDropBoxShareLink()
            file.rights?.canBecomeLink == true || file.shareLink?.isNotBlank() == true -> setupNormalShareLink(share)
            else -> hideShareLinkView()
        }
    }

    private fun setupDropBoxShareLink() {
        showShareLinkView()
        shareLinkContainer.setup(file)
    }

    private fun setupNormalShareLink(share: Share?) {
        showShareLinkView()
        shareLinkContainer.setup(
            file = file,
            shareLink = share?.link,
            onTitleClicked = { newShareLink -> handleOnShareLinkTitleClicked(newShareLink) },
            onSettingsClicked = { newShareLink -> handleOnShareLinkSettingsClicked(newShareLink) })
    }

    private fun handleOnShareLinkTitleClicked(newShareLink: ShareLink?) {
        shareLink = newShareLink
        val (permissionsGroup, currentPermission) = selectPermissions(
            isFolder = file.isFolder(),
            isOnlyOffice = file.onlyoffice,
            shareLinkExist = newShareLink != null,
        )
        findNavController().navigate(
            FileDetailsFragmentDirections.actionFileDetailsFragmentToSelectPermissionBottomSheetDialog(
                currentFileId = file.id,
                currentPermission = currentPermission,
                permissionsGroup = permissionsGroup,
            )
        )
    }

    private fun handleOnShareLinkSettingsClicked(newShareLink: ShareLink) {
        shareLink = newShareLink
        findNavController().navigate(
            FileDetailsFragmentDirections.actionFileDetailsFragmentToFileShareLinkSettings(
                fileId = file.id,
                driveId = file.driveId,
                shareLink = newShareLink,
                isOnlyOfficeFile = file.onlyoffice,
                isFolder = file.isFolder(),
            )
        )
    }

    private fun showShareLinkView() {
        shareLinkContainer.isVisible = true
        shareLinkDivider.isVisible = true
    }

    private fun hideShareLinkView() {
        shareLinkContainer.isGone = true
        shareLinkDivider.isGone = true
    }

    private fun setupCategoriesContainer(categories: List<Category>) {
        val rights = DriveInfosController.getCategoryRights()

        if (file.id.isPositive() && rights.canReadCategoryOnFile) {
            categoriesDivider.isVisible = true
            categoriesContainer.apply {
                isVisible = true
                setup(
                    categories = categories,
                    canPutCategoryOnFile = !file.isDisabled() && rights.canPutCategoryOnFile,
                    layoutInflater = layoutInflater,
                    onClicked = { onCategoriesClicked(file.id) },
                )
            }

        } else {
            categoriesDivider.isGone = true
            categoriesContainer.isGone = true
        }
    }

    private fun onCategoriesClicked(fileId: Int) {
        runCatching {
            findNavController().navigate(
                FileDetailsFragmentDirections.actionFileDetailsFragmentToSelectCategoriesFragment(
                    fileId = fileId, categoriesUsageMode = CategoriesUsageMode.MANAGED_CATEGORIES
                )
            )
        }
    }

    private fun displayUsersAvatars() {
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

    private fun displayFileOwner() {
        val userList = DriveInfosController.getUsers(arrayListOf(file.createdBy))
        userList.firstOrNull()?.apply {
            owner.isVisible = true
            ownerAvatar.loadAvatar(this)
            ownerValue.text = displayName
        }
    }

    private fun createShareLink() {
        mainViewModel.postFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                shareLinkContainer.update(apiResponse.data)
            } else {
                requireActivity().showSnackbar(getString(R.string.errorShareLink))
            }
        }
    }

    private fun deleteShareLink() {
        mainViewModel.deleteFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
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
