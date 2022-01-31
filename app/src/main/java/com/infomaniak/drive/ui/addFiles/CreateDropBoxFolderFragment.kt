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

import android.os.Bundle
import android.view.View
import android.widget.CompoundButton
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.FolderPermission.*
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.empty_icon_layout.view.*
import kotlinx.android.synthetic.main.fragment_create_folder.*
import kotlinx.android.synthetic.main.item_dropbox_settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.internal.toLongOrDefault

class CreateDropBoxFolderFragment : CreateFolderFragment() {

    var showAdvancedSettings = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        advancedSettings.isVisible = true
        createFolderButton.setText(R.string.createDropBoxTitle)
        createFolderCollapsing.title = getString(R.string.createDropBoxTitle)
        folderCreateIcon.icon.setImageResource(R.drawable.ic_folder_dropbox)
        folderNameValueLayout.hint = getString(R.string.createDropBoxHint)
        setupAdvancedSettings()

        adapter.apply {
            addItem(ONLY_ME)
            getShare {
                setUsers(it.users)
                addItem(if (canInherit(it.users, it.teams)) INHERIT else SPECIFIC_USERS)
            }
        }

        advancedSettingsCardView.isVisible = true
        advancedSettings.setOnClickListener { toggleShowAdvancedSettings() }

        createFolderButton.setOnClickListener { createDropBoxFolder() }
    }

    private fun createDropBoxFolder() {
        createDropBox(onDropBoxCreated = { file, dropBox ->
            mainViewModel.createDropBoxSuccess.value = dropBox
            if (currentPermission == ONLY_ME) {
                findNavController().popBackStack(R.id.newFolderFragment, true)
            } else {
                navigateToFileShareDetails(file)
            }
        }, onError = { translatedError ->
            requireActivity().showSnackbar(translatedError)
            findNavController().popBackStack(R.id.newFolderFragment, true)
        })
    }

    private fun createDropBox(
        onDropBoxCreated: (file: File, dropBox: DropBox) -> Unit,
        onError: (translatedError: String) -> Unit,
    ) {
        if (!isValid()) return

        val emailWhenFinished = emailWhenFinishedSwitch.isChecked
        val validUntil = if (expirationDateSwitch.isChecked) expirationDateInput.getCurrentTimestampValue() else null
        val password = passwordTextInput.text.toString()
        val limitFileSize = Utils.convertGigaByteToBytes(limitStorageValue.text.toString().toLongOrDefault(1))

        createFolder(false) { file, _ ->
            file?.let {
                mainViewModel.createDropBoxFolder(file, emailWhenFinished, limitFileSize, password, validUntil)
                    .observe(viewLifecycleOwner) { apiResponse ->
                        when (apiResponse?.result) {
                            ApiResponse.Status.SUCCESS -> apiResponse.data?.let { dropBox ->
                                file.collaborativeFolder = dropBox.url
                                onDropBoxCreated(file, dropBox)
                            }
                            else -> onError(getString(apiResponse.translateError()))
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

    private fun showAdvancedSettings(show: Boolean, displayAnimation: Boolean = false) {
        if (displayAnimation) advancedSettingsChevron.animateRotation(show)
        dropboxSettingsDivider.isVisible = show
        dropboxSettings.isVisible = show
    }

    private fun setupAdvancedSettings() {
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

    private fun isValid(): Boolean {
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
                    this.toLong() < 1 -> {
                        limitStorageValueLayout.error = getString(R.string.createDropBoxLimitFileSizeError)
                        result = false
                    }
                }
            }
        }

        return result
    }
}
