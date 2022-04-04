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
package com.infomaniak.drive.ui.addFiles

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.FolderPermission.ALL_DRIVE_USERS
import com.infomaniak.drive.data.models.File.FolderPermission.SPECIFIC_USERS
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.MatomoUtils.trackNewElementEvent
import com.infomaniak.drive.utils.hideKeyboard
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import kotlinx.android.synthetic.main.fragment_create_folder.*

class CreateCommonFolderFragment : CreateFolderFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        accessPermissionTitle.setText(R.string.createCommonFolderDescription)
        createFolderCollapsing.title = getString(R.string.createCommonFolderTitle)
        folderCreateIcon.icon.setImageResource(R.drawable.ic_folder_common_documents)
        pathCard.isVisible = true
        pathTitle.isVisible = true

        AccountUtils.getCurrentDrive()?.let { drive ->
            pathDriveText.text = drive.name
            pathDriveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
        }

        adapter.apply {
            val permissions: ArrayList<Permission> = arrayListOf(ALL_DRIVE_USERS, SPECIFIC_USERS)
            selectionPosition = permissions.indexOf(newFolderViewModel.currentPermission)
            setAll(permissions)
        }

        createFolderButton.setOnClickListener { createCommonFolder() }
    }

    private fun createCommonFolder() {
        folderNameValueInput.hideKeyboard()
        createFolderButton.showProgress()
        trackNewElementEvent("createCommonFolder")

        newFolderViewModel.createCommonFolder(
            name = folderNameValueInput.text.toString(),
        ).observe(viewLifecycleOwner) { apiResponse ->

            if (apiResponse.isSuccess()) {
                apiResponse.data?.let(::whenFolderCreated)
            } else {
                if (apiResponse.error?.code == ErrorCode.DESTINATION_ALREADY_EXISTS.code) {
                    folderNameValueLayout.error = getString(apiResponse.translateError())
                }
                showSnackbar(apiResponse.translateError())
            }

            createFolderButton.hideProgress(R.string.createFolderTitle)
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
