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
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode
import com.infomaniak.drive.data.api.ErrorCode.Companion.formatError
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.FolderPermission
import com.infomaniak.drive.data.models.Share
import com.infomaniak.drive.data.models.Tag
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.fileShare.PermissionsAdapter
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.hideKeyboard
import com.infomaniak.drive.utils.isPositive
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.fragment_create_folder.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

open class CreateFolderFragment : Fragment() {
    protected val newFolderViewModel: NewFolderViewModel by navGraphViewModels(R.id.newFolderFragment)
    protected val mainViewModel: MainViewModel by activityViewModels()
    protected lateinit var adapter: PermissionsAdapter
    protected lateinit var currentPermission: FolderPermission

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_create_folder, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createFolderButton.initProgress(viewLifecycleOwner)
        setupAdapter { selectedPermission ->
            currentPermission = selectedPermission
        }

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }
        folderNameValueInput.doOnTextChanged { _, _, _, count ->
            createFolderButton.isEnabled = count.isPositive()
        }
    }

    protected fun canInherit(userList: ArrayList<DriveUser>, tagList: ArrayList<Tag>): Boolean {
        return userList.size > 1 || tagList.isNotEmpty()
    }

    protected fun saveNewFolder(newFolder: File) {
        mainViewModel.currentFolder.value?.id?.let { parentFolderID ->
            runBlocking(Dispatchers.IO) {
                newFolderViewModel.saveNewFolder(parentFolderID, newFolder)
            }
        }
        mainViewModel.currentFolder.value = newFolder
    }

    private fun setupAdapter(onPermissionSelected: (permission: FolderPermission) -> Unit) {
        adapter = PermissionsAdapter(currentUser = AccountUtils.currentUser, showSelectionCheckIcon = false) {
            onPermissionSelected(it as FolderPermission)
        }
        permissionsRecyclerView.adapter = adapter
    }

    protected fun getShare(onSuccess: (share: Share) -> Unit) {
        newFolderViewModel.currentFolderId.value?.let { currentFolderId ->
            mainViewModel.getFileShare(currentFolderId, userDrive = newFolderViewModel.userDrive)
                .observe(viewLifecycleOwner) { apiResponse ->
                    apiResponse?.data?.let { share ->
                        onSuccess(share)
                    }
                }
        }
    }

    protected fun createFolder(onlyForMe: Boolean, onFolderCreated: (file: File?, redirectToShareDetails: Boolean) -> Unit) {
        folderNameValueInput.hideKeyboard()
        createFolderButton.showProgress()
        newFolderViewModel.currentFolderId.value?.let { parentId ->
            newFolderViewModel.createFolder(
                folderNameValueInput.text.toString(),
                parentId,
                onlyForMe = onlyForMe
            ).observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    val redirectToShareDetails = currentPermission == FolderPermission.SPECIFIC_USERS
                    onFolderCreated(apiResponse.data, redirectToShareDetails)
                } else {
                    if (apiResponse.formatError() == ErrorCode.DESTINATION_ALREADY_EXISTS) {
                        folderNameValueLayout.error = getString(apiResponse.translateError())
                    }
                    requireActivity().showSnackbar(apiResponse.translateError())
                }
                createFolderButton.hideProgress(R.string.createFolderTitle)
            }
        }
    }
}
