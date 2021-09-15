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
    private lateinit var allUserList: List<DriveUser>
    private lateinit var allTeams: List<Team>

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_file_share_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val currentFile = navigationArgs.file.also { fileShareViewModel.currentFile.value = it }
        allUserList = AccountUtils.getCurrentDrive().getDriveUsers()
        allTeams = DriveInfosController.getTeams(AccountUtils.getCurrentDrive()!!)

        fileShareViewModel.availableShareableItems.value = ArrayList(allUserList)
        availableShareableItemsAdapter =
            userAutoCompleteTextView.setupAvailableShareableItems(
                context = requireContext(),
                itemList = allUserList
            ) { selectedElement ->
                userAutoCompleteTextView.setText("")
                openAddUserDialog(selectedElement)
            }
        availableShareableItemsAdapter.notShareableUserIds.addAll(currentFile.users)

        fileShareCollapsingToolbarLayout.title = getString(
            if (currentFile.type == File.Type.FOLDER.value) R.string.fileShareDetailsFolderTitle else R.string.fileShareDetailsFileTitle,
            currentFile.name
        )

        sharedUsersTitle.visibility = GONE
        setupShareLinkContainer(currentFile, null)
        refreshUi()

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
                        availableShareableItemsAdapter.removeFromNotShareables(shareableItem)
                    } else {
                        sharedItemsAdapter.updateItemPermission(shareableItem, permission as Shareable.ShareablePermission)
                    }
                }
            }
        }

        getBackNavigationResult<ShareableItems>(SHARE_SELECTION_KEY) { (users, emails, teams, invitations) ->
            sharedItemsAdapter.putAll(ArrayList(invitations + teams + users))
            refreshUi()
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

    private fun refreshUi() {
        fileShareViewModel.currentFile.value?.let { file ->
            sharedItemsAdapter = SharedItemsAdapter(file) { shareable -> openSelectPermissionDialog(shareable) }
            sharedUsersRecyclerView.adapter = sharedItemsAdapter

            mainViewModel.getFileShare(file.id).observe(viewLifecycleOwner) { (_, data) ->
                data?.let { share ->
                    val itemList = if (share.canUseTeam) allUserList + allTeams else allUserList
                    fileShareViewModel.availableShareableItems.value = ArrayList(itemList)

                    share.teams.sort()
                    availableShareableItemsAdapter.apply {
                        fileShareViewModel.availableShareableItems.value?.let { setAll(it) }
                        notShareableUserIds = ArrayList(share.users.map { it.id } + share.invitations.map { it.userId })
                        notShareableEmails = ArrayList(share.invitations.map { invitation -> invitation.email })
                        notShareableTeamIds = ArrayList(share.teams.map { team -> team.id })
                    }

                    sharedUsersTitle.visibility = VISIBLE
                    sharedItemsAdapter.setAll(ArrayList(share.invitations + share.teams + share.users))
                    setupShareLinkContainer(file, share.link)
                }
            }
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
                    shareLinkSwitched(isEnabled, file) { success ->
                        val isChecked = if (success) null else !isEnabled
                        shareLinkContainer.toggleSwitchingApproval(true, isChecked)
                    }
                }
            )
        }
    }

    private fun shareLinkSwitched(isEnabled: Boolean, file: File, onApiResponse: (success: Boolean) -> Unit) {
        mainViewModel.apply {
            if (isEnabled) {
                postFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
                    onApiResponse(apiResponse.isSuccess())
                    if (apiResponse.isSuccess()) {
                        shareLinkContainer.update(apiResponse.data)
                    } else {
                        requireActivity().showSnackbar(R.string.errorShareLink)
                    }
                }
            } else {
                deleteFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
                    onApiResponse(apiResponse.isSuccess())
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

    private fun openAddUserDialog(element: Shareable) {
        safeNavigate(
            FileShareDetailsFragmentDirections.actionFileShareDetailsFragmentToFileShareAddUserDialog(
                sharedItem = element,
                notShareableUserIds = availableShareableItemsAdapter.notShareableUserIds.toIntArray(),
                notShareableEmails = availableShareableItemsAdapter.notShareableEmails.toTypedArray(),
                notShareableTeamIds = availableShareableItemsAdapter.notShareableTeamIds.toIntArray()
            )
        )
    }
}