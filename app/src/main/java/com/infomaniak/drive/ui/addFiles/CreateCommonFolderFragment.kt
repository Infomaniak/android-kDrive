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
package com.infomaniak.drive.ui.addFiles

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import androidx.core.graphics.toColorInt
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.legacy.utils.hideKeyboard
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackNewElementEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.FolderPermission.ALL_DRIVE_USERS
import com.infomaniak.drive.data.models.File.FolderPermission.SPECIFIC_USERS
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.showSnackbar

class CreateCommonFolderFragment : CreateFolderFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        accessPermissionTitle.setText(R.string.createCommonFolderDescription)
        createFolderCollapsing.title = getString(R.string.createCommonFolderTitle)
        folderCreateIcon.icon.setImageResource(R.drawable.ic_folder_common_documents)
        pathCard.isVisible = true
        pathTitle.isVisible = true

        AccountUtils.getCurrentDrive()?.let { drive ->
            pathDriveText.text = drive.name
            pathDriveIcon.imageTintList = ColorStateList.valueOf(drive.preferences.color.toColorInt())
        }

        adapter.apply {
            val permissions: ArrayList<Permission> = arrayListOf(ALL_DRIVE_USERS, SPECIFIC_USERS)
            selectionPosition = permissions.indexOf(newFolderViewModel.currentPermission)
            setAll(permissions)
        }

        createFolderButton.setOnClickListener { createCommonFolder() }
    }

    private fun createCommonFolder() = with(binding) {
        folderNameValueInput.hideKeyboard()
        createFolderButton.showProgressCatching()
        trackNewElementEvent(MatomoName.CreateCommonFolder)

        newFolderViewModel.createCommonFolder(
            name = folderNameValueInput.text.toString(),
        ).observe(viewLifecycleOwner) { apiResponse ->

            if (apiResponse.isSuccess()) {
                apiResponse.data?.let(::whenFolderCreated)
            } else {
                if (apiResponse.error?.code == ErrorCode.DESTINATION_ALREADY_EXISTS) {
                    folderNameValueLayout.error = getString(apiResponse.translateError())
                }
                showSnackbar(apiResponse.translateError())
            }

            createFolderButton.hideProgressCatching(R.string.createFolderTitle)
        }
    }

    private fun whenFolderCreated(file: File) {
        showSnackbar(R.string.createCommonFolderSucces)

        if (newFolderViewModel.currentPermission == SPECIFIC_USERS) {
            safeNavigate(
                CreateCommonFolderFragmentDirections.actionCreateCommonFolderFragmentToFileShareDetailsFragment(
                    fileId = file.id,
                    ignoreCreateFolderStack = true,
                )
            )
        } else {
            findNavController().popBackStack(R.id.newFolderFragment, true)
        }
    }
}
