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
package com.infomaniak.drive.ui.addFiles

import android.os.Bundle
import android.view.View
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File.FolderPermission.*
import com.infomaniak.drive.utils.MatomoUtils.trackEvent
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_create_folder.*

class CreatePrivateFolderFragment : CreateFolderFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter.apply {
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

        createFolderButton.setOnClickListener { createPrivateFolder() }
    }

    private fun createPrivateFolder() {
        activity?.application?.trackEvent("newElement", "click", "createPrivateFolder")
        createFolder(currentPermission == ONLY_ME) { file, redirectToShareDetails ->
            file?.let {
                saveNewFolder(file)
                requireActivity().showSnackbar(R.string.createPrivateFolderSucces, anchorView = requireActivity().mainFab)
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
