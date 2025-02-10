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
package com.infomaniak.drive.ui.menu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.FragmentBottomSheetSelectDriveBinding
import com.infomaniak.drive.databinding.PopupSelectUserBinding
import com.infomaniak.drive.ui.menu.UserAdapter
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.setUserView
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.utils.safeBinding

class SelectDriveDialog : FullScreenBottomSheetDialog() {

    private var binding: FragmentBottomSheetSelectDriveBinding by safeBinding()
    private var popupLayoutBinding: PopupSelectUserBinding by safeBinding()

    private val selectDriveViewModel: SelectDriveViewModel by activityViewModels()

    private lateinit var popupWindow: PopupWindow

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        popupLayoutBinding = PopupSelectUserBinding.inflate(inflater, container, false)
        return FragmentBottomSheetSelectDriveBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(selectDriveViewModel) {
        super.onViewCreated(view, savedInstanceState)

        binding.toolbar.setNavigationOnClickListener { dismiss() }

        val driveListAdapter = DriveListAdapter(getDriveList(), false) { newSelectedDrive ->
            selectedDrive.value = newSelectedDrive
            dismiss()
        }
        binding.driveList.adapter = driveListAdapter

        AccountUtils.getAllUsers().observe(viewLifecycleOwner) { users ->
            if (users.size > 1) {
                val selectedUser = users.find { it.id == selectedUserId.value } ?: users.first()
                binding.userCardview.itemViewUser.setUserView(selectedUser) {
                    popupWindow = PopupWindow(
                        popupLayoutBinding.root,
                        binding.userCardview.root.width,
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                    ).apply {
                        isOutsideTouchable = true
                        isFocusable = true
                        elevation = 20.0f
                        showAsDropDown(binding.userCardview.root)
                    }
                }
                binding.userCardview.root.isVisible = true

                popupLayoutBinding.usersRecyclerView.adapter = UserAdapter(users, isCardView = false) { user ->
                    selectedUserId.value = user.id
                    driveListAdapter.setDrives(getDriveList())

                    binding.userCardview.itemViewUser.setUserView(user) {
                        popupWindow.showAsDropDown(binding.userCardview.root)
                    }

                    popupWindow.dismiss()
                }
            } else {
                selectedUserId.value = users.first().id
            }
        }
    }

    private fun SelectDriveViewModel.getDriveList(): ArrayList<Drive> {
        return DriveInfosController.getDrives(selectedUserId.value, sharedWithMe = if (showSharedWithMe) null else false)
    }
}
