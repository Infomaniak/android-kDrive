/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.navigation.navArgs
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.infomaniak.core.common.extensions.isNightModeEnabled
import com.infomaniak.core.legacy.utils.whenResultIsOk
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ActivityCopyFileToDriveBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.menu.settings.SelectDriveDialog
import com.infomaniak.drive.ui.menu.settings.SelectDriveViewModel
import com.infomaniak.drive.utils.SingleOperation
import com.infomaniak.drive.utils.Utils.TARGET_DRIVE_ID_TAG
import com.infomaniak.drive.views.FileInfoActionsView.Companion.SINGLE_OPERATION_CUSTOM_TAG
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class CopyFileToDriveActivity : BaseActivity() {

    private val binding by lazy { ActivityCopyFileToDriveBinding.inflate(layoutInflater) }

    private val navigationArgs: CopyFileToDriveActivityArgs by navArgs()

    private val selectDriveViewModel: SelectDriveViewModel by viewModels()
    private val copyFileToDriveViewModel: CopyFileToDriveViewModel by viewModels()

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            data?.extras?.let { bundle ->
                copyFileToDriveViewModel.folderId.value = SelectFolderActivityArgs.fromBundle(bundle).folderId
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        addTwoFactorAuthOverlay(isDarkTheme = isNightModeEnabled())

        binding.root.enableEdgeToEdge(withBottom = false)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val fileIds = navigationArgs.fileIds
        val sourceDriveId = navigationArgs.sourceDriveId
        val userId = navigationArgs.userId

        val isSourceSharedWithMe = DriveInfosController.getDrive(driveId = sourceDriveId, sharedWithMe = null)?.sharedWithMe == true
        val userDrive = UserDrive(userId = userId, driveId = sourceDriveId, sharedWithMe = isSourceSharedWithMe)
        val sourceFiles = fileIds.toList().mapNotNull { id ->
            if (id != -1) FileController.getFileById(id, userDrive) else null
        }

        if (sourceFiles.isEmpty() || fileIds.isEmpty() || sourceDriveId == -1 || userId == -1) {
            finish()
            return
        }

        binding.filePreview.adapter = CopyFilePreviewAdapter(sourceFiles)

        configureDefaultDrive(sourceDriveId, userId)
        setupDriveSelection()
        setupFolderSelection()
        setupCopyButton()
    }

    private fun configureDefaultDrive(sourceDriveId: Int, userId: Int) {
        selectDriveViewModel.apply {
            excludedDriveId = sourceDriveId
            showUserSelection = false
        }

        val currentUserDrives = DriveInfosController.getEligibleDestinationDrives(userId, sourceDriveId)
        if (currentUserDrives.size > 1) setupDriveSwitch()

        val drive = currentUserDrives.firstOrNull()
        if (drive == null) {
            finish()
            return
        }

        selectDriveViewModel.apply {
            selectedUserId.value = userId
            selectedDrive.value = drive
        }
    }

    private fun setupDriveSelection() = with(selectDriveViewModel) {
        selectedDrive.observe(this@CopyFileToDriveActivity) {
            it?.let { drive ->
                displaySelectedDrive(drive)
                binding.pathTitle.isVisible = true
                binding.selectPath.isVisible = true
                copyFileToDriveViewModel.folderId.value = null
                checkEnabledCopyButton()
            } ?: run {
                displayDriveSelection()
                binding.pathTitle.isVisible = false
                binding.selectPath.isVisible = false
            }
        }
    }

    private fun displaySelectedDrive(drive: Drive) = with(binding) {
        driveIcon.imageTintList = ColorStateList.valueOf(drive.preferences.color.toColorInt())
        driveName.text = drive.name
    }

    private fun displayDriveSelection() = with(binding) {
        driveIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this@CopyFileToDriveActivity, R.color.iconColor),
        )
        driveName.setText(R.string.selectDriveTitle)
    }

    private fun setupFolderSelection() = with(copyFileToDriveViewModel) {
        folderId.observe(this@CopyFileToDriveActivity) { folderId ->
            if (folderId != null) {
                val drive = selectDriveViewModel.selectedDrive.value ?: return@observe
                val userId = selectDriveViewModel.selectedUserId.value ?: return@observe

                val userDrive = UserDrive(userId = userId, driveId = drive.id, sharedWithMe = drive.sharedWithMe)
                val folder = FileController.getFileById(folderId, userDrive)

                binding.pathName.text = folder?.name ?: getString(R.string.selectFolderTitle)
                checkEnabledCopyButton()
            } else {
                binding.pathName.setText(R.string.selectFolderTitle)
                checkEnabledCopyButton()
            }
        }
    }

    private fun setupCopyButton() = with(binding) {
        selectPath.setOnClickListener {
            val drive = selectDriveViewModel.selectedDrive.value ?: return@setOnClickListener
            val userId = selectDriveViewModel.selectedUserId.value ?: return@setOnClickListener

            Intent(this@CopyFileToDriveActivity, SelectFolderActivity::class.java).apply {
                putExtras(
                    SelectFolderActivityArgs(
                        userId = userId,
                        fromSaveExternal = true,
                        driveId = drive.id,
                    ).toBundle()
                )
                selectFolderResultLauncher.launch(this)
            }
        }

        copyButton.setOnClickListener {
            val drive = selectDriveViewModel.selectedDrive.value ?: return@setOnClickListener
            val userId = selectDriveViewModel.selectedUserId.value ?: return@setOnClickListener
            val folderId = copyFileToDriveViewModel.folderId.value ?: return@setOnClickListener

            val userDrive = UserDrive(userId = userId, driveId = drive.id, sharedWithMe = drive.sharedWithMe)
            val folder = FileController.getFileById(folderId, userDrive)
            val folderName = folder?.name ?: getString(R.string.selectFolderTitle)

            Intent().apply {
                putExtras(
                    SelectFolderActivityArgs(
                        folderId = folderId,
                        folderName = folderName,
                        customArgs = bundleOf(
                            SINGLE_OPERATION_CUSTOM_TAG to SingleOperation.COPY_TO_DRIVE.name,
                            TARGET_DRIVE_ID_TAG to drive.id,
                        )
                    ).toBundle()
                )
                setResult(RESULT_OK, this)
            }
            finish()
        }
    }

    private fun setupDriveSwitch() = with(binding) {
        switchDrive.isVisible = true
        selectDrive.setOnClickListener {
            SelectDriveDialog().show(supportFragmentManager, "CopyFileToDriveSelectDriveDialog")
        }
    }

    private fun checkEnabledCopyButton() {
        val canCopy = selectDriveViewModel.selectedUserId.value != null &&
                selectDriveViewModel.selectedDrive.value != null &&
                copyFileToDriveViewModel.folderId.value != null

        binding.copyButton.isEnabled = canCopy
    }

    class CopyFileToDriveViewModel : ViewModel() {
        val folderId = MutableLiveData<Int?>()
    }
}
