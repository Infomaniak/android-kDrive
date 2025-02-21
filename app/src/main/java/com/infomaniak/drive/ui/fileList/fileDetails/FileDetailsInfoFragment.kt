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
import com.infomaniak.core.utils.format
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.databinding.FragmentFileDetailsInfosBinding
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.ShareLinkContainerView
import com.infomaniak.drive.views.UserAvatarView
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

class FileDetailsInfoFragment : FileDetailsSubFragment() {

    private var binding: FragmentFileDetailsInfosBinding by safeBinding()

    private lateinit var file: File
    private var shareLink: ShareLink? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentFileDetailsInfosBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        fileDetailsViewModel.currentFile.observe(viewLifecycleOwner) { file ->

            this@FileDetailsInfoFragment.file = file

            file.shareLink?.let { setupShareLink() }
            setupCategoriesContainer(file.getCategories())
            displayUsersAvatars()
            setupShareButton()
            setupPathLocationButton()

            if (file.addedAt.isPositive()) {
                addedDateValue.text = file.getAddedAt().format(ShareLinkContainerView.formatFullDate)
                addedDate.isVisible = true
            }

            if (file.createdAt.isPositive()) {
                creationDateValue.text = file.getFileCreatedAt().format(ShareLinkContainerView.formatFullDate)
                creationDate.isVisible = true
            }

            displayFileOwner()

            if (file.path.isNotBlank()) setPath(file.path)

            if (file.isFolder()) displayFolderContentCount(file)

            file.version?.let {
                totalSizeValue.text = Formatter.formatFileSize(context, it.totalSize)
                totalSize.isVisible = true
            }
            file.size?.let {
                originalSizeValue.text = Formatter.formatFileSize(context, it)
                originalSize.isVisible = true
            }
        }

        setBackActionHandlers()
    }

    private fun displayFolderContentCount(folder: File) = with(binding) {
        fileDetailsViewModel.getFileCounts(folder).observe(viewLifecycleOwner) { (files, count, folders) ->
            val folderText = resources.getQuantityString(R.plurals.fileDetailsInfoFolder, folders, folders)
            val fileText = resources.getQuantityString(R.plurals.fileDetailsInfoFile, files, files)
            fileCountValue.text = when {
                count == 0 -> getString(R.string.fileDetailsInfoEmptyFolder)
                files == 0 && folders != 0 -> folderText
                files != 0 && folders == 0 -> fileText
                else -> {
                    getString(R.string.fileDetailsInfoFolderContentTemplate, folderText, fileText)
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
                setupCategoriesContainer(DriveInfosController.getCategoriesFromIds(file.driveId, ids.toTypedArray()))
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setPath(path: String) = with(binding) {
        val drive = DriveInfosController.getDrive(AccountUtils.currentUserId, driveId = file.driveId)!!
        driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
        pathValue.text = "${drive.name}$path"
        pathView.isVisible = true
    }

    private fun setupShareButton() = with(binding) {
        if (file.rights?.canShare == true) {
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
            binding.pathLocationButton.apply {
                isVisible = true
                setOnClickListener { navigateToParentFolder(folder.id, mainViewModel) }
            }
        }
    }

    private fun setupShareLink() {
        when {
            file.isDropBox() -> setupDropBoxShareLink()
            file.rights?.canBecomeShareLink == true || file.shareLink?.url?.isNotBlank() == true -> setupNormalShareLink()
            else -> hideShareLinkView()
        }
    }

    private fun setupDropBoxShareLink() {
        showShareLinkView()
        binding.shareLinkContainer.setup(file)
    }

    private fun setupNormalShareLink() {
        showShareLinkView()
        binding.shareLinkContainer.setup(
            file = file,
            shareLink = file.shareLink,
            onTitleClicked = { newShareLink -> handleOnShareLinkTitleClicked(newShareLink) },
            onSettingsClicked = { newShareLink -> handleOnShareLinkSettingsClicked(newShareLink) })
    }

    private fun handleOnShareLinkTitleClicked(newShareLink: ShareLink?) {
        shareLink = newShareLink
        val (permissionsGroup, currentPermission) = selectPermissions(
            isFolder = file.isFolder(),
            isOnlyOffice = file.hasOnlyoffice,
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
                isOnlyOfficeFile = file.hasOnlyoffice,
                isFolder = file.isFolder(),
            )
        )
    }

    private fun showShareLinkView() = with(binding) {
        shareLinkContainer.isVisible = true
        shareLinkDivider.isVisible = true
    }

    private fun hideShareLinkView() = with(binding) {
        shareLinkContainer.isGone = true
        shareLinkDivider.isGone = true
    }

    private fun setupCategoriesContainer(categories: List<Category>) = with(binding) {
        val rights = DriveInfosController.getCategoryRights(file.driveId)

        if (file.id.isPositive() && rights.canReadOnFile) {
            categoriesDivider.isVisible = true
            categoriesContainer.apply {
                isVisible = true
                setup(
                    categories = categories,
                    canPutCategoryOnFile = !file.isDisabled() && rights.canPutOnFile,
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
                    filesIds = intArrayOf(fileId),
                    categoriesUsageMode = CategoriesUsageMode.MANAGED_CATEGORIES,
                    userDrive = UserDrive(driveId = file.driveId)
                )
            )
        }
    }

    private fun displayUsersAvatars() = with(binding) {
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

    private fun displayFileOwner() = with(binding) {
        val userList = DriveInfosController.getUsers(arrayListOf(file.createdBy))
        userList.firstOrNull()?.apply {
            owner.isVisible = true
            ownerAvatar.loadAvatar(this)
            ownerValue.text = displayName
        }
    }

    private fun createShareLink() {
        mainViewModel.createShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                binding.shareLinkContainer.update(apiResponse.data)
            } else {
                showSnackbar(R.string.errorShareLink)
            }
        }
    }

    private fun deleteShareLink() {
        mainViewModel.deleteFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
            val success = apiResponse.data == true
            if (success) {
                binding.shareLinkContainer.update(null)
            } else {
                showSnackbar(apiResponse.translateError())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        addCommentButton.isGone = true
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
