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
package com.infomaniak.drive.ui.menu.settings

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View.GONE
import android.view.View.VISIBLE
import androidx.core.content.ContextCompat
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.BaseActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.SyncUtils.activateAutoSync
import com.infomaniak.drive.utils.SyncUtils.checkSyncPermissions
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.activity_sync_settings.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SyncSettingsActivity : BaseActivity() {

    private lateinit var syncSettingsViewModel: SyncSettingsViewModel
    private lateinit var selectDriveViewModel: SelectDriveViewModel
    private var oldSyncSettings: SyncSettings? = null
    private var editNumber = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sync_settings)
        syncSettingsViewModel = ViewModelProvider(this)[SyncSettingsViewModel::class.java]
        selectDriveViewModel = ViewModelProvider(this)[SelectDriveViewModel::class.java]

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        activateSyncSwitch.isChecked = AccountUtils.isEnableAppSync()
        showSettings(activateSyncSwitch.isChecked)

        oldSyncSettings = UploadFile.getAppSyncSettings()

        selectDriveViewModel.selectedUserId.value = oldSyncSettings?.userId
        selectDriveViewModel.selectedDrive.value = oldSyncSettings?.run {
            DriveInfosController.getDrives(this.userId, driveId = this.driveId).firstOrNull()
        }
        syncSettingsViewModel.syncIntervalType.value = oldSyncSettings?.getIntervalType() ?: SyncSettings.IntervalType.IMMEDIATELY
        syncSettingsViewModel.syncFolder.value = oldSyncSettings?.syncFolder

        AccountUtils.getAllUsers().observe(this) { users ->
            if (users.size > 1) {
                activeSelectDrive()
            } else {
                val currentUserDrives = DriveInfosController.getDrives(AccountUtils.currentUserId)
                if (currentUserDrives.size > 1) {
                    activeSelectDrive()
                } else {
                    selectDriveViewModel.selectedUserId.value = AccountUtils.currentUserId
                    selectDriveViewModel.selectedDrive.value = currentUserDrives.firstOrNull()
                }
            }
        }

        selectDriveViewModel.selectedDrive.observe(this) {
            it?.let {
                driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(it.preferences.color))
                driveName.text = it.name
                selectDivider.visibility = VISIBLE
                selectPath.visibility = VISIBLE
            } ?: run {
                driveIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.iconColor))
                driveName.setText(R.string.selectDriveTitle)
                selectDivider.visibility = GONE
                selectPath.visibility = GONE
            }
            if (selectDriveViewModel.selectedUserId.value != oldSyncSettings?.userId ||
                selectDriveViewModel.selectedDrive.value?.id != oldSyncSettings?.driveId ||
                syncSettingsViewModel.syncFolder.value != oldSyncSettings?.syncFolder
            ) {
                syncSettingsViewModel.syncFolder.value = null
            }
            changeSaveButtonStatus()
        }

        selectPath.setOnClickListener {
            val intent = Intent(this, SelectFolderActivity::class.java).apply {
                putExtra(SelectFolderActivity.USER_ID_TAG, selectDriveViewModel.selectedUserId.value)
                putExtra(SelectFolderActivity.USER_DRIVE_ID_TAG, selectDriveViewModel.selectedDrive.value?.id)
                putExtra(SelectFolderActivity.DISABLE_SELECTED_FOLDER, Utils.ROOT_ID)
            }
            startActivityForResult(intent, SelectFolderActivity.SELECT_FOLDER_REQUEST)
        }

        syncSettingsViewModel.syncFolder.observe(this) { syncFolder ->
            syncFolder?.let {
                FileController.getFileById(
                    syncFolder,
                    UserDrive(selectDriveViewModel.selectedUserId.value!!, selectDriveViewModel.selectedDrive.value?.id!!)
                )?.let {
                    pathName.text = it.name
                    changeSaveButtonStatus()
                }
            } ?: run {
                pathName.setText(R.string.selectFolderTitle)
            }
        }

        syncSettingsViewModel.saveOldPictures.observe(this) {
            syncDateValue.text = if (it == true) {
                getString(R.string.syncSettingsSaveDateAllPictureValue).toLowerCase(Locale.ROOT)
            } else {
                getString(R.string.syncSettingsSaveDateNowValue)
            }
            changeSaveButtonStatus()
        }

        syncSettingsViewModel.syncIntervalType.observe(this) {
            syncPeriodicityValue.setText(it.title)
        }

        val defaultValue = true
        syncPictureSwitch.isChecked = oldSyncSettings?.syncPicture ?: defaultValue
        syncVideoSwitch.isChecked = oldSyncSettings?.syncVideo ?: defaultValue
        syncScreenshotSwitch.isChecked = oldSyncSettings?.syncScreenshot ?: defaultValue

        activateSync.setOnClickListener { activateSyncSwitch.isChecked = !activateSyncSwitch.isChecked }
        activateSyncSwitch.setOnCheckedChangeListener { _, isChecked ->
            showSettings(isChecked)
            if (AccountUtils.isEnableAppSync() == isChecked) editNumber-- else editNumber++
            if (isChecked) checkSyncPermissions()
            changeSaveButtonStatus()
        }
        syncPictureSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldSyncSettings?.syncPicture ?: defaultValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }
        syncVideoSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldSyncSettings?.syncVideo ?: defaultValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }
        syncScreenshotSwitch.setOnCheckedChangeListener { _, isChecked ->
            if (oldSyncSettings?.syncScreenshot ?: defaultValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        syncDate.setOnClickListener {
            var syncOldMedia = syncSettingsViewModel.saveOldPictures.value == true
            val items = arrayOf(
                getString(R.string.syncSettingsSaveDateNowValue2),
                getString(R.string.syncSettingsSaveDateAllPictureValue)
            )
            val checkedItem = if (syncOldMedia) 1 else 0
            MaterialAlertDialogBuilder(this, R.style.DialogStyle)
                .setTitle(getString(R.string.syncSettingsButtonSaveDate))
                .setSingleChoiceItems(items, checkedItem) { _, which ->
                    syncOldMedia = which == 1
                }
                .setPositiveButton(R.string.buttonConfirm) { _, _ ->
                    syncSettingsViewModel.saveOldPictures.value = syncOldMedia
                }
                .setNegativeButton(R.string.buttonCancel) { _, _ -> }
                .setCancelable(false).show()
        }

        syncPeriodicity.setOnClickListener {
            var syncIntervalType = syncSettingsViewModel.syncIntervalType.value!!
            val choiseItems: ArrayList<String> = arrayListOf()
            val intervalTypeList: ArrayList<SyncSettings.IntervalType> = arrayListOf()
            for (intervalType in SyncSettings.IntervalType.values()) {
                if (Build.VERSION.SDK_INT >= intervalType.minAndroidSdk) {
                    choiseItems.add(getString(intervalType.title))
                    intervalTypeList.add(intervalType)
                }
            }
            val checkedItem = intervalTypeList.indexOfFirst { it == syncIntervalType }

            MaterialAlertDialogBuilder(this, R.style.DialogStyle)
                .setTitle(getString(R.string.syncSettingsButtonSyncPeriodicity))
                .setSingleChoiceItems(choiseItems.toTypedArray(), checkedItem) { _, position ->
                    syncIntervalType = intervalTypeList[position]
                }
                .setPositiveButton(R.string.buttonConfirm) { _, _ ->
                    if (syncSettingsViewModel.syncIntervalType.value != syncIntervalType) editNumber++
                    changeSaveButtonStatus()
                    syncSettingsViewModel.syncIntervalType.value = syncIntervalType
                }
                .setNegativeButton(R.string.buttonCancel) { _, _ -> }
                .setCancelable(false).show()
        }

        saveButton.initProgress(this)
        saveButton.setOnClickListener {
            if (checkSyncPermissions()) saveSettings()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SelectFolderActivity.SELECT_FOLDER_REQUEST && resultCode == RESULT_OK) {
            syncSettingsViewModel.syncFolder.value = data?.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)
        }
    }

    private fun activeSelectDrive() {
        switchDrive.visibility = VISIBLE
        selectDrive.setOnClickListener {
            SelectDriveDialog().show(supportFragmentManager, "SyncSettingsSelectDriveDialog")
        }
    }

    private fun showSettings(isChecked: Boolean) {
        val visibility = if (isChecked) VISIBLE else GONE
        saveSettingsTitle.visibility = visibility
        saveSettingsLayout.visibility = visibility
        syncSettingsTitle.visibility = visibility
        syncSettingsLayout.visibility = visibility
    }

    private fun changeSaveButtonStatus() {
        val isEdited = (editNumber > 0)
                || (selectDriveViewModel.selectedUserId.value != oldSyncSettings?.userId)
                || (selectDriveViewModel.selectedDrive.value?.id != oldSyncSettings?.driveId)
                || (syncSettingsViewModel.syncFolder.value != oldSyncSettings?.syncFolder)
                || (syncSettingsViewModel.saveOldPictures.value != null)
        saveButton.visibility = if (isEdited) VISIBLE else GONE

        saveButton.isEnabled = isEdited && (selectDriveViewModel.selectedUserId.value != null)
                && (selectDriveViewModel.selectedDrive.value != null)
                && (syncSettingsViewModel.syncFolder.value != null)
    }

    private fun saveSettings() {
        saveButton.showProgress()
        lifecycleScope.launch(Dispatchers.IO) {
            val saveOldPictures = syncSettingsViewModel.saveOldPictures.value == true

            if (activateSyncSwitch.isChecked) {
                val syncSettings = SyncSettings(
                    userId = selectDriveViewModel.selectedUserId.value!!,
                    driveId = selectDriveViewModel.selectedDrive.value!!.id,
                    lastSync = if (saveOldPictures) Date(0) else Date(),
                    syncFolder = syncSettingsViewModel.syncFolder.value!!,
                    syncPicture = syncPictureSwitch.isChecked,
                    syncScreenshot = syncScreenshotSwitch.isChecked,
                    syncVideo = syncVideoSwitch.isChecked
                )
                syncSettings.setIntervalType(syncSettingsViewModel.syncIntervalType.value!!)
                activateAutoSync(syncSettings)
            } else {
                disableAutoSync()
            }

            withContext(Dispatchers.Main) {
                onBackPressed()
            }
        }
    }

    class SyncSettingsViewModel : ViewModel() {
        val saveOldPictures = MutableLiveData<Boolean>()
        val syncIntervalType = MutableLiveData<SyncSettings.IntervalType>()
        val syncFolder = MutableLiveData<Int?>()
    }
}