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
package com.infomaniak.drive.ui.fileList

import android.content.Intent
import android.os.Bundle
import androidx.activity.viewModels
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.ViewModel
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ActivitySelectFolderBinding
import com.infomaniak.drive.extensions.onApplyWindowInsetsListener
import com.infomaniak.drive.ui.BaseActivity
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.lib.core.utils.setMargins

class SelectFolderActivity : BaseActivity() {

    private val binding: ActivitySelectFolderBinding by lazy { ActivitySelectFolderBinding.inflate(layoutInflater) }

    private val navHostFragment by lazy { supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment }

    private val selectFolderViewModel: SelectFolderViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val navigationArgs: SelectFolderActivityArgs by navArgs()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val userId = navigationArgs.userId
        val driveId = navigationArgs.driveId
        val customArgs = navigationArgs.customArgs
        val disabledFolderId = navigationArgs.disabledFolderId.getIntOrNull()

        // We're doing this in the mainthread because the FileListFragment rely on mainViewModel.selectFolderUserDrive.
        // Moving this call in a background thread we'll break everything
        DriveInfosController.getDrive(driveId = driveId, maintenance = false)?.let { selectedDrive ->
            val isSharedWithMe = selectedDrive.sharedWithMe
            val currentUserDrive = UserDrive(userId, driveId, isSharedWithMe)
            mainViewModel.selectFolderUserDrive = currentUserDrive

            selectFolderViewModel.apply {
                userDrive = currentUserDrive
                currentDrive = DriveInfosController.getDrive(userId, driveId)
                disableSelectedFolderId = disabledFolderId
            }

            setSaveButton(customArgs)

        }

        binding.saveButton.onApplyWindowInsetsListener { view, windowInsets ->
            view.setMargins(bottom = resources.getDimension(R.dimen.marginStandard).toInt() + windowInsets.bottom)
        }
    }

    private fun Int.getIntOrNull(): Int? = if (this <= 0) null else this

    private fun setSaveButton(customArgs: Bundle?) = with(binding) {
        saveButton.setOnClickListener {
            val currentFragment = navHostFragment.childFragmentManager.fragments.first() as SelectFolderFragment
            Intent().apply {
                putExtras(
                    SelectFolderActivityArgs(
                        folderId = currentFragment.folderId,
                        folderName = currentFragment.folderName,
                        customArgs = customArgs
                    ).toBundle()
                )
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    fun getSaveButton() = binding.saveButton

    fun showSaveButton() {
        binding.saveButton.isVisible = true
    }

    fun enableSaveButton(enable: Boolean) {
        binding.saveButton.isEnabled = enable
    }

    fun hideSaveButton() {
        binding.saveButton.isGone = true
    }

    class SelectFolderViewModel : ViewModel() {
        var userDrive: UserDrive? = null
        var currentDrive: Drive? = null
        var disableSelectedFolderId: Int? = null
    }
}
