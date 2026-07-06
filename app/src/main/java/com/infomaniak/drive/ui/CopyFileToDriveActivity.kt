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
import androidx.core.graphics.toColorInt
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.infomaniak.core.common.extensions.isNightModeEnabled
import com.infomaniak.core.legacy.utils.whenResultIsOk
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ActivityCopyFileToDriveBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.CopyFileToDriveViewModel.CopyDestination
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.menu.settings.SelectDriveDialog
import com.infomaniak.drive.ui.menu.settings.SelectDriveViewModel
import com.infomaniak.drive.utils.SingleOperation
import com.infomaniak.drive.utils.Utils.TARGET_DRIVE_ID_TAG
import com.infomaniak.drive.views.FileInfoActionsView.Companion.SINGLE_OPERATION_CUSTOM_TAG
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class CopyFileToDriveActivity : BaseActivity() {

    private val binding by lazy { ActivityCopyFileToDriveBinding.inflate(layoutInflater) }

    private val selectDriveViewModel: SelectDriveViewModel by viewModels()
    private val copyFileToDriveViewModel: CopyFileToDriveViewModel by viewModels()

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            data?.extras?.let { bundle ->
                copyFileToDriveViewModel.onFolderSelected(SelectFolderActivityArgs.fromBundle(bundle).folderId)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        addTwoFactorAuthOverlay(isDarkTheme = isNightModeEnabled())

        binding.root.enableEdgeToEdge(withBottom = false)

        binding.toolbar.setNavigationOnClickListener { finish() }

        val sourceFile = copyFileToDriveViewModel.sourceFile
        if (sourceFile == null || copyFileToDriveViewModel.selectedDrive.value == null) {
            finish()
            return
        }

        binding.filePreview.adapter = CopyFilePreviewAdapter(listOf(sourceFile))

        selectDriveViewModel.apply {
            excludedDriveId = copyFileToDriveViewModel.sourceDriveId
            showUserSelection = false
            selectedUserId.value = copyFileToDriveViewModel.userId
        }

        if (copyFileToDriveViewModel.hasMultipleDrives) setupDriveSwitch()

        setupClickListeners()
        observeSelectedDrive()
        observeSelectedFolderName()
        observeCanCopy()
        observeDriveDialogSelection()
    }

    private fun setupClickListeners() = with(binding) {
        selectPath.setOnClickListener { openFolderSelection() }
        copyButton.setOnClickListener { copyFileToDriveViewModel.getCopyDestination()?.let(::finishWithResult) }
    }

    private fun observeSelectedDrive() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                copyFileToDriveViewModel.selectedDrive.filterNotNull().collect(::displaySelectedDrive)
            }
        }
    }

    private fun observeSelectedFolderName() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                copyFileToDriveViewModel.selectedFolderName.collect { folderName ->
                    binding.pathName.text = folderName ?: getString(R.string.selectFolderTitle)
                }
            }
        }
    }

    private fun observeCanCopy() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                copyFileToDriveViewModel.canCopy.collect { canCopy -> binding.copyButton.isEnabled = canCopy }
            }
        }
    }

    private fun observeDriveDialogSelection() {
        selectDriveViewModel.selectedDrive.observe(this) { drive ->
            drive?.let(copyFileToDriveViewModel::onDriveSelected)
        }
    }

    private fun displaySelectedDrive(drive: Drive) = with(binding) {
        driveIcon.imageTintList = ColorStateList.valueOf(drive.preferences.color.toColorInt())
        driveName.text = drive.name
        pathTitle.isVisible = true
        selectPath.isVisible = true
    }

    private fun setupDriveSwitch() = with(binding) {
        switchDrive.isVisible = true
        selectDrive.setOnClickListener {
            SelectDriveDialog().show(supportFragmentManager, "CopyFileToDriveSelectDriveDialog")
        }
    }

    private fun openFolderSelection() {
        val drive = copyFileToDriveViewModel.selectedDrive.value ?: return
        Intent(this, SelectFolderActivity::class.java).apply {
            putExtras(
                SelectFolderActivityArgs(
                    userId = copyFileToDriveViewModel.userId,
                    fromSaveExternal = true,
                    driveId = drive.id,
                ).toBundle()
            )
            selectFolderResultLauncher.launch(this)
        }
    }

    private fun finishWithResult(destination: CopyDestination) {
        Intent().apply {
            putExtras(
                SelectFolderActivityArgs(
                    folderId = destination.folderId,
                    folderName = destination.folderName ?: getString(R.string.selectFolderTitle),
                    customArgs = bundleOf(
                        SINGLE_OPERATION_CUSTOM_TAG to SingleOperation.COPY_TO_DRIVE.name,
                        TARGET_DRIVE_ID_TAG to destination.driveId,
                    )
                ).toBundle()
            )
            setResult(RESULT_OK, this)
        }
        finish()
    }
}
