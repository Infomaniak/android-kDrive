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
import com.infomaniak.core.myksuite.ui.screens.KSuiteApp
import com.infomaniak.core.myksuite.ui.views.MyKSuiteUpgradeBottomSheetDialogArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.databinding.FragmentFileShareDetailsBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSION_BUNDLE_KEY
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.SHAREABLE_BUNDLE_KEY
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.PermissionsGroup
import com.infomaniak.drive.ui.fileList.fileDetails.FileDetailsInfoFragment
import com.infomaniak.drive.ui.fileList.fileShare.FileShareAddUserDialog.Companion.SHARE_SELECTION_KEY
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.getBackNavigationResult
import com.infomaniak.lib.core.utils.hideKeyboard
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

class FileShareDetailsFragment : Fragment() {

    private var binding: FragmentFileShareDetailsBinding by safeBinding()

    private val fileShareViewModel: FileShareViewModel by navGraphViewModels(R.id.fileShareDetailsFragment)
    private val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: FileShareDetailsFragmentArgs by navArgs()

    private lateinit var file: File

    private lateinit var availableShareableItemsAdapter: AvailableShareableItemsAdapter
    private lateinit var sharedItemsAdapter: SharedItemsAdapter

    private lateinit var allUserList: List<DriveUser>
    private lateinit var allTeams: List<Team>
    private var shareLink: ShareLink? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentFileShareDetailsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fileShareViewModel.fetchCurrentFile(navigationArgs.fileId).observe(viewLifecycleOwner) { currentFile ->
            file = currentFile?.also {
                fileShareViewModel.initCurrentDriveLiveData(it.driveId)
                fileShareViewModel.currentFile.value = it
            } ?: run {
                findNavController().popBackStack()
                return@observe
            }

            setAvailableShareableItems()
            setToolbarTitle()
            binding.sharedUsersTitle.isGone = true
            setupShareLink(file.shareLink)
            refreshUi()
            setBackActionHandlers()
            setBackPressedHandlers()
        }
    }

    private fun setAvailableShareableItems() = with(binding) {
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
        availableShareableItemsAdapter.notShareableIds.addAll(file.users)
    }

    private fun setToolbarTitle() {
        binding.fileShareCollapsingToolbarLayout.title = getString(
            if (file.type == File.Type.DIRECTORY.value) {
                R.string.fileShareDetailsFolderTitle
            } else {
                R.string.fileShareDetailsFileTitle
            },
            file.name,
        )
    }

    private fun refreshUi() = with(binding) {
        // TODO Need refactor
        sharedItemsAdapter = SharedItemsAdapter(file) { shareable -> openSelectPermissionDialog(shareable) }
        sharedUsersRecyclerView.adapter = sharedItemsAdapter

        mainViewModel.getFileShare(file.id).observe(viewLifecycleOwner) { apiResponse ->
            apiResponse.data?.let { share ->
                val itemList = if (file.rights?.canUseTeam == true) allUserList + allTeams else allUserList
                fileShareViewModel.availableShareableItems.value = ArrayList(itemList)

                share.teams.sort()
                availableShareableItemsAdapter.apply {
                    fileShareViewModel.availableShareableItems.value?.let { setAll(it) }

                    val userIds = share.users.map { it.id } +
                            share.invitations.mapNotNull { it.user?.id } +
                            share.teams.map { team -> team.id }

                    notShareableIds = ArrayList(userIds)
                    notShareableEmails = ArrayList(share.invitations.map { invitation -> invitation.email })
                }

                sharedUsersTitle.isVisible = true
                sharedItemsAdapter.setAll(ArrayList(share.members))
            }
        }

        mainViewModel.getShareLink(file).observe(viewLifecycleOwner) {
            it?.data?.let(::setupShareLink)
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

        getBackNavigationResult<Boolean>(SHARE_SELECTION_KEY) {
            fileShareViewModel.currentFile.value?.let { currentFile ->
                file = currentFile
                refreshUi()
            }
        }

        observeFileDrive()
    }

    private fun setBackPressedHandlers() = with(binding) {
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
            file.rights?.canBecomeShareLink == true || file.shareLink?.url?.isNotBlank() == true -> setupNormalShareLink(shareLink)
            else -> hideShareLinkView()
        }
    }

    private fun setupDropBoxShareLink() {
        showShareLinkView()
        binding.shareLinkContainer.setup(file)
    }

    private fun setupNormalShareLink(shareLink: ShareLink?) {
        showShareLinkView()
        binding.shareLinkContainer.setup(
            file = file,
            shareLink = shareLink,
            onTitleClicked = { newShareLink -> handleOnShareLinkTitleClicked(newShareLink) },
            onSettingsClicked = { newShareLink -> handleOnShareLinkSettingsClicked(newShareLink) })
    }

    private fun handleOnShareLinkTitleClicked(newShareLink: ShareLink?) {
        shareLink = newShareLink
        val (permissionsGroup, currentPermission) = FileDetailsInfoFragment.selectPermissions(
            isFolder = file.isFolder(),
            isOnlyOffice = file.hasOnlyoffice,
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
                isOnlyOfficeFile = file.hasOnlyoffice,
                isFolder = file.isFolder(),
            )
        )
    }

    private fun showShareLinkView() {
        binding.shareLinkLayout.isVisible = true
    }

    private fun hideShareLinkView() {
        binding.shareLinkLayout.isGone = true
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

    private fun openSelectPermissionDialog(shareable: Shareable) {

        val permissionsGroup = when {
            shareable is Invitation || (shareable is DriveUser && shareable.isExternalUser) -> PermissionsGroup.EXTERNAL_USERS_RIGHTS
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
                notShareableIds = notShareableIds.toIntArray(),
                notShareableEmails = notShareableEmails.toTypedArray()
            )
        )
    }

    private fun observeFileDrive() {
        fileShareViewModel.currentDriveResult.observe(viewLifecycleOwner) { drive ->
            val hasShareLink = fileShareViewModel.currentFile.value?.shareLink != null
            val canCreateShareLink = drive.quotas.canCreateShareLink || hasShareLink

            binding.shareLinkContainer.showMyKSuitePlusChip(canCreateShareLink) {
                val args = MyKSuiteUpgradeBottomSheetDialogArgs(kSuiteApp = KSuiteApp.Drive)
                safeNavigate(R.id.myKSuiteUpgradeBottomSheet, args = args.toBundle())
            }
        }
    }
}
