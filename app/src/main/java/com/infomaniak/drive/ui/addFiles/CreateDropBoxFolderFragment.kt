/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2023 Infomaniak Network SA
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

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.MatomoDrive.trackNewElementEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.FolderPermission.*
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.animateRotation
import com.infomaniak.drive.utils.showOrHideEmptyError
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

class CreateDropBoxFolderFragment : CreateFolderFragment() {

    var showAdvancedSettings = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        advancedSettings.isVisible = true
        createFolderButton.setText(R.string.createDropBoxTitle)
        createFolderCollapsing.title = getString(R.string.createDropBoxTitle)
        folderCreateIcon.icon.setImageResource(R.drawable.ic_folder_dropbox)
        folderNameValueLayout.hint = getString(R.string.createDropBoxHint)
        setupAdvancedSettings()

        adapter.apply {
            getShare {
                setUsers(it.users)
                val permissions: ArrayList<Permission> = arrayListOf(
                    ONLY_ME,
                    if (canInherit(it.users, it.teams)) INHERIT else SPECIFIC_USERS,
                )
                selectionPosition = permissions.indexOf(newFolderViewModel.currentPermission)
                setAll(permissions)
            }
        }

        advancedSettingsCardView.isVisible = true
        advancedSettings.setOnClickListener { toggleShowAdvancedSettings() }

        createFolderButton.setOnClickListener { createDropBoxFolder() }
    }

    private fun createDropBoxFolder() {
        createDropBox(onDropBoxCreated = { file ->
            mainViewModel.createDropBoxSuccess.value = file.dropbox
            if (newFolderViewModel.currentPermission == ONLY_ME) {
                findNavController().popBackStack(R.id.newFolderFragment, true)
            } else {
                navigateToFileShareDetails(file)
            }
        }, onError = { translatedError ->
            showSnackbar(translatedError)
            findNavController().popBackStack(R.id.newFolderFragment, true)
        })
    }

    private fun createDropBox(
        onDropBoxCreated: (file: File) -> Unit,
        onError: (translatedError: String) -> Unit,
    ) {
        if (!isValid()) return

        trackNewElementEvent("createDropbox")

        with(binding.dropboxSettings) {
            val emailWhenFinished = emailWhenFinishedSwitch.isChecked
            val validUntil = if (expirationDateSwitch.isChecked) expirationDateInput.getCurrentTimestampValue() else null
            val password = passwordTextInput.text.toString()
            val limitFileSize = Utils.convertGigaByteToBytes(limitStorageValue.text.toString().toDoubleOrNull() ?: 1.0)

            createFolder(false) { file, _ ->
                file?.let {
                    mainViewModel.createDropBoxFolder(file, emailWhenFinished, limitFileSize, password, validUntil)
                        .observe(viewLifecycleOwner) { apiResponse ->
                            when (apiResponse?.result) {
                                ApiResponse.Status.SUCCESS -> apiResponse.data?.let { dropBox ->
                                    file.dropbox = dropBox
                                    onDropBoxCreated(file)
                                }
                                else -> onError(getString(apiResponse.translateError()))
                            }
                        }
                }
            }
        }
    }

    private fun navigateToFileShareDetails(file: File) {
        runBlocking(Dispatchers.IO) {
            newFolderViewModel.saveNewFolder(newFolderViewModel.currentFolderId.value!!, file)
        }

        safeNavigate(
            CreateDropBoxFolderFragmentDirections.actionCreateDropBoxFolderFragmentToFileShareDetailsFragment(
                fileId = file.id, ignoreCreateFolderStack = true
            )
        )
    }

    /**
     * Will toggle the advanced settings "visibility" and chevron
     */
    private fun toggleShowAdvancedSettings() {
        showAdvancedSettings = !showAdvancedSettings
        showAdvancedSettings(showAdvancedSettings, true)
    }

    private fun showAdvancedSettings(show: Boolean, displayAnimation: Boolean = false) = with(binding) {
        if (displayAnimation) advancedSettingsChevron.animateRotation(show)
        dropboxSettingsDivider.isVisible = show
        dropboxSettings.root.isVisible = show
    }

    private fun setupAdvancedSettings() = with(binding.dropboxSettings) {
        showAdvancedSettings(showAdvancedSettings)
        passwordSwitch.setOnCheckedChangeListener(createOnCheckedChangeListener(passwordTextLayout))
        expirationDateSwitch.setOnCheckedChangeListener(createOnCheckedChangeListener(expirationDateInput))
        limitStorageSwitch.setOnCheckedChangeListener(
            createOnCheckedChangeListener(
                limitStorageValueLayout,
                limitStorageValueUnit
            )
        )
        expirationDateInput.init(fragmentManager = parentFragmentManager)
    }

    private fun createOnCheckedChangeListener(vararg viewsToReveal: View): CompoundButton.OnCheckedChangeListener =
        CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewsToReveal.forEach { it.isVisible = isChecked }
        }

    private fun isValid(): Boolean = with(binding.dropboxSettings) {
        var result = true
        if (passwordSwitch.isChecked) {
            result = !passwordTextInput.showOrHideEmptyError()
        }

        if (limitStorageSwitch.isChecked) {
            limitStorageValue.text.toString().apply {
                when {
                    this.isBlank() -> {
                        limitStorageValueLayout.error = getString(R.string.allEmptyInputError)
                        result = false
                    }
                    this.toDoubleOrNull() == 0.0 -> {
                        limitStorageValueLayout.error = getString(R.string.createDropBoxLimitFileSizeError)
                        result = false
                    }
                }
            }
        }

        return result
    }
}
