/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.lifecycle.distinctUntilChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.infomaniak.core.common.utils.FORMAT_DATE_CLEAR_MONTH
import com.infomaniak.core.common.utils.format
import com.infomaniak.core.common.utils.startOfTheDay
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.hideProgressCatching
import com.infomaniak.core.legacy.utils.initProgress
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.core.legacy.utils.showProgressCatching
import com.infomaniak.core.legacy.utils.startAppSettingsConfig
import com.infomaniak.core.legacy.utils.whenResultIsOk
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackPhotoSyncEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.data.models.SyncSettings
import com.infomaniak.drive.data.models.SyncSettings.IntervalType
import com.infomaniak.drive.data.models.SyncSettings.SavePicturesDate
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.ActivitySyncSettingsBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.BaseActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.SyncUtils.activateAutoSync
import com.infomaniak.drive.utils.SyncUtils.cancelPeriodicSync
import com.infomaniak.drive.utils.SyncUtils.disableAutoSync
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.views.SyncMediaSelectBottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Date
import java.util.TimeZone

@AndroidEntryPoint
class SyncSettingsActivity : BaseActivity() {

    private val binding: ActivitySyncSettingsBinding by lazy { ActivitySyncSettingsBinding.inflate(layoutInflater) }

    private val uiSettings by lazy { UiSettings(this) }

    private val syncPermissions = DrivePermissions(DrivePermissions.Type.ReadingMediaForSync).also {
        it.registerPermissions(this)
    }

    private val syncSettingsViewModel: SyncSettingsViewModel by viewModels()
    private val selectDriveViewModel: SelectDriveViewModel by viewModels()
    private var oldSyncSettings: SyncSettings? = null
    private var editNumber = 0

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            data?.extras?.let { bundle ->
                syncSettingsViewModel.syncFolderId.value = SelectFolderActivityArgs.fromBundle(bundle).folderId
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)
        addTwoFactorAuthOverlay()

        setOnBackPressed()

        activateSyncItem.isChecked = AccountUtils.isEnableAppSync()

        oldSyncSettings = UploadFile.getAppSyncSettings()

        initUserAndDrive()

        val oldIntervalTypeValue = oldSyncSettings?.getIntervalType() ?: IntervalType.IMMEDIATELY
        val oldSyncVideoValue = oldSyncSettings?.syncVideo != false
        val oldCreateDatedSubFoldersValue = oldSyncSettings?.createDatedSubFolders == true
        val oldDeleteAfterSyncValue = oldSyncSettings?.deleteAfterSync == true
        val oldSaveOldPicturesValue = SavePicturesDate.SINCE_NOW
        val oldOnlyWifiSyncMedia = oldSyncSettings?.onlyWifiSyncMedia == true

        syncSettingsViewModel.init(
            intervalTypeValue = oldIntervalTypeValue,
            syncFolderId = oldSyncSettings?.syncFolder,
            savePicturesDate = uiSettings.syncSettingsDate,
            onlyWifiSyncMedia = oldOnlyWifiSyncMedia,
        )

        setupListeners(oldSyncVideoValue, oldCreateDatedSubFoldersValue, oldDeleteAfterSyncValue)

        syncVideo.isChecked = oldSyncVideoValue
        createDatedSubFolders.isChecked = oldCreateDatedSubFoldersValue
        deletePicturesAfterSync.isChecked = oldDeleteAfterSyncValue

        observeAllUsers()
        observeCustomDate()
        observeSelectedDrive()

        observeSyncFolder()
        observeSaveOldPictures(oldSaveOldPicturesValue)

        observeSyncIntervalType(oldIntervalTypeValue)
        initOnlyWifiSyncMedia(oldOnlyWifiSyncMedia)

        binding.root.enableEdgeToEdge(shouldConsumeInsets = true, withBottom = false) {
            binding.saveButton.setMargins(bottom = resources.getDimension(R.dimen.marginStandard).toInt() + it.bottom)
        }
    }

    override fun onResume() {
        super.onResume()
        saveSettingVisibility(isVisible = binding.activateSyncItem.isChecked, showBatteryDialog = false)
    }

    private fun initUserAndDrive() {
        selectDriveViewModel.apply {
            val userId = oldSyncSettings?.userId ?: AccountUtils.currentUserId
            val drive = oldSyncSettings?.run { DriveInfosController.getDrive(userId, driveId) }
            selectedUserId.value = userId
            selectedDrive.value = drive
        }
    }

    private fun wontShowUnwantedPhotoPicker(): Boolean {
        val couldShowUnwantedPhotoPicker = when {
            SDK_INT >= 34 -> {
                checkSelfPermission(READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED
            }
            else -> false
        }
        return !couldShowUnwantedPhotoPicker
    }

    private fun ActivitySyncSettingsBinding.setupListeners(
        oldSyncVideoValue: Boolean,
        oldCreateDatedSubFoldersValue: Boolean,
        oldDeleteAfterSyncValue: Boolean,
    ) {
        setupSelectFolderListener()
        activateSync.setOnClickListener { activateSyncItem.isChecked = !activateSyncItem.isChecked }
        activateSyncItem.setOnCheckedChangeListener { _, isChecked ->
            saveSettingVisibility(isVisible = isChecked, showBatteryDialog = isChecked)
            if (AccountUtils.isEnableAppSync() == isChecked) editNumber-- else editNumber++
            if (isChecked && wontShowUnwantedPhotoPicker()) {
                // For sync to work, we need to have full access to images, and the photo picker that
                // opens starting from second permission request won't do it.
                // We only request permissions if user haven't chosen the "selected image" permission to avoid spamming him
                // with this unhelpful files choosing UI.
                syncPermissions.hasNeededPermissions(requestIfNotGranted = true, canShowBatteryDialog = false)
            }
            changeSaveButtonStatus()
        }

        photoAccessDeniedButton.setOnClickListener { startAppSettingsConfig() }

        mediaFolders.setOnClickListener {
            SelectMediaFoldersDialog().show(supportFragmentManager, "SyncSettingsSelectMediaFoldersDialog")
        }

        syncVideo.setOnCheckedChangeListener { _, isChecked ->
            if (oldSyncVideoValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        createDatedSubFolders.setOnCheckedChangeListener { _, isChecked ->
            if (oldCreateDatedSubFoldersValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        deletePicturesAfterSync.setOnCheckedChangeListener { _, isChecked ->
            if (oldDeleteAfterSyncValue == isChecked) editNumber-- else editNumber++
            changeSaveButtonStatus()
        }

        syncDate.setOnClickListener {
            SelectSaveDateBottomSheetDialog().show(supportFragmentManager, "SyncSettingsSelectSaveDateBottomSheetDialog")
        }

        syncDatePicker.setOnClickListener { showSyncDatePicker() }

        syncPeriodicity.setOnClickListener {
            SelectIntervalTypeBottomSheetDialog().show(supportFragmentManager, "SyncSettingsSelectIntervalTypeBottomSheetDialog")
        }

        syncOnlyWifi.setOnClickListener {
            SyncMediaSelectBottomSheetDialog().show(supportFragmentManager, SyncMediaSelectBottomSheetDialog::class.simpleName)
        }

        saveButton.initProgress(this@SyncSettingsActivity)
        saveButton.setOnClickListener {
            if (syncPermissions.hasNeededPermissions(requestIfNotGranted = true, canShowBatteryDialog = false)) {
                saveSettings()
            }
        }
    }

    private fun ActivitySyncSettingsBinding.setupSelectFolderListener() {
        selectPath.setOnClickListener {
            Intent(this@SyncSettingsActivity, SelectFolderActivity::class.java).apply {
                putExtras(
                    SelectFolderActivityArgs(
                        userId = selectDriveViewModel.selectedUserId.value!!,
                        driveId = selectDriveViewModel.selectedDrive.value?.id!!,
                        folderId = syncSettingsViewModel.syncFolderId.value ?: -1,
                    ).toBundle()
                )
                selectFolderResultLauncher.launch(this)
            }
        }
    }

    private fun observeAllUsers() {
        AccountUtils.getAllUsers().observe(this@SyncSettingsActivity) { users ->
            if (users.size > 1) {
                activeSelectDrive()
            } else {
                val currentUserId = AccountUtils.currentUserId
                val currentUserDrives = DriveInfosController.getDrives(currentUserId)
                if (currentUserDrives.size > 1) {
                    activeSelectDrive()
                } else {
                    val firstDrive = currentUserDrives.firstOrNull()
                    if (selectDriveViewModel.selectedUserId.value != currentUserId) {
                        selectDriveViewModel.selectedUserId.value = currentUserId
                    }
                    if (selectDriveViewModel.selectedDrive.value != firstDrive) {
                        selectDriveViewModel.selectedDrive.value = firstDrive
                    }
                }
            }
        }
    }

    private fun observeCustomDate() = with(binding) {
        syncSettingsViewModel.customDate.observe(this@SyncSettingsActivity) { date ->
            syncDatePicker.apply {
                isGone = date == null
                text = date?.format(FORMAT_DATE_CLEAR_MONTH) ?: ""
            }
        }
    }

    private fun observeSelectedDrive() = with(binding) {
        selectDriveViewModel.selectedDrive.distinctUntilChanged().observe(this@SyncSettingsActivity) {
            it?.let {
                selectDrive.setIconColor(it.preferences.color.toColorInt())
                selectDrive.title = it.name
                selectPath.isVisible = true
            } ?: run {
                selectDrive.setIconColor(ContextCompat.getColor(this@SyncSettingsActivity, R.color.iconColor))
                selectDrive.title = getString(R.string.selectDriveTitle)
                selectPath.isGone = true
            }
            if (selectDriveViewModel.selectedUserId.value != oldSyncSettings?.userId ||
                selectDriveViewModel.selectedDrive.value?.id != oldSyncSettings?.driveId ||
                syncSettingsViewModel.syncFolderId.value != oldSyncSettings?.syncFolder
            ) {
                syncSettingsViewModel.syncFolderId.value = null
            }
            changeSaveButtonStatus()
        }
    }

    private fun observeSyncFolder() = with(binding) {
        syncSettingsViewModel.syncFolderId.observe(this@SyncSettingsActivity) { syncFolderId ->

            val selectedUserId = selectDriveViewModel.selectedUserId.value
            val selectedDriveId = selectDriveViewModel.selectedDrive.value?.id
            if (syncFolderId != null && selectedUserId != null && selectedDriveId != null) {
                FileController.getFileById(syncFolderId, UserDrive(selectedUserId, selectedDriveId))?.let {
                    selectPath.setIconColor(it.color?.toColorInt() ?: context.getColor(R.color.folderDefaultColor))
                    selectPath.title = it.name
                    changeSaveButtonStatus()
                }
            } else {
                selectPath.title = getString(R.string.selectFolderTitle)
            }
            mediaFoldersSettingsVisibility(syncFolderId != null)
        }
    }

    private fun observeSaveOldPictures(oldSaveOldPicturesValue: SavePicturesDate) = with(binding) {
        syncSettingsViewModel.saveOldPictures.observe(this@SyncSettingsActivity) {
            if (it != oldSaveOldPicturesValue) editNumber++
            changeSaveButtonStatus()
            syncDateValue.text = getString(it.shortTitle).lowercase()
            if (it == SavePicturesDate.SINCE_DATE) {
                syncSettingsViewModel.customDate.value = Date().startOfTheDay()
                syncDatePicker.text = syncSettingsViewModel.customDate.value?.format(FORMAT_DATE_CLEAR_MONTH)
            } else {
                syncSettingsViewModel.customDate.value = null
            }
        }
    }

    private fun observeSyncIntervalType(oldIntervalTypeValue: IntervalType) = with(binding) {
        syncSettingsViewModel.syncIntervalType.observe(this@SyncSettingsActivity) {
            if (syncSettingsViewModel.syncIntervalType.value != oldIntervalTypeValue) editNumber++
            changeSaveButtonStatus()
            syncPeriodicity.endText = getString(it.title).lowercase()
        }
    }

    private fun initOnlyWifiSyncMedia(oldOnlyWifiSyncMedia: Boolean) {
        syncSettingsViewModel.onlyWifiSyncMedia.observe(this) {
            if (it != oldOnlyWifiSyncMedia) editNumber++ else editNumber--
            changeSaveButtonStatus()
            val descriptionResId = if (it) R.string.syncOnlyWifiTitle else R.string.syncWifiAndMobileDataTitle
            binding.syncOnlyWifi.description = getString(descriptionResId)
        }
    }

    private fun setOnBackPressed() {

        fun finishIfPossible() {
            if (editNumber > 0) {
                Utils.createConfirmation(
                    context = this,
                    title = getString(R.string.syncSettingsNotSavedTitle),
                    message = getString(R.string.syncSettingsNotSavedDescription),
                ) { finish() }
            } else {
                finish()
            }
        }

        binding.toolbar.setNavigationOnClickListener { finishIfPossible() }
        onBackPressedDispatcher.addCallback(this) { finishIfPossible() }
    }

    fun onDialogDismissed() {
        syncSettingsVisibility(MediaFolder.getAllSyncedFoldersCount() > 0)
        changeSaveButtonStatus()
    }

    private fun activeSelectDrive() = with(binding) {
        selectDrive.setAction(ItemSettingView.Action.Chevron)
        selectDrive.setOnClickListener { SelectDriveDialog().show(supportFragmentManager, "SyncSettingsSelectDriveDialog") }
    }

    private fun saveSettingVisibility(isVisible: Boolean, showBatteryDialog: Boolean) = with(binding) {
        val hasPermissions = syncPermissions.hasNeededPermissions(canShowBatteryDialog = showBatteryDialog)
        photoAccessDeniedLayout.isVisible = isVisible && !hasPermissions
        photoAccessDeniedTitle.setText(DrivePermissions.permissionNeededDescriptionRes)
        settingsLayout.isVisible = isVisible && hasPermissions

        mediaFoldersSettingsVisibility(isVisible && syncSettingsViewModel.syncFolderId.value != null)
        saveSettingsTitle.isVisible = isVisible
        saveSettingsLayout.isVisible = isVisible
    }

    private fun mediaFoldersSettingsVisibility(isVisible: Boolean) = with(binding) {
        syncSettingsVisibility(isVisible && MediaFolder.getAllSyncedFoldersCount() > 0)
        mediaFoldersSettingsTitle.isVisible = isVisible
        mediaFoldersSettingsLayout.isVisible = isVisible
    }

    private fun syncSettingsVisibility(isVisible: Boolean) = with(binding) {
        syncSettingsTitle.isVisible = isVisible
        syncSettingsLayout.isVisible = isVisible
    }

    private fun changeSaveButtonStatus() = with(binding) {
        val allSyncedFoldersCount = MediaFolder.getAllSyncedFoldersCount().toInt()
        val isEdited = (editNumber > 0)
                || (selectDriveViewModel.selectedUserId.value != oldSyncSettings?.userId)
                || (selectDriveViewModel.selectedDrive.value?.id != oldSyncSettings?.driveId)
                || (syncSettingsViewModel.syncFolderId.value != oldSyncSettings?.syncFolder)
                || (syncSettingsViewModel.saveOldPictures.value != SavePicturesDate.SINCE_NOW)
                || allSyncedFoldersCount > 0
        saveButton.isVisible = isEdited

        mediaFolders.title = if (allSyncedFoldersCount == 0) getString(R.string.noSelectMediaFolders)
        else resources.getQuantityString(R.plurals.mediaFoldersSelected, allSyncedFoldersCount, allSyncedFoldersCount)

        saveButton.isEnabled = isEdited && (selectDriveViewModel.selectedUserId.value != null)
                && (selectDriveViewModel.selectedDrive.value != null)
                && (syncSettingsViewModel.syncFolderId.value != null)
                && allSyncedFoldersCount > 0
    }

    private fun trackPhotoSyncEvents(syncSettings: SyncSettings) {

        val dateName = when (syncSettingsViewModel.saveOldPictures.value!!) {
            SavePicturesDate.SINCE_NOW -> MatomoName.SyncNew
            SavePicturesDate.SINCE_FOREVER -> MatomoName.SyncAll
            SavePicturesDate.SINCE_DATE -> MatomoName.SyncFromDate
        }

        trackPhotoSyncEvent(MatomoName.DeleteAfterImport, syncSettings.deleteAfterSync)
        trackPhotoSyncEvent(MatomoName.CreateDatedFolders, syncSettings.createDatedSubFolders)
        trackPhotoSyncEvent(MatomoName.ImportVideo, syncSettings.syncVideo)
        trackPhotoSyncEvent(dateName)
    }

    private fun saveSettings() = with(binding) {
        saveButton.showProgressCatching()

        lifecycleScope.launch {
            val result = runCatching {
                SentryLog.i(TAG, "start saveSettings on coroutineScope")
                if (activateSyncItem.isChecked) {
                    val syncSettings = generateSyncSettings()
                    trackPhotoSyncEvents(syncSettings)
                    syncSettings.setIntervalType(syncSettingsViewModel.syncIntervalType.value!!)
                    Dispatchers.IO {
                        SentryLog.i(TAG, "start update appSettings")
                        UploadFile.setAppSyncSettings(syncSettings)
                        SentryLog.i(TAG, "appSettings updated")
                        if (oldSyncSettings?.driveId != syncSettings.driveId) {
                            UploadFile.deleteAllSyncFile()
                            applicationContext.cancelPeriodicSync()
                            SentryLog.i(TAG, "New drive detected -> old sync files has been deleted")
                        }
                        applicationContext.activateAutoSync(syncSettings)
                        SentryLog.i(TAG, "auto sync enabled")
                    }
                } else {
                    Dispatchers.IO { disableAutoSync() }
                }

                trackPhotoSyncEvent(if (activateSyncItem.isChecked) MatomoName.Enabled else MatomoName.Disabled)
            }.onFailure { exception ->
                showSnackbar(R.string.anErrorHasOccurred)
                SentryLog.e("SyncSettings", "An error has occurred when save settings", exception) { scope ->
                    scope.setTag("syncIntervalType", syncSettingsViewModel.syncIntervalType.value?.title.toString())
                    scope.setTag("createMonthFolder", createDatedSubFolders.isChecked.toString())
                    scope.setTag("deletePhoto", deletePicturesAfterSync.isChecked.toString())
                    scope.setTag("coroutineScope.isActive", isActive.toString())
                    scope.setTag("lifecycle.currentState", lifecycle.currentState.name)
                }
            }

            saveButton.hideProgressCatching(R.string.buttonSave)

            if (result.isSuccess) finish()
        }
    }

    private fun generateSyncSettings(): SyncSettings = with(binding) {
        val date = when (syncSettingsViewModel.saveOldPictures.value!!) {
            SavePicturesDate.SINCE_NOW -> Date()
            SavePicturesDate.SINCE_FOREVER -> Date(-62_135_597_361L)
            SavePicturesDate.SINCE_DATE -> syncSettingsViewModel.customDate.value ?: Date()
        }
        return SyncSettings(
            userId = selectDriveViewModel.selectedUserId.value!!,
            driveId = selectDriveViewModel.selectedDrive.value!!.id,
            lastSync = date,
            syncFolder = syncSettingsViewModel.syncFolderId.value!!,
            syncVideo = syncVideo.isChecked,
            createDatedSubFolders = createDatedSubFolders.isChecked,
            deleteAfterSync = deletePicturesAfterSync.isChecked,
            onlyWifiSyncMedia = syncSettingsViewModel.onlyWifiSyncMedia.value ?: false,
        )
    }

    private fun showSyncDatePicker() {
        val calendarConstraints = CalendarConstraints.Builder()
            .setEnd(Date().time)
            .setValidator(DateValidatorPointBackward.now())
            .build()

        val dateToSet = syncSettingsViewModel.customDate.value ?: Date()

        MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.MaterialCalendarThemeBackground)
            .setSelection(
                // Need to account for time zones as MaterialDatePicker expects GMT+0. Else setting the picker to anything between
                // 02.01.22 00:00:00 and 02.01.22 00:59:59 results in the date picker being set to 01.01.22 instead when the device uses GMT+1
                dateToSet.time + TimeZone.getDefault().getOffset(dateToSet.time)
            )
            .setCalendarConstraints(calendarConstraints)
            .build()
            .apply {
                addOnPositiveButtonClickListener { syncSettingsViewModel.customDate.value = Date(it).startOfTheDay() }
                show(supportFragmentManager, "syncDatePicker")
            }
    }

    companion object {
        val TAG = SyncSettingsActivity::class.java.simpleName
    }
}
