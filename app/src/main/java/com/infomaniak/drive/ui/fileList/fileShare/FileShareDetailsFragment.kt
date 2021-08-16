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
package com.infomaniak.drive.ui.fileList.fileShare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSIONS_GROUP_BUNDLE_KEY
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSION_BUNDLE_KEY
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.SHAREABLE_BUNDLE_KEY
import com.infomaniak.drive.ui.fileList.fileShare.FileShareAddUserDialog.Companion.SHARE_SELECTION_KEY
import com.infomaniak.drive.utils.*
import kotlinx.android.synthetic.main.fragment_file_share_details.*

class FileShareDetailsFragment : Fragment() {
    private lateinit var availableShareableItemsAdapter: AvailableShareableItemsAdapter
    private lateinit var sharedItemsAdapter: SharedItemsAdapter
    private val fileShareViewModel: FileShareViewModel by navGraphViewModels(R.id.fileShareDetailsFragment)
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: FileShareDetailsFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_file_share_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val allUserList = AccountUtils.getCurrentDrive()?.users?.let { categories ->
            return@let DriveInfosController.getUsers(ArrayList(categories.drive + categories.account))
        } ?: listOf()

        fileShareViewModel.availableUsers.value = ArrayList(allUserList) // add available tags if in common
        availableShareableItemsAdapter =
            userAutoCompleteTextView.setupAvailableShareableItems(
                requireContext(),
                allUserList
            ) { selectedElement ->
                userAutoCompleteTextView.setText("")
                openAddUserDialog(selectedElement)
            }

        fileShareCollapsingToolbarLayout.title = getString(
            if (navigationArgs.fileType == File.Type.FOLDER.value) R.string.fileShareDetailsFolderTitle else R.string.fileShareDetailsFileTitle,
            navigationArgs.fileName
        )

        FileController.getFileById(navigationArgs.fileId)?.let { file ->
            sharedUsersTitle.visibility = GONE
            setupShareLinkContainer(file, null)
        }
        mainViewModel.getFileDetails(navigationArgs.fileId, UserDrive()).observe(viewLifecycleOwner) { fileDetails ->
            fileDetails?.let { file ->
                fileShareViewModel.currentFile.value = file
                availableShareableItemsAdapter.setAll(allUserList)
                availableShareableItemsAdapter.notShareableUsers.addAll(file.users.map { userId -> DriveUser(id = userId) })

                sharedItemsAdapter = SharedItemsAdapter(file) { shareable ->
                    openSelectPermissionDialog(shareable)
                }
                sharedUsersRecyclerView.adapter = sharedItemsAdapter
                mainViewModel.getFileShare(file.id).observe(viewLifecycleOwner) { (_, data) ->
                    data?.let { share ->
                        sharedUsersTitle.visibility = VISIBLE
                        availableShareableItemsAdapter.notShareableUsers.clear()
                        availableShareableItemsAdapter.notShareableUsers.addAll(share.users)
                        sharedItemsAdapter.setAll(ArrayList(share.users + share.invitations + share.tags))
                        setupShareLinkContainer(file, share.link)
                    }
                }
            }
        }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SELECT_PERMISSION_NAV_KEY) { bundle ->
            val permission = bundle.getParcelable<Permission>(PERMISSION_BUNDLE_KEY)
            val shareable = bundle.getParcelable<Shareable>(SHAREABLE_BUNDLE_KEY)
            val permissionsType =
                bundle.getParcelable<SelectPermissionBottomSheetDialog.PermissionsGroup>(PERMISSIONS_GROUP_BUNDLE_KEY)

            // Determine if we come back from users permission selection or share link office
            if (permissionsType == SelectPermissionBottomSheetDialog.PermissionsGroup.SHARE_LINK_OFFICE) {
                shareLinkContainer.officePermission = permission as ShareLink.OfficePermission
            } else {
                shareable?.let { shareableItem ->
                    if (permission == Shareable.ShareablePermission.DELETE) {
                        sharedItemsAdapter.removeItem(shareableItem)
                        if (shareableItem is DriveUser) availableShareableItemsAdapter.notShareableUsers.remove(shareableItem)
                    } else {
                        sharedItemsAdapter.updateItemPermission(shareableItem, permission as Shareable.ShareablePermission)
                    }
                }
            }
        }

        getBackNavigationResult<ShareableItems>(SHARE_SELECTION_KEY) { (users, _, tags, invitations) ->
            sharedItemsAdapter.putAll(ArrayList(users + tags + invitations))
            availableShareableItemsAdapter.notShareableUsers.addAll(users)
        }

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            onBackPressed()
        }
        closeButton.setOnClickListener {
            onBackPressed()
        }
    }

    private fun onBackPressed() {
        view?.hideKeyboard()
        Utils.ignoreCreateFolderBackStack(findNavController(), navigationArgs.ignoreCreateFolderStack)
    }

    private fun setupShareLinkContainer(file: File, shareLink: ShareLink?) {
        if (file.rights?.share == false) {
            shareLinkLayout.visibility = GONE
        } else {
            shareLinkLayout.visibility = VISIBLE
            shareLinkContainer.setup(shareLink = shareLink,
                file = file,
                onSwitchClicked = { isEnabled ->
                    shareLinkSwitched(isEnabled, file) {
                        shareLinkContainer.toggleSwitchingApproval(true)
                    }
                }
            )
        }
    }

    private fun shareLinkSwitched(isEnabled: Boolean, file: File, onApiResponse: () -> Unit) {
        mainViewModel.apply {
            if (isEnabled) {
                postFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
                    onApiResponse()
                    if (apiResponse.isSuccess()) {
                        shareLinkContainer.update(apiResponse.data)
                    } else {
                        requireActivity().showSnackbar(R.string.errorShareLink)
                    }
                }
            } else {
                deleteFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
                    onApiResponse()
                    if (apiResponse.data == true) {
                        shareLinkContainer.update(null)
                    } else {
                        requireActivity().showSnackbar(apiResponse.translateError())
                    }
                }
            }
        }
    }

    private fun openSelectPermissionDialog(shareable: Shareable) {
        val permissionsGroup = when {
            shareable is Invitation || (shareable is DriveUser && shareable.isExternalUser()) -> SelectPermissionBottomSheetDialog.PermissionsGroup.EXTERNAL_USERS_RIGHTS
            else -> SelectPermissionBottomSheetDialog.PermissionsGroup.USERS_RIGHTS
        }
        safeNavigate(
            FileShareDetailsFragmentDirections.actionFileShareDetailsFragmentToSelectPermissionBottomSheetDialog(
                currentShareable = shareable,
                currentFileId = fileShareViewModel.currentFile.value?.id!!,
                currentPermission = shareable.getFilePermission(),
                permissionsGroup = permissionsGroup
            )
        )
    }

    private fun openAddUserDialog(element: Any) {
        var sharedEmail: String? = null
        var sharedUserId: Int = -1
        when (element) {
            is String -> sharedEmail = element
            is Invitation -> sharedEmail = element.email
            is DriveUser -> sharedUserId = element.id
        }
        safeNavigate(
            FileShareDetailsFragmentDirections.actionFileShareDetailsFragmentToFileShareAddUserDialog(
                sharedEmail = sharedEmail,
                sharedUserId = sharedUserId,
                notShareableUsersIds = availableShareableItemsAdapter.notShareableUsers.map { it.id }.toIntArray()
            )
        )
    }
}