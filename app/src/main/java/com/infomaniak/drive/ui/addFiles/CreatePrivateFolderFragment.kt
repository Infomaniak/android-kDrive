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
import android.view.View
import androidx.core.view.isGone
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.MatomoDrive.trackNewElementEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File.FolderPermission.INHERIT
import com.infomaniak.drive.data.models.File.FolderPermission.ONLY_ME
import com.infomaniak.drive.data.models.File.FolderPermission.SPECIFIC_USERS
import com.infomaniak.drive.data.models.Permission
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.safeNavigate

class CreatePrivateFolderFragment : CreateFolderFragment() {

    private val createFolderFragmentArgs by navArgs<CreatePrivateFolderFragmentArgs>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter.apply {
            if (createFolderFragmentArgs.isSharedWithMe) {
                binding.accessPermissionTitle.isGone = true
            } else {
                getShare {
                    setUsers(it.users)
                    val permissions: ArrayList<Permission> = arrayListOf(
                        ONLY_ME,
                        if (canInherit(it.users, it.teams)) INHERIT else SPECIFIC_USERS,
                    )
                    selectionPosition = permissions.indexOf(newFolderViewModel.currentPermission)
                    setAll(permissions)
                }
            }
        }

        binding.createFolderButton.setOnClickListener { createPrivateFolder() }
    }

    override fun toggleCreateFolderButton(): Unit = with(binding) {
        if (createFolderFragmentArgs.isSharedWithMe) {
            createFolderButton.isEnabled = !folderNameValueInput.text.isNullOrBlank()
        } else {
            super.toggleCreateFolderButton()
        }
    }

    private fun createPrivateFolder() {
        trackNewElementEvent("createPrivateFolder")
        val onlyForMe = !createFolderFragmentArgs.isSharedWithMe && newFolderViewModel.currentPermission == ONLY_ME
        createFolder(onlyForMe) { file, redirectToShareDetails ->
            file?.let {
                saveNewFolder(file)
                showSnackbar(R.string.createPrivateFolderSucces, true)
                if (redirectToShareDetails) {
                    safeNavigate(
                        CreatePrivateFolderFragmentDirections.actionCreatePrivateFolderFragmentToFileShareDetailsFragment(
                            fileId = file.id,
                            ignoreCreateFolderStack = true,
                        )
                    )
                } else {
                    findNavController().popBackStack(R.id.newFolderFragment, true)
                }
            }
        }
    }
}
