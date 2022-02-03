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

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import coil.ImageLoader
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.android.material.chip.Chip
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog
import com.infomaniak.drive.ui.bottomSheetDialogs.SelectPermissionBottomSheetDialog.Companion.PERMISSION_BUNDLE_KEY
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.utils.UtilsUi.generateInitialsAvatarDrawable
import com.infomaniak.lib.core.utils.UtilsUi.getBackgroundColorBasedOnId
import com.infomaniak.lib.core.utils.UtilsUi.getInitials
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.fragment_bottom_sheet_file_share.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable
import java.util.*

class FileShareAddUserDialog : FullScreenBottomSheetDialog() {
    private lateinit var availableUsersAdapter: AvailableShareableItemsAdapter
    private val fileShareViewModel: FileShareViewModel by navGraphViewModels(R.id.fileShareDetailsFragment)
    private val navigationArgs: FileShareAddUserDialogArgs by navArgs()
    private val selectedItems: ShareableItems = ShareableItems()

    private var selectedPermission: Shareable.ShareablePermission = Shareable.ShareablePermission.READ
        set(value) {
            filePermissionsIcon.setImageResource(value.icon)
            filePermissionsValue.setText(value.translation)
            field = value
        }

    companion object {
        const val SHARE_SELECTION_KEY = "selection_dialog_dismissed"
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bottom_sheet_file_share, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        availableUsersAdapter = userAutoCompleteTextView.setupAvailableShareableItems(
            context = requireContext(),
            itemList = fileShareViewModel.availableShareableItems.value ?: AccountUtils.getCurrentDrive().getDriveUsers(),
            notShareableUserIds = navigationArgs.notShareableUserIds.toMutableList() as ArrayList<Int>,
            notShareableEmails = navigationArgs.notShareableEmails.toMutableList() as ArrayList<String>,
            notShareableTeamIds = navigationArgs.notShareableTeamIds.toMutableList() as ArrayList<Int>
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

        shareButton.initProgress(this)
        shareButton.setOnClickListener {
            shareButton.showProgress()
            checkShare(selectedPermission) { file, body ->
                createShareAndCloseDialog(file, body)
            }
        }
    }

    private fun addToSharedElementList(element: Shareable) {
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
                    users.add(element)
                    availableUsersAdapter.notShareableUserIds.add(element.id)
                    createChip(element).setOnClickListener {
                        users.remove(element)
                        availableUsersAdapter.notShareableUserIds.remove(element.id)
                        selectedItemsChipGroup.removeView(it)
                    }
                }
            }
        }
    }

    private fun createChip(item: Shareable): Chip {
        val chip = layoutInflater.inflate(R.layout.chip_shared_elements, null) as Chip

        when (item) {
            is DriveUser -> {
                chip.text = item.displayName
                lifecycleScope.launch(Dispatchers.IO) {
                    requireContext().apply {
                        val fallback = generateInitialsAvatarDrawable(
                            initials = item.displayName.getInitials(),
                            background = getBackgroundColorBasedOnId(item.id)
                        )
                        val imageLoader = ImageLoader.Builder(this).build()
                        val request = ImageRequest.Builder(this)
                            .data(item.avatar)
                            .transformations(CircleCropTransformation())
                            .fallback(fallback)
                            .error(fallback)
                            .placeholder(R.drawable.ic_account)
                            .build()
                        imageLoader.execute(request).drawable?.let {
                            withContext(Dispatchers.Main) {
                                chip.chipIcon = it
                            }
                        }
                    }
                }
            }
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

        selectedItemsChipGroup.addView(chip)
        return chip
    }

    private fun createShareAndCloseDialog(file: File, body: MutableMap<String, Serializable>) {
        body += "lang" to Locale.getDefault().language
        body += "message" to shareMessage.text.toString()

        fileShareViewModel.postFileShare(file, body).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                // TODO - Temp hack because API doesn't return permission yet
                apiResponse.data?.valid?.users?.forEach {
                    it.permission = selectedPermission.apiValue
                }
                setBackNavigationResult(SHARE_SELECTION_KEY, apiResponse.data?.valid)
            } else {
                Utils.showSnackbar(requireView(), apiResponse.translateError())
            }
            shareButton.hideProgress(R.string.buttonShare)
        }
    }

    private fun checkShare(
        newPermission: Shareable.ShareablePermission,
        onCheckApproved: (file: File, body: MutableMap<String, Serializable>) -> Unit
    ) {
        fileShareViewModel.currentFile.value?.let { file ->
            val body = mutableMapOf(
                "emails" to ArrayList(selectedItems.invitations.map { it.email }),
                "user_ids" to ArrayList(selectedItems.users.map { it.id }),
                "team_ids" to ArrayList(selectedItems.teams.map { it.id }),
                "permission" to newPermission
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
                    Utils.showSnackbar(requireView(), apiResponse.translateError())
                    shareButton.hideProgress(R.string.buttonShare)
                }
            }
        }
    }

    private fun showConflictDialog(
        newPermission: Shareable.ShareablePermission,
        checkResults: ArrayList<FileCheckResult>,
        onConflictApproved: () -> Unit
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
}
