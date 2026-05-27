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

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.core.legacy.utils.CoilUtils.simpleImageLoader
import com.infomaniak.core.legacy.utils.SnackbarUtils
import com.infomaniak.core.legacy.utils.Utils.getDefaultAcceptedLanguage
import com.infomaniak.core.legacy.utils.UtilsUi.generateInitialsAvatarDrawable
import com.infomaniak.core.legacy.utils.UtilsUi.getBackgroundColorBasedOnId
import com.infomaniak.core.legacy.utils.getBackNavigationResult
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setBackNavigationResult
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackShareRightsEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCheckResult
import com.infomaniak.drive.data.models.Invitation
import com.infomaniak.drive.data.models.Share
import com.infomaniak.drive.data.models.Shareable
import com.infomaniak.drive.data.models.Shareable.ShareablePermission
import com.infomaniak.drive.data.models.Team
import com.infomaniak.drive.databinding.FragmentBottomSheetFileShareBinding
import com.infomaniak.drive.ui.selectPermission.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.ui.selectPermission.SelectPermissionBottomSheetDialog.Companion.PERMISSION_BUNDLE_KEY
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.getDriveUsers
import com.infomaniak.drive.utils.setupAvailableShareableItems
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

class FileShareAddUserDialog : FullScreenBottomSheetDialog() {

    private var binding: FragmentBottomSheetFileShareBinding by safeBinding()

    private lateinit var availableUsersAdapter: AvailableShareableItemsAdapter
    private val fileShareViewModel: FileShareViewModel by navGraphViewModels(R.id.fileShareDetailsFragment)
    private val navigationArgs: FileShareAddUserDialogArgs by navArgs()
    private val selectedItems: Share = Share()

    private var selectedPermission: ShareablePermission = ShareablePermission.READ
        set(value) {
            binding.filePermissionsIcon.setImageResource(value.icon)
            binding.filePermissionsValue.setText(value.translation)
            field = value
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetFileShareBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        availableUsersAdapter = userAutoCompleteTextView.setupAvailableShareableItems(
            context = requireContext(),
            itemList = fileShareViewModel.availableShareableItems.value ?: AccountUtils.getCurrentDrive().getDriveUsers(),
            notShareableIds = navigationArgs.notShareableIds.toMutableList() as ArrayList<Int>,
            notShareableEmails = navigationArgs.notShareableEmails.toMutableList() as ArrayList<String>,
        ) { element ->
            userAutoCompleteTextView.setText("")
            addToSharedElementList(element)
        }

        collapsingToolbarLayout.title = getString(
            if (fileShareViewModel.currentFile.value?.isFolder() == true) R.string.fileShareFolderTitle
            else R.string.fileShareFileTitle
        )

        addToSharedElementList(navigationArgs.sharedItem)
        filePermissions.setOnClickListener {
            safeNavigate(
                FileShareAddUserDialogDirections.actionFileShareAddUserDialogToSelectPermissionBottomSheetDialog(
                    currentPermission = selectedPermission,
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.FILE_SHARE_UPDATE
                )
            )
        }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.ADD_USERS_RIGHTS_NAV_KEY) { bundle ->
            selectedPermission = bundle.getParcelable(PERMISSION_BUNDLE_KEY)!!
        }

        shareButton.apply {
            initProgress(this@FileShareAddUserDialog)
            setOnClickListener {
                showProgressCatching()
                trackShareRightsEvent(MatomoName.InviteUser)
                checkShare(selectedPermission) { file, body ->
                    createShareAndCloseDialog(file, body)
                }
            }
        }
    }

    private fun addToSharedElementList(element: Shareable) = with(binding) {
        selectedItems.apply {
            when (element) {
                is Invitation -> {
                    invitations.add(element)
                    availableUsersAdapter.notShareableEmails.add(element.email)
                    createChip(element).setOnClickListener {
                        invitations.remove(element)
                        selectedItemsChipGroup.removeView(it)
                    }
                }
                is Team -> {
                    teams.add(element)
                    createChip(element).setOnClickListener {
                        teams.remove(element)
                        selectedItemsChipGroup.removeView(it)
                    }
                }
                is DriveUser -> {
                    driveUsers.add(element)
                    availableUsersAdapter.notShareableIds.add(element.id)
                    createChip(element).setOnClickListener {
                        driveUsers.remove(element)
                        availableUsersAdapter.notShareableIds.remove(element.id)
                        selectedItemsChipGroup.removeView(it)
                    }
                }
            }
        }
    }

    private fun createChip(item: Shareable): Chip {
        val chip = layoutInflater.inflate(R.layout.chip_shared_elements, null) as Chip

        fun computeDriveUser(driveUser: DriveUser) {
            chip.text = driveUser.displayName
            lifecycleScope.launch(Dispatchers.IO) {
                requireContext().apply {
                    val fallback = generateInitialsAvatarDrawable(
                        initials = driveUser.getInitials(),
                        background = getBackgroundColorBasedOnId(item.id)
                    )
                    val request = ImageRequest.Builder(this)
                        .data(driveUser.avatar)
                        .transformations(CircleCropTransformation())
                        .fallback(fallback)
                        .error(fallback)
                        .placeholder(R.drawable.ic_account)
                        .build()
                    simpleImageLoader.execute(request).drawable?.let {
                        withContext(Dispatchers.Main) {
                            chip.chipIcon = it
                        }
                    }
                }
            }
        }

        when (item) {
            is DriveUser -> computeDriveUser(driveUser = item)
            is Invitation -> chip.apply {
                text = item.email
                setChipIconResource(R.drawable.ic_circle_send)
            }
            is Team -> chip.apply {
                text = item.name
                setChipIconResource(R.drawable.ic_circle_team)
                chipIconTint = ColorStateList.valueOf(item.getParsedColor())
            }
        }

        binding.selectedItemsChipGroup.addView(chip)
        return chip
    }

    private fun createShareAndCloseDialog(file: File, body: MutableMap<String, Serializable>) {
        fileShareViewModel.postFileShare(file, body).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                setBackNavigationResult(SHARE_SELECTION_KEY, true)
            } else {
                SnackbarUtils.showSnackbar(requireView(), apiResponse.translateError())
            }
            binding.shareButton.hideProgressCatching(R.string.buttonShare)
        }
    }

    private fun checkShare(
        newPermission: ShareablePermission,
        onCheckApproved: (file: File, body: MutableMap<String, Serializable>) -> Unit,
    ) = with(binding) {

        fileShareViewModel.currentFile.value?.let { file ->
            val body = mutableMapOf(
                "emails" to ArrayList(selectedItems.invitations.map { it.email }),
                "user_ids" to ArrayList(selectedItems.driveUsers.map { it.id }),
                "team_ids" to ArrayList(selectedItems.teams.map { it.id }),
                "right" to newPermission.apiValue,
                "lang" to getDefaultAcceptedLanguage(),
                "message" to shareMessage.text.toString(),
            )

            fileShareViewModel.postFileShareCheck(file, body).observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    val conflictList = apiResponse.data?.filter { checkResult -> checkResult.isConflict }
                    if (conflictList.isNullOrEmpty()) {
                        onCheckApproved(file, body)
                    } else {
                        showConflictDialog(newPermission, ArrayList(conflictList)) {
                            onCheckApproved(file, body)
                        }
                    }
                } else {
                    SnackbarUtils.showSnackbar(requireView(), apiResponse.translateError())
                    shareButton.hideProgressCatching(R.string.buttonShare)
                }
            }
        }
    }

    private fun showConflictDialog(
        newPermission: ShareablePermission,
        checkResults: ArrayList<FileCheckResult>,
        onConflictApproved: () -> Unit,
    ) {
        val conflictedUsers = checkResults.filter { it.isConflict }

        val message: String? = when (conflictedUsers.size) {
            1 -> {
                fileShareViewModel.availableShareableItems.value?.find { item -> item is DriveUser && item.id == conflictedUsers.first().userId }
                    ?.let { user ->
                        getString(
                            R.string.sharedConflictDescription,
                            (user as DriveUser).displayName,
                            getString(user.getFilePermission().translation),
                            getString(newPermission.translation)
                        )
                    }
            }
            else -> {
                getString(R.string.sharedConflictManyUserDescription, newPermission.apiValue)
            }
        }

        MaterialAlertDialogBuilder(requireContext(), R.style.DialogStyle)
            .setTitle(getString(R.string.sharedConflictTitle))
            .setMessage(message)
            .setNegativeButton(R.string.buttonCancel) { _, _ ->
                dismiss()
            }
            .setPositiveButton(R.string.buttonShare) { _, _ ->
                onConflictApproved()
            }.show()
    }

    companion object {
        const val SHARE_SELECTION_KEY = "selection_dialog_dismissed"
    }
}
