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
package com.infomaniak.drive.ui.addFiles

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.databinding.FragmentNewFolderBinding
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

class NewFolderFragment : Fragment() {

    private var binding: FragmentNewFolderBinding by safeBinding()

    private val newFolderViewModel: NewFolderViewModel by navGraphViewModels(R.id.newFolderFragment)
    private val arguments: NewFolderFragmentArgs by navArgs()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentNewFolderBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        newFolderViewModel.apply {
            currentFolderId.value = arguments.parentFolderId
            userDrive = arguments.userDrive
            currentPermission = null
        }

        binding.toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        initPrivateFolder()
        initCommonFolder()
        initDropBoxFolder()
    }

    private fun initPrivateFolder() {
        binding.privateFolder.setOnClickListener {
            safeNavigate(R.id.createPrivateFolderFragment)
        }
    }

    private fun initCommonFolder() = with(binding) {
        val drive = newFolderViewModel.userDrive?.let {
            DriveInfosController.getDrive(it.userId, it.driveId)
        } ?: AccountUtils.getCurrentDrive()

        if (drive?.capabilities?.useTeamSpace == true) {
            commonFolderDescription.text = getString(R.string.commonFolderDescription, drive.name)
            commonFolder.setOnClickListener {
                safeNavigate(R.id.createCommonFolderFragment)
            }
        } else {
            commonFolder.isGone = true
        }
    }

    private fun initDropBoxFolder() {
        binding.dropBox.setOnClickListener {
            safeNavigate(
                if (AccountUtils.getCurrentDrive()?.pack?.capabilities?.useDropbox == true) R.id.createDropBoxFolderFragment
                else R.id.dropBoxBottomSheetDialog
            )
        }
    }
}
