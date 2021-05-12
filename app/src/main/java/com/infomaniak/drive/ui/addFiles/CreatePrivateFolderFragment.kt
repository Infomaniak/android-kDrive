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
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File.FolderPermission.*
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_create_folder.*

class CreatePrivateFolderFragment : CreateFolderFragment() {

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        currentPermission = ONLY_ME
        adapter.apply {
            addItem(currentPermission)
            getShare {
                setUsers(it.users)
                addItem(if (canInherit(it.users, it.tags)) INHERIT else SPECIFIC_USERS)
            }
        }

        createFolderButton.setOnClickListener {
            createFolder(currentPermission == ONLY_ME) { file, redirectToShareDetails ->
                file?.let {
                    saveNewFolder(file)
                    requireActivity().showSnackbar(R.string.createPrivateFolderSucces, anchorView = requireActivity().mainFab)
                    safeNavigate(
                        if (redirectToShareDetails) CreatePrivateFolderFragmentDirections.actionCreatePrivateFolderFragmentToFileShareDetailsFragment(
                            fileId = file.id, fileName = file.name, ignoreCreateFolderStack = true
                        )
                        else CreatePrivateFolderFragmentDirections.actionCreatePrivateFolderFragmentToFileListFragment(
                            folderID = file.id, folderName = file.name, ignoreCreateFolderStack = true
                        )
                    )
                }
            }
        }
    }
}
