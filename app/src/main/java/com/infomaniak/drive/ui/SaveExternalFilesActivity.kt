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
package com.infomaniak.drive.ui

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Parcelable
import android.provider.OpenableColumns
import android.view.View.VISIBLE
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.ViewModelProvider
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.menu.settings.SelectDriveDialog
import com.infomaniak.drive.ui.menu.settings.SelectDriveViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.SyncUtils
import com.infomaniak.drive.utils.SyncUtils.checkSyncPermissions
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.showOrHideEmptyError
import kotlinx.android.synthetic.main.activity_save_external_file.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.util.*


class SaveExternalFilesActivity : BaseActivity() {

    private lateinit var selectDriveViewModel: SelectDriveViewModel

    private var currentUri: Uri? = null
    private var folderID: Int? = null
    private var folderName: String? = null

    private var isMultiple = false

    override fun onCreate(savedInstanceState: Bundle?) {
        runBlocking { AccountUtils.requestCurrentUser() }
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_save_external_file)
        selectDriveViewModel = ViewModelProvider(this)[SelectDriveViewModel::class.java]

        if (!isAuth()) return

        when (intent?.action) {
            Intent.ACTION_SEND -> handleSendSingle(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleSendMultiple(intent)
        }

        activeDefaultUser()
        AccountUtils.getAllUsers().observe(this) { users ->
            if (users.size > 1) {
                activeSelectDrive()
            }
        }

        selectDriveViewModel.selectedDrive.observe(this) {
            it?.let {
                driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(it.preferences.color))
                driveName.text = it.name
                saveButton.isEnabled = false
                resetFilePath()

                pathTitle.visibility = VISIBLE
                selectPath.visibility = VISIBLE
                selectPath.setOnClickListener {
                    val intent = Intent(this, SelectFolderActivity::class.java).apply {
                        putExtra(SelectFolderActivity.USER_ID_TAG, selectDriveViewModel.selectedUserId.value)
                        putExtra(SelectFolderActivity.USER_DRIVE_ID_TAG, selectDriveViewModel.selectedDrive.value?.id)
                    }
                    startActivityForResult(intent, SelectFolderActivity.SELECT_FOLDER_REQUEST)
                }
            } ?: run {
                showSelectDrive()
            }
        }

        fileNameEdit?.addTextChangedListener {
            saveButton.isEnabled = isValidFields()
            fileNameEdit.showOrHideEmptyError()
        }

        saveButton.setOnClickListener {
            if (isValidFields()) {
                runBlocking(Dispatchers.IO) {
                    val userID = selectDriveViewModel.selectedUserId.value
                    val driveID = selectDriveViewModel.selectedDrive.value?.id
                    val folderID = folderID

                    if (userID != null && driveID != null && folderID != null) {
                        storeFiles(userID, driveID, folderID)
                    }
                }
                finish()
            }
        }
        checkSyncPermissions()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SelectFolderActivity.SELECT_FOLDER_REQUEST && resultCode == RESULT_OK) {
            folderID = data?.extras?.getInt(SelectFolderActivity.FOLDER_ID_TAG)
            folderName = if (folderID == Utils.ROOT_ID) {
                getString(R.string.allRootName, selectDriveViewModel.selectedDrive.value?.name)
            } else {
                data?.extras?.getString(SelectFolderActivity.FOLDER_NAME_TAG)
            }
            pathName.text = folderName
            saveButton.isEnabled = isValidFields()
        }
    }

    private fun resetFilePath() {
        folderID = null
        folderName = null
        pathName.setText(R.string.selectFolderTitle)
    }

    private fun activeDefaultUser() {
        val currentUserDrives = DriveInfosController.getDrives(AccountUtils.currentUserId)
        if (currentUserDrives.size > 1) {
            activeSelectDrive()
        }
        selectDriveViewModel.selectedUserId.value = AccountUtils.currentUserId
        selectDriveViewModel.selectedDrive.value = AccountUtils.getCurrentDrive()
    }

    private fun isAuth(): Boolean {
        if (AccountUtils.currentUserId == -1) {
            startActivity(Intent(this, LaunchActivity::class.java))
            finish()
            return false
        }
        return true
    }

    private fun handleSendSingle(intent: Intent) {
        (intent.getParcelableExtra<Parcelable>(Intent.EXTRA_STREAM) as? Uri)?.let { uri ->
            fileNameEdit.setText(uri.fileName())
            currentUri = uri
        }
    }

    private fun handleSendMultiple(intent: Intent) {
        val uris = intent.getParcelableArrayListExtra<Parcelable>(Intent.EXTRA_STREAM)?.map { it as Uri } ?: arrayListOf()
        fileNames.adapter = SaveExternalUriAdapter(uris as ArrayList<Uri>)
        isMultiple = true

        fileNameEditLayout.removeViewAt(0)
        fileNames.visibility = VISIBLE
    }

    private fun isValidFields(): Boolean {
        return fileNameEdit == null ||
                checkSyncPermissions() &&
                !fileNameEdit.showOrHideEmptyError() &&
                selectDriveViewModel.selectedUserId.value != null &&
                selectDriveViewModel.selectedDrive.value != null &&
                folderID != null &&
                currentUri != null
    }

    private fun showSelectDrive() {
        driveIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.iconColor))
        driveName.setText(R.string.selectDriveTitle)
    }

    private fun activeSelectDrive() {
        switchDrive.visibility = VISIBLE
        selectDrive.setOnClickListener {
            SelectDriveDialog().show(supportFragmentManager, "SyncSettingsSelectDriveDialog")
        }
    }

    private fun storeFiles(userID: Int, driveID: Int, folderID: Int) {
        if (isMultiple) {
            val adapter = fileNames.adapter as SaveExternalUriAdapter
            adapter.uris.forEach { currentUri ->
                store(uri = currentUri, userId = userID, driveId = driveID, folderId = folderID)
            }
        } else {
            store(currentUri!!, fileNameEdit.text.toString(), userID, driveID, folderID)
        }
        applicationContext.syncImmediately()
    }

    private fun store(uri: Uri, name: String? = null, userId: Int, driveId: Int, folderId: Int) {
        val folder = File(cacheDir, SHARED_FILE_FOLDER).apply { if (!exists()) mkdirs() }

        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
            val fileSize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
            val fileName = name ?: SyncUtils.getFileName(cursor)
            val outputFile = File(folder, fileName)

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
        }
    }

    private fun Uri.fileName(): String {
        contentResolver.query(this, null, null, null, null)?.use { cursor ->
            cursor.moveToFirst()
            return SyncUtils.getFileName(cursor)
        }
        return ""
    }

    companion object {
        const val SHARED_FILE_FOLDER = "shared_files"
    }
}
