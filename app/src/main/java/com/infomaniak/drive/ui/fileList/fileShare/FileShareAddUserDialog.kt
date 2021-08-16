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
import kotlin.collections.ArrayList

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
            itemList = getAvailableUsers(),
            notShareableItems = ArrayList(navigationArgs.notShareableUsersIds.map { id -> DriveUser(id = id) })
        ) { element ->
            userAutoCompleteTextView.setText("")
            addToSharedElementList(if (element is Invitation) element.email else element)
        }

        collapsingToolbarLayout.title = getString(
            if (fileShareViewModel.currentFile.value?.isFolder() == true) R.string.fileShareFolderTitle
            else R.string.fileShareFileTitle
        )

        when {
            navigationArgs.sharedEmail != null -> {
                addToSharedElementList(navigationArgs.sharedEmail as String)
            }
            navigationArgs.sharedUserId != -1 -> {
                val user = fileShareViewModel.availableUsers.value?.find { it.id == navigationArgs.sharedUserId } as DriveUser
                addToSharedElementList(user)
            }
            navigationArgs.sharedTagId != -1 -> {
                // Not supported for now - Awaiting new tags/groups feature from backend
            }
        }

        filePermissions.setOnClickListener {
            safeNavigate(
                FileShareAddUserDialogDirections.actionFileShareAddUserDialogToSelectPermissionBottomSheetDialog(
                    currentPermission = selectedPermission,
                    permissionsGroup = SelectPermissionBottomSheetDialog.PermissionsGroup.FILE_SHARE_UPDATE
                )
            )
        }

        getBackNavigationResult<Bundle>(SelectPermissionBottomSheetDialog.SELECT_PERMISSION_NAV_KEY) { bundle ->
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

    private fun addToSharedElementList(element: Any) {
        selectedItems.apply {
            when (element) {
                is String -> {
                    if (!emails.contains(element) && !users.any { it.email == element }) {
                        emails.add(element)
                        createChip(element).setOnClickListener {
                            emails.remove(element)
                            selectedUsersChipGroup.removeView(it)
                        }
                    }
                }
                is DriveUser -> {
                    if (!users.any { it.id == element.id }) {
                        users.add(element)
                        availableUsersAdapter.notShareableUsers.add(element)
                        createChip(element).setOnClickListener {
                            users.remove(element)
                            availableUsersAdapter.notShareableUsers.remove(element)
                            selectedUsersChipGroup.removeView(it)
                        }
                    }
                }
                is Tag -> {
                    tags.add(element)
                    createChip(element).setOnClickListener {
                        tags.remove(element)
                        selectedUsersChipGroup.removeView(it)
                    }
                }
            }
        }
    }

    private fun createChip(item: Any): Chip {
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
            is String -> {
                chip.text = item
                chip.setChipIconResource(R.drawable.ic_circle_send)
            }
            is Tag -> {
                chip.text = item.name
                chip.setChipIconResource(R.drawable.ic_circle_tag)
            }
        }

        selectedUsersChipGroup.addView(chip)
        return chip
    }

    private fun getAvailableUsers(): List<Shareable> {
        return fileShareViewModel.availableUsers.value
            ?.removeCommonUsers(ArrayList(fileShareViewModel.currentFile.value?.users ?: arrayListOf()))
            ?.filterNot { availableUser ->
                selectedItems.users.any { it.id == availableUser.id }
            } ?: listOf()
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
                "emails" to selectedItems.emails,
                "user_ids" to ArrayList(selectedItems.users.map { user -> user.id }),
                "tag_ids" to selectedItems.tags,
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
                fileShareViewModel.availableUsers.value?.find { user -> user.id == conflictedUsers.first().userId }?.let { user ->
                    getString(
                        R.string.sharedConflictDescription,
                        user.displayName,
                        getString(user.getFilePermission().translation),
                        getString(newPermission.translation)
                    )
                }
            }
            else -> {
                getString(R.string.sharedConflictManyUserDescription, newPermission.apiValue)
            }
        }

        MaterialAlertDialogBuilder(requireContext())
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
