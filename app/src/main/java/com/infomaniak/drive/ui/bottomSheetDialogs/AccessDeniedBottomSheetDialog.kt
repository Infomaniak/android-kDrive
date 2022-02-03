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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.os.Bundle
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.fragment_bottom_sheet_information.*
import kotlinx.coroutines.Dispatchers

class AccessDeniedBottomSheetDialog : InformationBottomSheetDialog() {

    private val informationBottomSheetViewModel: InformationBottomSheetViewModel by viewModels()
    private val navigationArgs: AccessDeniedBottomSheetDialogArgs by navArgs()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        illu.setImageResource(R.drawable.ic_stop)
        title.setText(R.string.accessDeniedTitle)

        if (navigationArgs.isAdmin) {
            description.setText(R.string.accessDeniedDescriptionIsAdmin)
            actionButton.initProgress(this)
            actionButton.setText(R.string.buttonConfirmNotify)
            actionButton.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.red_error))
            actionButton.setOnClickListener {
                actionButton.showProgress()
                informationBottomSheetViewModel.enableFolderAccess(navigationArgs.fileId)
                    .observe(viewLifecycleOwner) { apiResponse ->
                        if (apiResponse.data == null) {
                            Utils.showSnackbar(requireView(), apiResponse.translateError())
                        } else {
                            apiResponse.data?.let { file ->
                                safeNavigate(
                                    AccessDeniedBottomSheetDialogDirections.actionAccessDeniedBottomSheetFragmentToFileListFragment(
                                        folderId = file.id,
                                        folderName = file.name
                                    )
                                )
                            }
                        }
                        actionButton.hideProgress(R.string.buttonConfirmNotify)
                    }
            }
            secondaryActionButton.setText(R.string.buttonBack)
        } else {
            description.setText(R.string.accessDeniedDescriptionIsNotAdmin)
            actionButton.setText(R.string.buttonClose)
            actionButton.setOnClickListener { dismiss() }
            secondaryActionButton.isGone = true
        }
    }

    class InformationBottomSheetViewModel : ViewModel() {

        fun enableFolderAccess(fileId: Int): LiveData<ApiResponse<File>> = liveData(Dispatchers.IO) {
            emit(ApiRepository.postFolderAccess(File(driveId = AccountUtils.currentDriveId, id = fileId)))
        }
    }
}