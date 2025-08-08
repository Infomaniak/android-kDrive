/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ActivitySelectFolderBinding
import com.infomaniak.drive.extensions.onApplyWindowInsetsListener
import com.infomaniak.drive.ui.BaseActivity
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.menu.SharedWithMeFragment
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.utils.setMargins

class SelectFolderActivity : BaseActivity() {

    private val binding: ActivitySelectFolderBinding by lazy { ActivitySelectFolderBinding.inflate(layoutInflater) }

    private val navHostFragment by lazy { supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment }
    private val navController by lazy { navHostFragment.navController }

    private val selectFolderViewModel: SelectFolderViewModel by viewModels()
    private val mainViewModel: MainViewModel by viewModels()
    private val navigationArgs: SelectFolderActivityArgs by navArgs()

    private val navigationIds = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val userId = navigationArgs.userId
        val driveId = navigationArgs.driveId
        val fromSaveExternal = navigationArgs.fromSaveExternal
        val customArgs = navigationArgs.customArgs
        val currentFolderId = navigationArgs.folderId.getIntOrNull()
        val disabledFolderId = navigationArgs.disabledFolderId.getIntOrNull()

        // We're doing this in the mainthread because the FileListFragment rely on mainViewModel.selectFolderUserDrive.
        // Moving this call in a background thread we'll break everything
        DriveInfosController.getDrive(userId = userId, driveId = driveId, maintenance = false)?.let { selectedDrive ->
            val isSharedWithMe = selectedDrive.sharedWithMe
            val currentUserDrive = UserDrive(userId, driveId, isSharedWithMe)
            mainViewModel.selectFolderUserDrive = currentUserDrive

            selectFolderViewModel.apply {
                userDrive = currentUserDrive
                currentDrive = DriveInfosController.getDrive(userId, driveId)
                disableSelectedFolderId = disabledFolderId
            }

            navController.setGraph(
                R.navigation.select_folder_navigation,
                SelectRootFolderFragmentArgs(
                    driveId = driveId,
                    fromSaveExternal = fromSaveExternal,
                    userDrive = currentUserDrive
                ).toBundle()
            )

            setSaveButton(customArgs)

            currentFolderId?.let { folderId ->
                // Simply navigate when the folder exists in the local database
                FileController.getFileProxyById(
                    fileId = folderId,
                    userDrive = currentUserDrive,
                    customRealm = getCustomRealm(currentUserDrive),
                )?.let {
                    initiateNavigationToCurrentFolder(folderId, currentUserDrive)
                }
            }
        }

        binding.saveButton.onApplyWindowInsetsListener { view, windowInsets ->
            view.setMargins(bottom = resources.getDimension(R.dimen.marginStandard).toInt() + windowInsets.bottom)
        }
    }

    private fun Int.getIntOrNull(): Int? = if (this <= 0) null else this

    private fun setSaveButton(customArgs: Bundle?) = with(binding) {
        saveButton.setOnClickListener {
            val currentFragment = navHostFragment.childFragmentManager.fragments.first() as FileListFragment
            Intent().apply {
                putExtras(
                    SelectFolderActivityArgs(
                        folderId = currentFragment.folderId,
                        folderName = currentFragment.folderName,
                        customArgs = customArgs,
                        isSharedWithMe = navHostFragment.childFragmentManager.fragments.first() is SharedWithMeFragment
                    ).toBundle()
                )
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    private fun getCustomRealm(currentUserDrive: UserDrive) = if (currentUserDrive.sharedWithMe) null else mainViewModel.realm

    private fun initiateNavigationToCurrentFolder(folderId: Int, userDrive: UserDrive) {
        generateNavigationIds(folderId, userDrive)
        navigateToCurrentFolder(userDrive)
    }

    private fun generateNavigationIds(folderId: Int, userDrive: UserDrive) = with(navigationIds) {
        add(folderId)
        addNavigationIdsRecursively(folderId, userDrive)
        reverse()
    }

    private fun MutableList<Int>.addNavigationIdsRecursively(folderId: Int, userDrive: UserDrive) {
        FileController.getParentFileProxy(folderId, userDrive, getCustomRealm(userDrive))?.id?.let { parentId ->
            if (parentId != Utils.ROOT_ID) {
                add(parentId)
                addNavigationIdsRecursively(parentId, userDrive)
            }
        }
    }

    private fun navigateToCurrentFolder(currentUserDrive: UserDrive) {
        // Making sure the current backstack entry is selectRootFolderFragment because it'll generate a
        // crash when "Don't keep activities" is activated
        navController.popBackStack(R.id.selectRootFolderFragment, false)

        navigationIds.forEachIndexed { index, folderId ->
            if (index == 0) {
                navController.navigate(
                    SelectRootFolderFragmentDirections.selectRootFolderFragmentToSelectFolderFragment(
                        folderId = folderId,
                        userDrive = currentUserDrive
                    )
                )
            } else {
                navController.navigate(
                    SelectFolderFragmentDirections.fileListFragmentToFileListFragment(
                        folderId = folderId,
                        userDrive = currentUserDrive
                    )
                )
            }
        }
    }

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

        fun getFolderName(folderId: Int): String {
            val selectedFolderName = if (folderId == ROOT_ID) {
                currentDrive?.name
            } else {
                FileController.getFileById(folderId, userDrive)?.name
            }
            return selectedFolderName ?: "/"
        }
    }
}
