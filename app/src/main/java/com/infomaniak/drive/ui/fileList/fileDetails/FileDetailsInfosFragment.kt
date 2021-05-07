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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_file_details_infos, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        fileDetailsViewModel.currentFile.observe(viewLifecycleOwner) { currentFile ->
            setupShareLinkContainer(currentFile, null)
            displayUsersAvatars(currentFile)
            getShareDetails(currentFile)
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

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SELECT_PERMISSION_NAV_KEY) { bundle ->
            val permission = bundle.getParcelable<Permission>(SelectPermissionBottomSheetDialog.PERMISSION_BUNDLE_KEY)
            shareLinkContainer.officePermission = permission as ShareLink.OfficePermission
        }
    }

    private fun setupShareButton(currentFile: File) {
        if (currentFile.rights?.share == true) {
            shareButton.visibility = VISIBLE
            shareButton.setOnClickListener {
                safeNavigate(
                    FileDetailsFragmentDirections.actionFileDetailsFragmentToFileShareDetailsFragment(
                        fileId = currentFile.id,
                        fileName = currentFile.name,
                        fileType = currentFile.type
                    )
                )
            }
        } else {
            shareButton.visibility = GONE
        }
    }

    private fun setupShareLinkContainer(currentFile: File, share: Share?) {
        if (currentFile.rights?.share == true) {
            shareLinkContainer.visibility = VISIBLE
            shareDivider.visibility = VISIBLE
            shareLinkContainer.setup(shareLink = share?.link, file = currentFile, onSwitchClicked = { isEnabled ->
                mainViewModel.apply {
                    if (isEnabled) {
                        createShareLink(currentFile) {
                            shareLinkContainer.toggleSwitchingApproval(true)
                        }
                    }
                    else {
                        deleteShareLink(currentFile) {
                            shareLinkContainer.toggleSwitchingApproval(true)
                        }
                    }
                }
            })
        } else {
            shareLinkContainer.visibility = GONE
            shareDivider.visibility = GONE
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
            ownerAvatar.loadUrlWithoutToken(requireContext(), getUserAvatar(), R.drawable.ic_placeholder_avatar)
            ownerValue.text = displayName
        }
    }

    private fun getShareDetails(currentFile: File) {
        mainViewModel.getFileShare(currentFile.id).observe(viewLifecycleOwner) { shareResponse ->
            shareResponse.data?.let { share ->
                pathValue.text = share.path
                if (currentFile.rights?.share == true) {
                    setupShareLinkContainer(currentFile, share)
                }
            }
        }
    }

    private fun createShareLink(currentFile: File, onApiResponse: () -> Unit) {
        mainViewModel.postFileShareLink(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            onApiResponse()
            if (apiResponse.isSuccess()) {
                shareLinkContainer.update(apiResponse.data)
            } else {
                requireActivity().showSnackbar(getString(R.string.errorShareLink))
            }
        }
    }

    private fun deleteShareLink(currentFile: File, onApiResponse: () -> Unit) {
        onApiResponse()
        mainViewModel.deleteFileShareLink(currentFile).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.data == true) {
                shareLinkContainer.update(null)
            } else {
                requireActivity().showSnackbar(apiResponse.translateError())
            }
        }
    }

    override fun onResume() {
        super.onResume()
        requireParentFragment().addCommentButton.visibility = GONE
    }

    private companion object {
        const val MAX_DISPLAYED_USERS = 4
    }
}
