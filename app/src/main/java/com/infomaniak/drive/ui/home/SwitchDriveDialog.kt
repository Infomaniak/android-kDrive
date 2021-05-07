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
package com.infomaniak.drive.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.DialogFragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.android.synthetic.main.dialog_switch_drive.*
import kotlinx.android.synthetic.main.item_search_view.*

class SwitchDriveDialog : DialogFragment() {

    private lateinit var driveListAdapter: DriveListAdapter
    private lateinit var initialDriveList: ArrayList<Drive>
    private val homeViewModel: HomeViewModel by navGraphViewModels(R.id.homeFragment)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_switch_drive, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val layoutParams = dialog?.window?.attributes
        layoutParams?.dimAmount = 0.8F
        layoutParams?.flags = layoutParams?.flags?.or(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog?.window?.attributes = layoutParams
        dialog?.window?.setLayout(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.MATCH_PARENT)
        view?.setOnClickListener { _ ->
            findNavController().popBackStack()
        }

        initialDriveList = DriveInfosController.getDrives(AccountUtils.currentUserId)
        AccountUtils.getCurrentDrive()?.let { // Set current drive to first position
            initialDriveList.apply {
                remove(find { drive -> drive.id == it.id })
                add(0, it)
            }
        }
        driveListAdapter = DriveListAdapter(initialDriveList) { drive ->
            dismiss()
            // TODO - Implement drive blocked BottomSheetDialog (for invoice issues) - Awaiting API attributes
            if (drive.maintenance) {
                findNavController().navigate(
                    SwitchDriveDialogDirections.actionSwitchDriveDialogToDriveMaintenanceBottomSheetFragment(
                        driveName = drive.name
                    )
                )
            } else {
                AccountUtils.currentDriveId = drive.id
                FileController.switchDriveDB(UserDrive())
                homeViewModel.driveSelectionDialogDismissed.value = true
            }
        }

        clearButton.setOnClickListener { searchView.text = null }
        selectionRecyclerView.adapter = driveListAdapter
        searchView.hint = getString(R.string.switchDriveSearchViewHint)
        searchView.doOnTextChanged { text, _, _, _ ->
            driveListAdapter.apply {
                this.driveList = if (text.isNullOrEmpty()) {
                    clearButton.visibility = View.INVISIBLE
                    initialDriveList
                } else {
                    clearButton.visibility = View.VISIBLE
                    ArrayList(initialDriveList.filter { it.name.contains(text, true) })
                }
                notifyDataSetChanged()
            }
        }
    }
}
