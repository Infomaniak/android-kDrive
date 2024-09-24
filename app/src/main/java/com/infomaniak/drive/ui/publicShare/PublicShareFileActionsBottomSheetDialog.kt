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
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.FragmentBottomSheetPublicShareFileActionsBinding
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.safeBinding

class PublicShareFileActionsBottomSheetDialog : BottomSheetDialogFragment(), OnPublicShareItemClickListener {

    private var binding: FragmentBottomSheetPublicShareFileActionsBinding by safeBinding()
    override val publicShareViewModel: PublicShareViewModel by activityViewModels()

    override val ownerFragment = this
    override val currentContext by lazy { requireContext() }
    override lateinit var currentFile: File
    override val previewPDFHandler = null

    private val mainButton by lazy { (requireActivity() as PublicShareActivity).getMainButton() }
    override val drivePermissions = DrivePermissions()

    override fun initCurrentFile() {
        currentFile = publicShareViewModel.fileClicked ?: throw Exception("No current file found")
    }

    override fun onDownloadSuccess() {
        findNavController().popBackStack(destinationId = R.id.publicShareListFragment, inclusive = false)
    }

    override fun onDownloadError(errorMessage: Int) {
        showSnackbar(errorMessage, anchor = mainButton)
        findNavController().popBackStack()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetPublicShareFileActionsBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        initBottomSheet()

        drivePermissions.registerPermissions(this@PublicShareFileActionsBottomSheetDialog) { authorized ->
            if (authorized) downloadFileClicked()
        }

        observeCacheFileForAction(viewLifecycleOwner)
    }

    private fun initBottomSheet() = with(binding.publicShareFileActionsView) {
        runCatching { initCurrentFile() }.onFailure { findNavController().popBackStack() }
        updateWithExternalFile(currentFile)
        initOnClickListener(onItemClickListener = this@PublicShareFileActionsBottomSheetDialog)
        isPrintingHidden(isGone = true)
        if (currentFile.isFolder()) displayFolderActions()
    }
}
