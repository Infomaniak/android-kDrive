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
package com.infomaniak.drive.ui.fileList.fileShare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.core.view.isGone
import androidx.core.view.isVisible
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
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSION_BUNDLE_KEY
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.SHAREABLE_BUNDLE_KEY
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.PermissionsGroup
import com.infomaniak.drive.ui.fileList.fileDetails.FileDetailsInfoFragment
import com.infomaniak.drive.ui.fileList.fileShare.FileShareAddUserDialog.Companion.SHARE_SELECTION_KEY
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_share_details.*

class FileShareDetailsFragment : Fragment() {

    private val fileShareViewModel: FileShareViewModel by navGraphViewModels(R.id.fileShareDetailsFragment)
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: FileShareDetailsFragmentArgs by navArgs()

    private lateinit var file: File

    private lateinit var availableShareableItemsAdapter: AvailableShareableItemsAdapter
    private lateinit var sharedItemsAdapter: SharedItemsAdapter

    private lateinit var allUserList: List<DriveUser>
    private lateinit var allTeams: List<Team>
    private var shareLink: ShareLink? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_file_share_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileShareViewModel.fetchCurrentFile(navigationArgs.fileId).observe(viewLifecycleOwner) { currentFile ->
            file = currentFile?.also {
                fileShareViewModel.currentFile.value = it
            } ?: run {
                findNavController().popBackStack()
                return@observe
            }

            setAvailableShareableItems()
            setToolbarTitle()
            sharedUsersTitle.isGone = true
            setupShareLink()
            refreshUi()
            setBackActionHandlers()
            setBackPressedHandlers()
        }
    }

    private fun setAvailableShareableItems() {
        allUserList = AccountUtils.getCurrentDrive().getDriveUsers()
        allTeams = DriveInfosController.getTeams(AccountUtils.getCurrentDrive()!!)

        fileShareViewModel.availableShareableItems.value = ArrayList(allUserList)
        availableShareableItemsAdapter = userAutoCompleteTextView.setupAvailableShareableItems(
            context = requireContext(),
            itemList = allUserList,
            onDataPassed = { selectedElement ->
                userAutoCompleteTextView.setText("")
                openAddUserDialog(selectedElement)
            },
        )
        availableShareableItemsAdapter.notShareableUserIds.addAll(file.users)
    }

    private fun setToolbarTitle() {
        fileShareCollapsingToolbarLayout.title = getString(
            if (file.type == File.Type.FOLDER.value) {
                R.string.fileShareDetailsFolderTitle
            } else {
                R.string.fileShareDetailsFileTitle
            },
            file.name,
        )
    }

    private fun refreshUi() {
        sharedItemsAdapter = SharedItemsAdapter(file) { shareable -> openSelectPermissionDialog(shareable) }
        sharedUsersRecyclerView.adapter = sharedItemsAdapter

        mainViewModel.getFileShare(file.id).observe(viewLifecycleOwner) { (_, data) ->
            data?.let { share ->
                val itemList = if (file.rights?.canUseTeam == true) allUserList + allTeams else allUserList
                fileShareViewModel.availableShareableItems.value = ArrayList(itemList)

                share.teams.sort()
                availableShareableItemsAdapter.apply {
                    fileShareViewModel.availableShareableItems.value?.let { setAll(it) }
                    notShareableUserIds = ArrayList(share.users.map { it.id } + share.invitations.map { it.userId })
                    notShareableEmails = ArrayList(share.invitations.map { invitation -> invitation.email })
                    notShareableTeamIds = ArrayList(share.teams.map { team -> team.id })
                }

                sharedUsersTitle.isVisible = true
                sharedItemsAdapter.setAll(ArrayList(share.invitations + share.teams + share.users))
                setupShareLink(share.link)
            }
        }
    }

    private fun setBackActionHandlers() {
        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SHARE_LINK_ACCESS_NAV_KEY) { bundle ->
            fileShareViewModel.currentFile.value?.let { currentFile ->
                file = currentFile
                val permission = bundle.getParcelable<Permission>(PERMISSION_BUNDLE_KEY)
                val isPublic = FileDetailsInfoFragment.isPublicPermission(permission)
                if (isPublic && shareLink == null) {
                    createShareLink()
                } else if (!isPublic && shareLink != null) {
                    deleteShareLink()
                }
            }
        }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.UPDATE_USERS_RIGHTS_NAV_KEY) { bundle ->
            fileShareViewModel.currentFile.value?.let { currentFile ->
                file = currentFile
                with(bundle) {
                    val permission = getParcelable<Permission>(PERMISSION_BUNDLE_KEY)
                    val shareable = getParcelable<Shareable>(SHAREABLE_BUNDLE_KEY)
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
        }

        getBackNavigationResult<ShareableItems>(SHARE_SELECTION_KEY) { (invitations, teams, users) ->
            fileShareViewModel.currentFile.value?.let { currentFile ->
                file = currentFile
                sharedItemsAdapter.putAll(ArrayList(invitations + teams + users))
                refreshUi()
            }
        }
    }

    private fun setBackPressedHandlers() {
        toolbar.setNavigationOnClickListener { onBackPressed() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBackPressed() }
        closeButton.setOnClickListener { onBackPressed() }
    }

    private fun onBackPressed() {
        view?.hideKeyboard()
        Utils.ignoreCreateFolderBackStack(findNavController(), navigationArgs.ignoreCreateFolderStack)
    }

    private fun setupShareLink(shareLink: ShareLink? = null) {
        when {
            file.isDropBox() -> setupDropBoxShareLink()
            file.rights?.canBecomeShareLink == true || file.sharelink?.url?.isNotBlank() == true -> setupNormalShareLink(shareLink)
            else -> hideShareLinkView()
        }
    }

    private fun setupDropBoxShareLink() {
        showShareLinkView()
        shareLinkContainer.setup(file)
    }

    private fun setupNormalShareLink(shareLink: ShareLink?) {
        showShareLinkView()
        shareLinkContainer.setup(
            file = file,
            shareLink = shareLink,
            onTitleClicked = { newShareLink -> handleOnShareLinkTitleClicked(newShareLink) },
            onSettingsClicked = { newShareLink -> handleOnShareLinkSettingsClicked(newShareLink) })
    }

    private fun handleOnShareLinkTitleClicked(newShareLink: ShareLink?) {
        shareLink = newShareLink
        val (permissionsGroup, currentPermission) = FileDetailsInfoFragment.selectPermissions(
            isFolder = file.isFolder(),
            isOnlyOffice = file.onlyoffice,
            shareLinkExist = shareLink != null,
        )
        safeNavigate(
            FileShareDetailsFragmentDirections.actionFileShareDetailsFragmentToSelectPermissionBottomSheetDialog(
                currentFileId = file.id,
                currentPermission = currentPermission,
                permissionsGroup = permissionsGroup,
            )
        )
    }

    private fun handleOnShareLinkSettingsClicked(newShareLink: ShareLink) {
        shareLink = newShareLink
        safeNavigate(
            FileShareDetailsFragmentDirections.actionFileShareDetailsFragmentToFileShareLinkSettings(
                fileId = file.id,
                driveId = file.driveId,
                shareLink = newShareLink,
                isOnlyOfficeFile = file.onlyoffice,
                isFolder = file.isFolder(),
            )
        )
    }

    private fun showShareLinkView() {
        shareLinkLayout.isVisible = true
    }

    private fun hideShareLinkView() {
        shareLinkLayout.isGone = true
    }

    private fun createShareLink() {
        mainViewModel.postFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                shareLinkContainer.update(apiResponse.data)
            } else {
                showSnackbar(R.string.errorShareLink)
            }
        }
    }

    private fun deleteShareLink() {
        mainViewModel.deleteFileShareLink(file).observe(viewLifecycleOwner) { apiResponse ->
            val success = apiResponse.data == true
            if (success) {
                shareLinkContainer.update(null)
            } else {
                showSnackbar(apiResponse.translateError())
            }
        }
    }

    private fun openSelectPermissionDialog(shareable: Shareable) {

        val permissionsGroup = when {
            shareable is Invitation || (shareable is DriveUser && shareable.isExternalUser()) -> PermissionsGroup.EXTERNAL_USERS_RIGHTS
            else -> PermissionsGroup.USERS_RIGHTS
        }

        safeNavigate(
            FileShareDetailsFragmentDirections.actionFileShareDetailsFragmentToSelectPermissionBottomSheetDialog(
                currentShareable = shareable,
                currentFileId = file.id,
                currentPermission = shareable.getFilePermission(),
                permissionsGroup = permissionsGroup,
            )
        )
    }

    private fun openAddUserDialog(element: Shareable) = with(availableShareableItemsAdapter) {
        safeNavigate(
            FileShareDetailsFragmentDirections.actionFileShareDetailsFragmentToFileShareAddUserDialog(
                sharedItem = element,
                notShareableUserIds = notShareableUserIds.toIntArray(),
                notShareableEmails = notShareableEmails.toTypedArray(),
                notShareableTeamIds = notShareableTeamIds.toIntArray(),
            )
        )
    }
}
