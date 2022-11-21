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
package com.infomaniak.drive.ui.menu.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.menu.UserAdapter
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.setUserView
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.models.user.User
import kotlinx.android.synthetic.main.fragment_bottom_sheet_select_drive.*
import kotlinx.android.synthetic.main.popup_select_user.view.*

class SelectDriveDialog : FullScreenBottomSheetDialog() {

    private val selectDriveViewModel: SelectDriveViewModel by activityViewModels()
    private lateinit var popupLayout: View
    private lateinit var popupWindow: PopupWindow

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        popupLayout = inflater.inflate(R.layout.popup_select_user, container, false)
        return inflater.inflate(R.layout.fragment_bottom_sheet_select_drive, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(selectDriveViewModel) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener { dismiss() }

        val driveListAdapter = DriveListAdapter(getDriveList(), false) { newSelectedDrive ->
            selectedDrive.value = newSelectedDrive
            dismiss()
        }
        driveList.adapter = driveListAdapter

        AccountUtils.getAllUsers().observe(viewLifecycleOwner) { users ->
            if (users.size > 1) {
                val selectedUser = users.find { it.id == selectedUserId.value } ?: users.first()
                userCardview.setUserView(selectedUser) {
                    popupWindow = PopupWindow(popupLayout, userCardview.width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        isOutsideTouchable = true
                        isFocusable = true
                        elevation = 20.0f
                        showAsDropDown(userCardview)
                    }
                }
                userCardview.isVisible = true

                popupLayout.usersRecyclerView.adapter = UserAdapter(users as ArrayList<User>, isCardview = false) { user ->
                    selectedUserId.value = user.id
                    driveListAdapter.setDrives(getDriveList())

                    userCardview.setUserView(user) { popupWindow.showAsDropDown(userCardview) }

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
