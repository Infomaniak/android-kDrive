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
package com.infomaniak.drive.ui.addFiles

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.FolderPermission.ALL_DRIVE_USERS
import com.infomaniak.drive.data.models.File.FolderPermission.SPECIFIC_USERS
import com.infomaniak.drive.utils.AccountUtils
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
        folderCreateIcon.icon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_folder_common_documents))
        pathCard.isVisible = true
        pathTitle.isVisible = true

        AccountUtils.getCurrentDrive()?.let { drive ->
            pathDriveText.text = drive.name
            pathDriveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
        }

        adapter.setAll(arrayListOf(ALL_DRIVE_USERS, SPECIFIC_USERS))

        createFolderButton.setOnClickListener {
            createCommonFolder { file ->
                file?.let {
                    requireActivity().showSnackbar(R.string.createCommonFolderSucces)
                    if (currentPermission == SPECIFIC_USERS) {
                        safeNavigate(
                            CreateCommonFolderFragmentDirections.actionCreateCommonFolderFragmentToFileShareDetailsFragment(
                                file = file, ignoreCreateFolderStack = true
                            )
                        )
                    } else {
                        findNavController().popBackStack(R.id.newFolderFragment, true)
                    }
                }
            }
        }
    }

    private fun createCommonFolder(onFolderCreated: (newFolder: File?) -> Unit) {
        folderNameValueInput.hideKeyboard()
        createFolderButton.showProgress()
        newFolderViewModel.createCommonFolder(
            folderNameValueInput.text.toString(),
            currentPermission == ALL_DRIVE_USERS
        ).observe(viewLifecycleOwner) { apiResponse ->
            if (apiResponse.isSuccess()) {
                onFolderCreated(apiResponse.data)
            } else {
                if (apiResponse.error?.code == ErrorCode.DESTINATION_ALREADY_EXISTS.code) {
                    folderNameValueLayout.error = getString(apiResponse.translateError())
                }
                requireActivity().showSnackbar(apiResponse.translateError())
            }
            createFolderButton.hideProgress(R.string.createFolderTitle)
        }
    }
}
