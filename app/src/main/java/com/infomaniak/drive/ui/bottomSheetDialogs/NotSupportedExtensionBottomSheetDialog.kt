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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.openOnlyOfficeActivity
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*

class NotSupportedExtensionBottomSheetDialog : InformationBottomSheetDialog() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        FileController.getFileById(requireArguments().getInt(FILE_ID))?.let { currentFile ->

            title.text = getString(R.string.notSupportedExtensionTitle, currentFile.getFileExtension())
            description.text = getString(R.string.notSupportedExtensionDescription, currentFile.name)
            illu.layoutParams.height = 50.toPx()
            illu.layoutParams.width = 50.toPx()
            illu.setImageResource(R.drawable.ic_info)

            secondaryActionButton.setText(R.string.buttonOpenReadOnly)
            secondaryActionButton.setOnClickListener {
                requireContext().openOnlyOfficeActivity(currentFile)
                dismiss()
            }

            actionButton.initProgress(this)
            actionButton.text = getString(R.string.buttonCreateOnlyOfficeCopy, currentFile.onlyofficeConvertExtension)
            actionButton.setOnClickListener {
                actionButton.showProgress()
                mainViewModel.convertFile(currentFile).observe(viewLifecycleOwner) { apiResponse ->
                    when (apiResponse?.result) {
                        ApiResponse.Status.SUCCESS -> apiResponse.data?.let { newFile ->
                            requireContext().openOnlyOfficeActivity(newFile)
                        }
                        else -> requireActivity().showSnackbar(apiResponse.translateError())
                    }
                    dismiss()
                }
            }
        }
    }

    companion object {
        const val FILE_ID = "fileId"
    }
}