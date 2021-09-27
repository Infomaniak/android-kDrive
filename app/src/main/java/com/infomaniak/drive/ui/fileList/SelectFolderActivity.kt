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
package com.infomaniak.drive.ui.fileList

import android.content.Intent
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.BaseActivity
import kotlinx.android.synthetic.main.activity_select_folder.*
import java.util.*


class SelectFolderActivity : BaseActivity() {

    private val saveExternalViewModel: SaveExternalViewModel by viewModels()

    companion object {
        const val SELECT_FOLDER_REQUEST = 42
        const val USER_ID_TAG = "userID"
        const val USER_DRIVE_ID_TAG = "userDriveID"
        const val FOLDER_ID_TAG = "folderID"
        const val FOLDER_NAME_TAG = "folderNAME"
        const val DISABLE_SELECTED_FOLDER = "disableSelectedFolder"
        const val CUSTOM_ARGS_TAG = "disableSelectedFolder"

        const val BULK_OPERATION_CUSTOM_TAG = "bulk_operation_type"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val userID = intent.extras?.getInt(USER_ID_TAG) ?: throw MissingFormatArgumentException(USER_ID_TAG)
        val driveID = intent.extras?.getInt(USER_DRIVE_ID_TAG) ?: throw MissingFormatArgumentException(USER_DRIVE_ID_TAG)
        val customArgs = intent.extras?.getBundle(CUSTOM_ARGS_TAG)
        saveExternalViewModel.userDrive = UserDrive(userID, driveID)
        saveExternalViewModel.currentDrive = DriveInfosController.getDrives(userID, driveID).firstOrNull()
        saveExternalViewModel.disableSelectedFolder = intent.extras?.getInt(DISABLE_SELECTED_FOLDER)

        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_select_folder)

        saveButton.setOnClickListener {
            val currentFragment = hostFragment.childFragmentManager.fragments.first() as SelectFolderFragment

            val intent = Intent().apply {
                putExtra(FOLDER_ID_TAG, currentFragment.folderID)
                putExtra(FOLDER_NAME_TAG, currentFragment.folderName)
                putExtra(CUSTOM_ARGS_TAG, customArgs)
            }
            setResult(RESULT_OK, intent)
            finish()
        }
    }

    fun showSaveButton() {
        saveButton.visibility = VISIBLE
    }

    fun enableSaveButton(enable: Boolean) {
        saveButton.isEnabled = enable
    }

    fun hideSaveButton() {
        saveButton.visibility = GONE
    }

    class SaveExternalViewModel : ViewModel() {
        var userDrive: UserDrive? = null
        var currentDrive: Drive? = null
        var disableSelectedFolder: Int? = null
    }
}

