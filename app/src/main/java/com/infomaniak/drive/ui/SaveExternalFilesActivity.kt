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
package com.infomaniak.drive.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.MediaStore.Files.FileColumns
import android.text.InputFilter
import android.text.Spanned
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import androidx.navigation.navArgs
import com.google.android.material.textfield.TextInputEditText
import com.infomaniak.core.utils.FORMAT_NEW_FILE
import com.infomaniak.core.utils.format
import com.infomaniak.drive.MatomoDrive.trackUserId
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.models.UiSettings.SaveExternalFilesData
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.databinding.ActivitySaveExternalFileBinding
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.menu.settings.SelectDriveDialog
import com.infomaniak.drive.ui.menu.settings.SelectDriveViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.lib.applock.LockActivity
import com.infomaniak.lib.core.utils.*
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder
import java.util.Date

class SaveExternalFilesActivity : BaseActivity() {

    private val binding by lazy { ActivitySaveExternalFileBinding.inflate(layoutInflater) }

    private val selectDriveViewModel: SelectDriveViewModel by viewModels()
    private val saveExternalFilesViewModel: SaveExternalFilesViewModel by viewModels()
    private val navigationArgs: SaveExternalFilesActivityArgs by navArgs()

    private lateinit var saveExternalUriAdapter: SaveExternalUriAdapter

    private val sharedFolder: IOFile by lazy {
        IOFile(cacheDir, SHARED_FILE_FOLDER).apply { if (!exists()) mkdirs() }
    }

    private val uiSettings by lazy { UiSettings(context = this) }

    private lateinit var drivePermissions: DrivePermissions
    private var currentUri: Uri? = null
    private var isMultiple = false

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            data?.extras?.let { bundle ->
                saveExternalFilesViewModel.folderId.value = SelectFolderActivityArgs.fromBundle(bundle).folderId
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(root)

        if (!isAuth() || isExtrasNull()) {
            finish()
            return
        }

        setupDrivePermissions()
        activeDefaultUser()
        trackUserId(AccountUtils.currentUserId)
        fetchSelectedDrive()
        fetchFolder()
        setupSaveButton()

        fileNameEdit.selectAllButFileExtension()

        LockActivity.scheduleLockIfNeeded(
            targetActivity = this@SaveExternalFilesActivity,
            isAppLockEnabled = { AppSettings.appSecurityLock }
        )
    }

    private fun TextInputEditText.selectAllButFileExtension() {
        setOnFocusChangeListener { _, hasFocus ->
            if (saveExternalFilesViewModel.firstFocus.value == true && hasFocus) {
                saveExternalFilesViewModel.firstFocus.value = false
                val fileName = (text ?: "").toString()
                val endIndex = File(name = fileName).getFileName().length
                post { setSelection(0, endIndex) }
            }
        }
    }

    private fun isAuth(): Boolean {
        if (AccountUtils.currentUserId == -1) {
            startActivity(Intent(this, LaunchActivity::class.java))
            return false
        }
        return true
    }

    private fun isExtrasNull(): Boolean {
        if (intent?.extras == null) {
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                Sentry.captureException(IllegalStateException("Activity $this has null extras in $intent"))
            }
            return true
        }
        return false
    }

    private fun setupDrivePermissions() {
        drivePermissions = DrivePermissions().apply {
            registerPermissions(
                activity = this@SaveExternalFilesActivity,
                onPermissionResult = { authorized -> if (authorized) getFiles() },
            )
            checkSyncPermissions()
        }
    }

    private fun activeDefaultUser() {
        AccountUtils.getAllUsers().observe(this) { users ->
            if (users.size > 1) activeSelectDrive()
        }
        val currentUserDrives = DriveInfosController.getDrives(AccountUtils.currentUserId)
        if (currentUserDrives.size > 1) activeSelectDrive()
        var (userId, driveId) = getSelectedFolder()
        var drive = DriveInfosController.getDrive(userId, driveId)
        if (drive == null) {
            userId = AccountUtils.currentUserId
            drive = AccountUtils.getCurrentDrive()
        }

        selectDriveViewModel.apply {
            selectedUserId.value = userId
            selectedDrive.value = drive
            showSharedWithMe = true
        }
    }

    private fun fetchSelectedDrive() = with(selectDriveViewModel) {
        selectedDrive.observe(this@SaveExternalFilesActivity) {
            it?.let { drive ->
                displaySelectedDrive(drive)
                binding.saveButton.isEnabled = false
                binding.pathTitle.isVisible = true
                setupSelectPath()
                with(getSelectedFolder()) {
                    saveExternalFilesViewModel.folderId.value = if (userId == selectedUserId.value && driveId == drive.id) {
                        folderId
                    } else {
                        null
                    }
                }
            } ?: run {
                displayDriveSelection()
            }
        }
    }

    private fun getSelectedFolder(): SaveExternalFilesData {
        return if (canUseExternalFilesPref()) {
            uiSettings.getSaveExternalFilesPref()
        } else {
            val folderId = if (navigationArgs.folderId == -1) {
                uiSettings.getSaveExternalFilesPref().folderId
            } else {
                navigationArgs.folderId
            }

            SaveExternalFilesData(navigationArgs.userId, navigationArgs.driveId, folderId)
        }
    }

    private fun canUseExternalFilesPref() = navigationArgs.userId == -1

    private fun canSaveFilesPref() = canUseExternalFilesPref() || navigationArgs.folderId == -1

    private fun displaySelectedDrive(drive: Drive) = with(binding) {
        driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
        driveName.text = drive.name
    }

    private fun displayDriveSelection() = with(binding) {
        driveIcon.imageTintList = ColorStateList.valueOf(
            ContextCompat.getColor(this@SaveExternalFilesActivity, R.color.iconColor),
        )
        driveName.setText(R.string.selectDriveTitle)
    }

    private fun setupSelectPath() {
        binding.selectPath.apply {
            isVisible = true
            setOnClickListener {
                Intent(this@SaveExternalFilesActivity, SelectFolderActivity::class.java).apply {
                    putExtras(
                        SelectFolderActivityArgs(
                            userId = selectDriveViewModel.selectedUserId.value!!,
                            driveId = selectDriveViewModel.selectedDrive.value?.id!!,
                            folderId = saveExternalFilesViewModel.folderId.value ?: -1,
                        ).toBundle()
                    )
                    selectFolderResultLauncher.launch(this)
                }
            }
        }
    }

    private fun fetchFolder() = with(selectDriveViewModel) {
        saveExternalFilesViewModel.folderId.observe(this@SaveExternalFilesActivity) { folderId ->

            val folder = if (selectedUserId.value == null || selectedDrive.value?.id == null
                || folderId == null
            ) {
                null
            } else {
                val userDrive = UserDrive(
                    userId = selectedUserId.value!!,
                    driveId = selectedDrive.value!!.id,
                    sharedWithMe = selectedDrive.value!!.sharedWithMe,
                )
                FileController.getFileById(folderId, userDrive)
            }

            folder?.let {
                binding.pathName.text = folder.name
                checkEnabledSaveButton()
            } ?: run {
                binding.pathName.setText(R.string.selectFolderTitle)
            }
        }
    }

    private fun setupSaveButton() = with(selectDriveViewModel) {
        binding.saveButton.apply {
            initProgress(this@SaveExternalFilesActivity)
            setOnClickListener {
                if (navigationArgs.isPublicShare) {
                    Intent().apply {
                        putExtra(DESTINATION_DRIVE_ID_KEY, selectDriveViewModel.selectedDrive.value?.id)
                        putExtra(DESTINATION_FOLDER_ID_KEY, saveExternalFilesViewModel.folderId.value)
                        setResult(RESULT_OK, this)
                    }
                    finish()
                    return@setOnClickListener
                }

                showProgressCatching()
                if (drivePermissions.checkSyncPermissions()) {
                    val userId = selectedUserId.value!!
                    val driveId = selectedDrive.value?.id!!
                    val folderId = saveExternalFilesViewModel.folderId.value!!

                    if (canSaveFilesPref()) uiSettings.setSaveExternalFilesPref(userId, driveId, folderId)

                    lifecycleScope.launch(Dispatchers.IO) {
                        if (storeFiles(userId, driveId, folderId)) {
                            syncImmediately()
                            finish()
                        } else {
                            withContext(Dispatchers.Main) {
                                hideProgressCatching(R.string.buttonSave)
                                showSnackbar(R.string.errorSave)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (drivePermissions.checkWriteStoragePermission(false)) getFiles()
    }

    private fun getFiles() {
        if (currentUri == null && !isMultiple) {
            try {
                when (intent?.action) {
                    Intent.ACTION_SEND -> handleSendSingle()
                    Intent.ACTION_SEND_MULTIPLE -> handleSendMultiple()
                }
            } catch (exception: Exception) {
                exception.printStackTrace()
                showSnackbar(R.string.anErrorHasOccurred)
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.WARNING
                    Sentry.captureException(exception)
                }
                finish()
            }
        }
    }

    private fun handleSendSingle() = with(binding) {

        fun getExtraTextFileName(): String {
            val extension = if (intent.getStringExtra(Intent.EXTRA_TEXT)?.isValidUrl() == true) ".url" else ".txt"
            val name = (intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "").let {
                it.ifEmpty { Date().format(FORMAT_NEW_FILE) }
            } + extension
            return name
        }

        fun getExtraStreamFileName(): String? {
            return (intent.parcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                currentUri = uri
                uri.fileName()
            }
        }

        fileNameEdit.addTextChangedListener {
            fileNameEdit.showOrHideEmptyError()
            checkEnabledSaveButton()
        }

        fileNameEdit.filters = arrayOf(
            object : InputFilter {
                override fun filter(
                    source: CharSequence?,
                    start: Int,
                    end: Int,
                    dest: Spanned?,
                    dstart: Int,
                    dend: Int
                ): CharSequence? = source?.replace(Regex("/"), "")
            },
            *fileNameEdit.filters
        )

        val fileName = when {
            intent.hasExtra(Intent.EXTRA_STREAM) -> getExtraStreamFileName() ?: return
            intent.hasExtra(Intent.EXTRA_TEXT) -> getExtraTextFileName()
            else -> return
        }

        fileNameEdit.setText(fileName)
        fileNameEditLayout.isVisible = true
    }

    private fun handleSendMultiple() = with(binding) {
        val uris = intent.parcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)
            ?.filterIsInstance<Uri>()
            ?.map { it to it.fileName() }
            ?: emptyList()

        saveExternalUriAdapter = SaveExternalUriAdapter(uris.toMutableList())

        fileNames.adapter = saveExternalUriAdapter
        fileNames.isVisible = true
        isMultiple = true
        checkEnabledSaveButton()
    }

    private fun checkEnabledSaveButton() {
        binding.saveButton.isEnabled = isValidFields()
    }

    private fun isValidFields(): Boolean {
        return (isMultiple || !binding.fileNameEdit.showOrHideEmptyError() || navigationArgs.isPublicShare) &&
                selectDriveViewModel.selectedUserId.value != null &&
                selectDriveViewModel.selectedDrive.value != null &&
                saveExternalFilesViewModel.folderId.value != null &&
                saveExternalFilesViewModel.folderId.value != OTHER_ROOT_ID
    }

    private fun activeSelectDrive() = with(binding) {
        switchDrive.isVisible = true
        selectDrive.setOnClickListener { SelectDriveDialog().show(supportFragmentManager, "SyncSettingsSelectDriveDialog") }
    }

    private fun storeFiles(userId: Int, driveId: Int, folderId: Int): Boolean = with(binding) {
        return try {
            when {
                isMultiple -> {
                    val adapter = fileNames.adapter as SaveExternalUriAdapter
                    adapter.uris.forEach { (uri, name) ->
                        if (!store(uri, name, userId, driveId, folderId)) return false
                    }
                    true
                }
                intent.hasExtra(Intent.EXTRA_STREAM) -> {
                    store(currentUri!!, fileNameEdit.text.toString().trim(), userId, driveId, folderId)
                }
                intent.hasExtra(Intent.EXTRA_TEXT) -> {
                    storeText(userId, driveId, folderId)
                }
                else -> false

            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            showSnackbar(R.string.anErrorHasOccurred)
            Sentry.withScope { scope ->
                scope.setExtra("lifecycleState", lifecycle.currentState.name)
                scope.setExtra("sharedFolderExists", sharedFolder.exists().toString())
                scope.level = SentryLevel.WARNING
                Sentry.captureException(exception)
            }
            false
        }
    }

    private fun storeText(userId: Int, driveId: Int, folderId: Int): Boolean {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            val fileName = binding.fileNameEdit.text.toString().trim()
            val lastModified = Date()
            val outputFile = getOutputFile(fileName)

            if (outputFile.createNewFile()) {
                outputFile.setLastModified(lastModified.time)

                if (fileName.isUrlFile()) { // Create url file
                    // See URL format http://www.lyberty.com/encyc/articles/tech/dot_url_format_-_an_unofficial_guide.html
                    outputFile.outputStream().use { output ->
                        output.write("[InternetShortcut]".toByteArray())
                        output.write("URL=$text".toByteArray())
                    }
                } else { // Create text file
                    outputFile.writeText(text)
                }

                UploadFile(
                    uri = outputFile.toUri().toString(),
                    userId = userId,
                    driveId = driveId,
                    remoteFolder = folderId,
                    type = UploadFile.Type.SHARED_FILE.name,
                    fileSize = outputFile.length(),
                    fileName = fileName,
                    fileCreatedAt = lastModified,
                    fileModifiedAt = lastModified
                ).store()

                return true
            }
        }
        return false
    }

    private fun getOutputFile(fileName: String) = IOFile(sharedFolder, fileName).also { if (it.exists()) it.delete() }

    private fun store(uri: Uri, fileName: String?, userId: Int, driveId: Int, folderId: Int): Boolean {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val lastModifiedDateFromUri = intent.getLongExtra(FileColumns.DATE_MODIFIED, -1L)
                val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor, lastModifiedDateFromUri)

                try {
                    if (fileName == null) return false

                    val outputFile = getOutputFile(fileName)

                    if (outputFile.createNewFile()) {
                        outputFile.setLastModified(fileModifiedAt.time)
                        contentResolver.openInputStream(uri)?.use { input ->
                            outputFile.outputStream().use { output ->
                                input.copyTo(output)
                            }
                        }

                        UploadFile(
                            uri = outputFile.toUri().toString(),
                            userId = userId,
                            driveId = driveId,
                            remoteFolder = folderId,
                            type = UploadFile.Type.SHARED_FILE.name,
                            fileSize = outputFile.length(),
                            fileName = fileName,
                            fileCreatedAt = fileCreatedAt,
                            fileModifiedAt = fileModifiedAt
                        ).store()
                        return true
                    }
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    return false
                }
            }
        }
        return false
    }

    private fun Uri.fileName(): String {
        return contentResolver.query(this, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getFileName(this) else null
        } ?: URLDecoder.decode(toString(), "UTF-8").substringAfterLast("/")
    }

    class SaveExternalFilesViewModel : ViewModel() {
        val folderId = MutableLiveData<Int?>()
        val firstFocus = MutableLiveData(true)
    }

    companion object {
        const val SHARED_FILE_FOLDER = "shared_files"
        const val LAST_MODIFIED_URI_KEY = "last_modified"
        const val DESTINATION_DRIVE_ID_KEY = "destination_drive_id"
        const val DESTINATION_FOLDER_ID_KEY = "destination_folder_id"
    }
}
