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
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_new_folder.*

class NewFolderFragment : Fragment() {

    private val newFolderViewModel: NewFolderViewModel by navGraphViewModels(R.id.newFolderFragment)
    private val arguments: NewFolderFragmentArgs by navArgs()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_new_folder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        newFolderViewModel.currentFolderId.value = arguments.parentFolderId
        newFolderViewModel.userDrive = arguments.userDrive

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        initPrivateFolder()
        initCommonFolder()
        initDropBoxFolder()
    }

    private fun initPrivateFolder() {
        privateFolder.setOnClickListener {
            safeNavigate(R.id.createPrivateFolderFragment)
        }
    }

    private fun initCommonFolder() {
        val drive = newFolderViewModel.userDrive?.let {
            DriveInfosController.getDrives(it.userId, driveId = it.driveId).firstOrNull()
        } ?: AccountUtils.getCurrentDrive()

        if (drive?.canCreateTeamFolder == true) {
            commonFolderDescription.text = getString(R.string.commonFolderDescription, drive.name)
            commonFolder.setOnClickListener {
                safeNavigate(R.id.createCommonFolderFragment)
            }
        } else {
            commonFolder.isGone = true
        }
    }

    private fun initDropBoxFolder() {
        dropBox.setOnClickListener {
            safeNavigate(
                if (AccountUtils.getCurrentDrive()?.packFunctionality?.dropbox == true) R.id.createDropBoxFolderFragment
                else R.id.dropBoxBottomSheetDialog
            )
        }
    }
}