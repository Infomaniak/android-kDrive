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
package com.infomaniak.drive.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.menu.settings.SelectDriveDialog
import com.infomaniak.drive.ui.menu.settings.SelectDriveViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.MatomoUtils.trackCurrentUserId
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.utils.*
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.android.synthetic.main.activity_save_external_file.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SaveExternalFilesActivity : BaseActivity() {

    private val selectDriveViewModel: SelectDriveViewModel by viewModels()
    private val saveExternalFilesViewModel: SaveExternalFilesViewModel by viewModels()

    private lateinit var saveExternalUriAdapter: SaveExternalUriAdapter

    private val sharedFolder: java.io.File by lazy {
        java.io.File(cacheDir, SHARED_FILE_FOLDER).apply { if (!exists()) mkdirs() }
    }

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_external_file)

        if (!isAuth()) return

        setupDrivePermissions()
        activeDefaultUser()
        application.trackCurrentUserId()
        fetchSelectedDrive()
        fetchFolder()
        setupSaveButton()

        fileNameEdit.selectAllButFileExtension()
    }

    private fun TextInputEditText.selectAllButFileExtension() {
        setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                val fileName = (text ?: "").toString()
                val endIndex = fileName.substringBeforeLast(".").length
                post { setSelection(0, endIndex) }
            }
        }
    }

    private fun isAuth(): Boolean {
        if (AccountUtils.currentUserId == -1) {
            startActivity(Intent(this, LaunchActivity::class.java))
            finish()
            return false
        }
        return true
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

        var (userId, driveId) = UiSettings(this).getSaveExternalFilesPref()
        var drive = DriveInfosController.getDrives(userId, driveId).firstOrNull()
        if (drive == null) {
            userId = AccountUtils.currentUserId
            drive = AccountUtils.getCurrentDrive()
        }

        selectDriveViewModel.apply {
            selectedUserId.value = userId
            selectedDrive.value = drive
        }
    }

    private fun fetchSelectedDrive() = with(selectDriveViewModel) {
        selectedDrive.observe(this@SaveExternalFilesActivity) {
            it?.let { drive ->
                displaySelectedDrive(drive)
                saveButton.isEnabled = false
                pathTitle.isVisible = true
                setupSelectPath()
                UiSettings(this@SaveExternalFilesActivity).getSaveExternalFilesPref().let { (userId, driveId, folderId) ->
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

    private fun displaySelectedDrive(drive: Drive) {
        driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(drive.preferences.color))
        driveName.text = drive.name
    }

    private fun displayDriveSelection() {
        driveIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.iconColor))
        driveName.setText(R.string.selectDriveTitle)
    }

    private fun setupSelectPath() {
        selectPath.apply {
            isVisible = true
            setOnClickListener {
                Intent(this@SaveExternalFilesActivity, SelectFolderActivity::class.java).apply {
                    putExtras(
                        SelectFolderActivityArgs(
                            userId = selectDriveViewModel.selectedUserId.value!!,
                            userDriveId = selectDriveViewModel.selectedDrive.value?.id!!,
                            currentFolderId = saveExternalFilesViewModel.folderId.value ?: -1,
                        ).toBundle()
                    )
                    selectFolderResultLauncher.launch(this)
                }
            }
        }
    }

    private fun fetchFolder() = with(selectDriveViewModel) {
        saveExternalFilesViewModel.folderId.observe(this@SaveExternalFilesActivity) { folderId ->

            val folder = if (selectedUserId.value == null || selectedDrive.value?.id == null || folderId == null) {
                null
            } else {
                val userDrive = UserDrive(userId = selectedUserId.value!!, driveId = selectedDrive.value!!.id)
                FileController.getFileById(folderId, userDrive)
            }

            folder?.let {
                val folderName = if (folder.isRoot()) {
                    getString(R.string.allRootName, selectedDrive.value?.name)
                } else {
                    folder.name
                }
                pathName.text = folderName
                checkEnabledSaveButton()
            } ?: run {
                pathName.setText(R.string.selectFolderTitle)
            }
        }
    }

    private fun setupSaveButton() = with(selectDriveViewModel) {
        saveButton.apply {
            initProgress(this@SaveExternalFilesActivity)
            setOnClickListener {
                showProgress()
                if (drivePermissions.checkSyncPermissions()) {
                    val userId = selectedUserId.value!!
                    val driveId = selectedDrive.value?.id!!
                    val folderId = saveExternalFilesViewModel.folderId.value!!

                    UiSettings(this@SaveExternalFilesActivity).setSaveExternalFilesPref(userId, driveId, folderId)
                    lifecycleScope.launch(Dispatchers.IO) {
                        if (storeFiles(userId, driveId, folderId)) {
                            syncImmediately()
                            finish()
                        } else {
                            withContext(Dispatchers.Main) {
                                saveButton?.hideProgress(R.string.buttonSave)
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

    private fun handleSendSingle() {
        fileNameEdit.addTextChangedListener {
            fileNameEdit.showOrHideEmptyError()
            checkEnabledSaveButton()
        }

        var showEditText = false

        if (intent.hasExtra(Intent.EXTRA_TEXT)) {
            val extension = if (intent.getStringExtra(Intent.EXTRA_TEXT)?.isValidUrl() == true) ".url" else ".txt"
            val name = (intent.getStringExtra(Intent.EXTRA_SUBJECT) ?: "").let {
                if (it.isEmpty()) Date().format(FORMAT_NEW_FILE)
                else it
            } + extension

            fileNameEdit.setText(name)
            showEditText = true

        } else {
            (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
                currentUri = uri
                showEditText = true
                fileNameEdit.setText(uri.fileName())
            }
        }

        fileNameEditLayout.isVisible = showEditText
    }

    private fun handleSendMultiple() {
        val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.map { it as Uri } ?: arrayListOf()

        saveExternalUriAdapter = SaveExternalUriAdapter(uris as ArrayList<Uri>)

        fileNames.adapter = saveExternalUriAdapter
        fileNames.isVisible = true
        isMultiple = true
        checkEnabledSaveButton()
    }

    private fun checkEnabledSaveButton() {
        saveButton.isEnabled = isValidFields()
    }

    private fun isValidFields(): Boolean {
        return (isMultiple || !fileNameEdit.showOrHideEmptyError()) &&
                selectDriveViewModel.selectedUserId.value != null &&
                selectDriveViewModel.selectedDrive.value != null &&
                saveExternalFilesViewModel.folderId.value != null
    }

    private fun activeSelectDrive() {
        switchDrive.isVisible = true
        selectDrive.setOnClickListener { SelectDriveDialog().show(supportFragmentManager, "SyncSettingsSelectDriveDialog") }
    }

    private fun storeFiles(userId: Int, driveId: Int, folderId: Int): Boolean {
        return try {
            when {
                isMultiple -> {
                    val adapter = fileNames.adapter as SaveExternalUriAdapter
                    adapter.uris.forEach { uri ->
                        if (!store(uri, saveExternalUriAdapter.getFileName(uri), userId, driveId, folderId)) return false
                    }
                    true
                }
                intent.hasExtra(Intent.EXTRA_TEXT) -> storeText(userId, driveId, folderId)
                else -> store(currentUri!!, fileNameEdit.text.toString(), userId, driveId, folderId)

            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            showSnackbar(R.string.anErrorHasOccurred)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.WARNING
                Sentry.captureException(exception)
            }
            false
        }
    }

    private fun storeText(userId: Int, driveId: Int, folderId: Int): Boolean {
        intent.getStringExtra(Intent.EXTRA_TEXT)?.let { text ->
            val fileName = fileNameEdit.text.toString()
            val lastModified = Date()
            val outputFile = java.io.File(sharedFolder, fileName).also { if (it.exists()) it.delete() }

            if (outputFile.createNewFile()) {
                outputFile.setLastModified(lastModified.time)

                if (fileName.endsWith(".url")) { // Create url file
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

    private fun store(uri: Uri, fileName: String?, userId: Int, driveId: Int, folderId: Int): Boolean {
        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
                val fileSize = SyncUtils.getFileSize(cursor)

                try {
                    if (fileName == null) return false

                    val outputFile = java.io.File(sharedFolder, fileName).also { if (it.exists()) it.delete() }

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
                            fileSize = fileSize,
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
        return contentResolver.query(this, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) SyncUtils.getFileName(cursor) else ""
        } ?: ""
    }

    class SaveExternalFilesViewModel : ViewModel() {
        val folderId = MutableLiveData<Int?>()
    }

    companion object {
        const val SHARED_FILE_FOLDER = "shared_files"
    }
}
