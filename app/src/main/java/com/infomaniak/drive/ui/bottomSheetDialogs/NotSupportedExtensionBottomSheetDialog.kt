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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.core.legacy.utils.toPx
import com.infomaniak.core.network.models.ApiResponseStatus
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.openOnlyOfficeActivity
import com.infomaniak.drive.utils.showSnackbar

class NotSupportedExtensionBottomSheetDialog : InformationBottomSheetDialog() {

    val mainViewModel: MainViewModel by activityViewModels()
    private val navigationArgs: NotSupportedExtensionBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        FileController.getFileById(navigationArgs.fileId)?.let { currentFile ->

            title.text = getString(R.string.notSupportedExtensionTitle, currentFile.getFileExtension())
            description.text = getString(R.string.notSupportedExtensionDescription, currentFile.name)

            illu.apply {
                layoutParams.height = 50.toPx()
                layoutParams.width = 50.toPx()
                setImageResource(R.drawable.ic_info)
            }

            secondaryActionButton.apply {
                setText(R.string.buttonOpenReadOnly)
                setOnClickListener {
                    requireContext().openOnlyOfficeActivity(currentFile)
                    dismiss()
                }
            }

            actionButton.apply {
                initProgress(this@NotSupportedExtensionBottomSheetDialog)
                text = getString(R.string.buttonCreateOnlyOfficeCopy, currentFile.conversion?.onlyofficeExtension)
                setOnClickListener {
                    showProgressCatching()
                    mainViewModel.convertFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
                        when (apiResponse?.result) {
                            ApiResponseStatus.SUCCESS -> apiResponse.data?.let { newFile ->
                                requireContext().openOnlyOfficeActivity(newFile)
                            }
                            else -> showSnackbar(apiResponse.translateError())
                        }
                        dismiss()
                    }
                }
            }
        }
    }
}
