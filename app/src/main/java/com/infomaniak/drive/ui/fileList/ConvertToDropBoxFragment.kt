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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.lib.core.utils.hideProgressCatching
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgressCatching

class ConvertToDropBoxFragment : ManageDropboxFragment() {

    private val navigationArgs: ManageDropboxFragmentArgs by navArgs()
    override var isManageDropBox = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        fileShareCollapsingToolbarLayout.title = getString(R.string.convertToDropboxTitle, navigationArgs.fileName)

        shareLinkCardView.isGone = true
        disableButton.isGone = true

        val file = FileController.getFileById(navigationArgs.fileId) ?: return

        updateUi(file)

        settings.expirationDateInput.init(fragmentManager = parentFragmentManager)

        enableSaveButton()
        saveButton.initProgress(viewLifecycleOwner)
        saveButton.setOnClickListener {
            settings.apply {
                val limitFileSize = if (limitStorageSwitch.isChecked) {
                    limitStorageValue.text.toString().toDoubleOrNull()
                } else {
                    null
                }

                saveButton.showProgressCatching()
                mainViewModel.createDropBoxFolder(
                    file = file,
                    emailWhenFinished = emailWhenFinishedSwitch.isChecked,
                    password = if (passwordSwitch.isChecked) passwordTextInput.text?.toString() else null,
                    limitFileSize = limitFileSize?.let { Utils.convertGigaByteToBytes(it) },
                    validUntil = if (expirationDateSwitch.isChecked) expirationDateInput.getCurrentTimestampValue() else null
                ).observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse.isSuccess()) {
                        mainViewModel.createDropBoxSuccess.value = apiResponse.data
                        findNavController().popBackStack()
                    } else {
                        showSnackbar(apiResponse.translateError())
                    }
                    saveButton.hideProgressCatching(R.string.buttonSave)
                }
            }
        }
    }
}
