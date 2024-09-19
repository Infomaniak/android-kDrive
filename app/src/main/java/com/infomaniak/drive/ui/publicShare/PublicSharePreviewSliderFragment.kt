/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.publicShare

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewSliderBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderViewModel
import com.infomaniak.drive.utils.setupBottomSheetFileBehavior
import com.infomaniak.drive.views.ExternalFileInfoActionsView
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar

class PublicSharePreviewSliderFragment : BasePreviewSliderFragment(), OnPublicShareItemClickListener {

    private val navigationArgs: PublicSharePreviewSliderFragmentArgs by navArgs()
    override val previewSliderViewModel: PreviewSliderViewModel by activityViewModels()
    override val publicShareViewModel: PublicShareViewModel by activityViewModels()

    override val bottomSheetView: ExternalFileInfoActionsView
        get() = binding.publicShareBottomSheetFileActions
    override val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(bottomSheetView)

    override val isFileShare = true
    override val ownerFragment = this

    override fun initCurrentFile() {
        currentFile = mainViewModel.currentPreviewFileList[navigationArgs.fileId] ?: throw Exception("No current preview found")
    }

    override fun onDownloadSuccess() {
        toggleBottomSheet(shouldShow = true)
    }

    override fun onDownloadError(errorMessage: Int) {
        showSnackbar(errorMessage, anchor = bottomSheetView)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {

        if (noPreviewList()) {
            findNavController().popBackStack()
            return null
        }

        initCurrentFile()

        initPreviewSliderViewModel()

        return FragmentPreviewSliderBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    private fun initPreviewSliderViewModel() {
        previewSliderViewModel.currentPreview = currentFile

        if (previewSliderViewModel.currentPreview == null) {
            userDrive = UserDrive(driveId = navigationArgs.driveId, sharedWithMe = true)
            previewSliderViewModel.userDrive = userDrive
        } else {
            userDrive = previewSliderViewModel.userDrive
        }

        previewSliderViewModel.publicShareUuid = navigationArgs.publicShareUuid
        previewSliderViewModel.publicShareCanDownload = publicShareViewModel.canDownloadFiles
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initBottomSheet()
        observeCacheFileForAction(viewLifecycleOwner)
    }

    private fun initBottomSheet() = with(bottomSheetView) {
        requireActivity().setupBottomSheetFileBehavior(
            bottomSheetBehavior = bottomSheetBehavior,
            isDraggable = publicShareViewModel.canDownloadFiles,
            isFitToContents = true,
        )
        updateWithExternalFile(currentFile)
        initOnClickListener(onItemClickListener = this@PublicSharePreviewSliderFragment)
    }
}
