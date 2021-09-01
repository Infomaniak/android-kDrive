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

import android.os.Bundle
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.widget.CompoundButton
import androidx.core.content.ContextCompat
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
import okhttp3.internal.toLongOrDefault

class CreateDropBoxFolderFragment : CreateFolderFragment() {
    var showAdvancedSettings = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        advancedSettings.visibility = VISIBLE
        createFolderButton.setText(R.string.createDropBoxTitle)
        createFolderCollapsing.title = getString(R.string.createDropBoxTitle)
        folderCreateIcon.icon.setImageDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.ic_folder_dropbox))
        folderNameValueLayout.hint = getString(R.string.createDropBoxHint)
        setupAdvancedSettings()

        adapter.apply {
            addItem(ONLY_ME)
            getShare {
                setUsers(it.users)
                addItem(if (canInherit(it.users, it.teams)) INHERIT else SPECIFIC_USERS)
            }
        }

        advancedSettingsCardView.visibility = VISIBLE
        advancedSettings.setOnClickListener {
            toggleShowAdvancedSettings()
        }

        createFolderButton.setOnClickListener {
            createDropBox(onDropBoxCreated = { file, dropBox ->
                mainViewModel.createDropBoxSuccess.value = dropBox
                if (currentPermission == ONLY_ME) {
                    findNavController().popBackStack(R.id.newFolderFragment, true)
                } else {
                    safeNavigate(
                        CreateDropBoxFolderFragmentDirections.actionCreateDropBoxFolderFragmentToFileShareDetailsFragment(
                            fileId = file.id, fileName = file.name, ignoreCreateFolderStack = true
                        )
                    )
                }
            }, onError = { translatedError ->
                requireActivity().showSnackbar(translatedError)
                findNavController().popBackStack(R.id.newFolderFragment, true)
            })
        }
    }

    /**
     * Will toggle the advanced settings "visibility" and chevron
     */
    private fun toggleShowAdvancedSettings() {
        showAdvancedSettings = !showAdvancedSettings
        showAdvancedSettings(showAdvancedSettings, true)
    }

    private fun showAdvancedSettings(show: Boolean, displayAnimation: Boolean = false) {
        val visibility = if (show) VISIBLE else GONE
        if (displayAnimation) advancedSettingsChevron.animateRotation(show)

        dropboxSettingsDivider.visibility = visibility
        dropboxSettings.visibility = visibility
    }

    private fun setupAdvancedSettings() {
        showAdvancedSettings(showAdvancedSettings)
        passwordSwitch.setOnCheckedChangeListener(createOnCheckedChangeListener(passwordTextLayout))
        expirationDateSwitch.setOnCheckedChangeListener(createOnCheckedChangeListener(expirationDateInput))
        limiteStorageSwitch.setOnCheckedChangeListener(
            createOnCheckedChangeListener(
                limiteStorageValueLayout,
                limiteStorageValueUnit
            )
        )
        expirationDateInput.init(fragmentManager = parentFragmentManager)
    }

    private fun createOnCheckedChangeListener(vararg viewsToReveal: View): CompoundButton.OnCheckedChangeListener {
        return CompoundButton.OnCheckedChangeListener { _, isChecked ->
            viewsToReveal.forEach {
                it.visibility = if (isChecked) VISIBLE else GONE
            }
        }
    }

    private fun isValid(): Boolean {
        var result = true
        if (passwordSwitch.isChecked) {
            result = !passwordTextInput.showOrHideEmptyError()
        }

        if (limiteStorageSwitch.isChecked) {
            limiteStorageValue.text.toString().apply {
                when {
                    this.isBlank() -> {
                        limiteStorageValueLayout.error = getString(R.string.allEmptyInputError)
                        result = false
                    }
                    this.toLong() < 1 -> {
                        limiteStorageValueLayout.error = getString(R.string.createDropBoxLimiteFileSizeError)
                        result = false
                    }
                }
            }
        }

        return result
    }

    private fun createDropBox(
        onDropBoxCreated: (file: File, dropBox: DropBox) -> Unit,
        onError: (translatedError: String) -> Unit
    ) {
        if (!isValid()) return

        val emailWhenFinished = emailWhenFinishedSwitch.isChecked
        val validUntil = if (expirationDateSwitch.isChecked) expirationDateInput.getCurrentTimestampValue() else null
        val password = passwordTextInput.text.toString()
        val limitFileSize = Utils.convertGigaByteToBytes(limiteStorageValue.text.toString().toLongOrDefault(1))


        createFolder(false) { file, _ ->
            file?.let {
                mainViewModel.createDropBoxFolder(file, emailWhenFinished, limitFileSize, password, validUntil)
                    .observe(viewLifecycleOwner) { apiResponse ->
                        when (apiResponse?.result) {
                            ApiResponse.Status.SUCCESS -> apiResponse.data?.let { dropBox ->
                                onDropBoxCreated(file, dropBox)
                            }
                            else -> onError(getString(apiResponse.translateError()))
                        }
                    }
            }
        }
    }
}
