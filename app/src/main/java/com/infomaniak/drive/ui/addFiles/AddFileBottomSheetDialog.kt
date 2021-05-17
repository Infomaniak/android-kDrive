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
package com.infomaniak.drive.ui.addFiles

import android.app.Activity
import android.content.ContentValues
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.AccountUtils.currentDriveId
import com.infomaniak.drive.utils.AccountUtils.currentUserId
import com.infomaniak.drive.utils.SyncUtils.DATE_TAKEN
import com.infomaniak.drive.utils.SyncUtils.checkSyncPermissions
import com.infomaniak.drive.utils.SyncUtils.checkSyncPermissionsResult
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import kotlinx.android.synthetic.main.fragment_bottom_sheet_add_file.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*


class AddFileBottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var mainViewModel: MainViewModel
    private lateinit var currentFolderFile: File
    private var currentPhotoUri: Uri? = null

    companion object {
        const val SELECT_FILES_REQ = 2
        const val CAPTURE_MEDIA_REQ = 3
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_bottom_sheet_add_file, container, false)

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        mainViewModel = ViewModelProvider(requireActivity())[MainViewModel::class.java]
        val file = mainViewModel.currentFolderOpenAddFileBottom.value ?: mainViewModel.currentFolder.value
        file?.let {
            currentFolderFile = it
            currentFolder.setFileItem(it)
        }

        openCamera.setOnClickListener { openCamera() }
        documentUpload.setOnClickListener { uploadFiles() }
        documentScanning.setOnClickListener { scanDocuments() }
        folderCreate.setOnClickListener { createFolder() }
        docsCreate.setOnClickListener { createFile(File.Office.DOCS) }
        pointsCreate.setOnClickListener { createFile(File.Office.POINTS) }
        gridsCreate.setOnClickListener { createFile(File.Office.GRIDS) }
        noteCreate.setOnClickListener { createFile(File.Office.TXT) }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mainViewModel.currentFolderOpenAddFileBottom.value = null
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            when (requestCode) {
                SELECT_FILES_REQ -> onSelectFilesResult(data)
                CAPTURE_MEDIA_REQ -> onCaptureMediaResult(data)
            }
        } else if (requestCode == CAPTURE_MEDIA_REQ) deleteTempPhoto()
        dismiss()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when {
            requireActivity().checkSyncPermissionsResult(requestCode, grantResults, SELECT_FILES_REQ) -> {
                uploadFiles()
            }
            requireActivity().checkSyncPermissionsResult(requestCode, grantResults, CAPTURE_MEDIA_REQ) -> {
                openCamera()
            }
            Utils.checkWriteStoragePermissionResult(requestCode, grantResults) -> {
                createPhotoFile()
            }
        }
    }

    private fun openCamera() {
        if (checkWriteStoragePermission() && checkSyncPermissions(CAPTURE_MEDIA_REQ)) {
            openCamera.isEnabled = false
            currentPhotoUri = createPhotoFile()
            try {
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                }
                val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                val chooserIntent = Intent.createChooser(takePictureIntent, getString(R.string.buttonTakePhotoOrVideo))
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takeVideoIntent))
                startActivityForResult(chooserIntent, CAPTURE_MEDIA_REQ)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadFiles() {
        if (checkSyncPermissions(SELECT_FILES_REQ)) {
            documentUpload.isEnabled = false
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
            startActivityForResult(Intent.createChooser(intent, getString(R.string.addFileSelectUploadFile)), SELECT_FILES_REQ)
        }
    }

    private fun scanDocuments() {
        // TODO find a good lib
        dismiss()
    }

    private fun createFolder() {
        safeNavigate(
            AddFileBottomSheetDialogDirections.actionAddFileBottomSheetDialogToNewFolderFragment(
                parentFolderId = currentFolderFile.id,
                userDrive = null
            )
        )
        dismiss()
    }

    private fun createFile(office: File.Office) {
        Utils.createPromptNameDialog(
            context = requireContext(),
            title = R.string.modalCreateFileTitle,
            fieldName = R.string.hintInputFileName,
            positiveButton = R.string.buttonCreate,
            iconRes = office.convertedType.icon
        ) { dialog, name ->
            val createFile = CreateFile(name, office.extension)
            mainViewModel.createOffice(currentDriveId, currentFolderFile.id, createFile)
                .observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse.isSuccess()) {
                        requireActivity().showSnackbar(getString(R.string.modalCreateFileSucces, createFile.name))
                        apiResponse.data?.let { file -> requireContext().openOnlyOfficeActivity(file) }
                    } else {
                        requireActivity().showSnackbar(R.string.errorFileCreate)
                    }
                    mainViewModel.refreshActivities.value = true
                    dialog.dismiss()
                    dismiss()
                }
        }

    }

    private fun onSelectFilesResult(data: Intent?) {
        val clipData = data?.clipData
        val uri = data?.data

        if (clipData != null) {
            val count = clipData.itemCount
            for (i in 0 until count) initUpload(clipData.getItemAt(i).uri)
        } else if (uri != null) {
            initUpload(uri)
        }
    }

    private fun onCaptureMediaResult(data: Intent?) {
        if (data?.data == null) {
            currentPhotoUri?.let { uri ->
                val bitmap = uri.getBitmap(requireContext())
                bitmap.saveAsPhoto(requireContext(), uri)
                initUpload(uri)
            }
        } else {
            deleteTempPhoto()
            data.data?.let { videoUri -> initUpload(videoUri) }
        }
    }

    private fun initUpload(uri: Uri) {
        uri.let { returnUri ->
            requireContext().contentResolver.query(returnUri, null, null, null, null)
        }?.use { cursor ->
            if (cursor.moveToFirst()) {
                val fileName = SyncUtils.getFileName(cursor)
                val fileSize = cursor.getLong(cursor.getColumnIndex(OpenableColumns.SIZE))
                val (fileCreatedAt, fileModifiedAt) = SyncUtils.getFileDates(cursor)
                val memoryInfo = requireContext().getAvailableMemory()
                val isLowMemory = memoryInfo.lowMemory || memoryInfo.availMem < UploadTask.chunkSize

                if (isLowMemory) {
                    requireActivity().showSnackbar(R.string.anErrorHasOccurred)
                } else {
                    val appContext = requireContext().applicationContext
                    lifecycleScope.launch(Dispatchers.IO) {
                        UploadFile(
                            uri = uri.toString(),
                            userId = currentUserId,
                            driveId = currentDriveId,
                            remoteFolder = currentFolderFile.id,
                            type = UploadFile.Type.UPLOAD.name,
                            fileName = fileName,
                            fileSize = fileSize,
                            fileCreatedAt = fileCreatedAt,
                            fileModifiedAt = fileModifiedAt
                        ).store()
                        appContext.syncImmediately()
                    }
                }
            }
        }
    }

    private fun createPhotoFile(): Uri? {
        val date = Date()
        val contentResolver = requireContext().contentResolver
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.ENGLISH).format(date)
        val fileName = "${timeStamp}.jpg"
        val directory = getString(R.string.app_name)

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.TITLE, fileName)
            put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
            put(MediaStore.Images.Media.DATE_ADDED, date.time / 1000)
            put(MediaStore.Images.Media.DATE_MODIFIED, date.time / 1000)
            put(DATE_TAKEN, date.time)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativePath = Environment.DIRECTORY_PICTURES + java.io.File.separator + directory
                put(MediaStore.Images.Media.RELATIVE_PATH, relativePath)
            } else {
                val pathname = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
                    .toString() + java.io.File.separator + directory
                val fileDirectory = java.io.File(pathname)
                if (!fileDirectory.exists()) fileDirectory.mkdirs()
                put(MediaStore.Images.Media.DATA, java.io.File(fileDirectory, fileName).toString())
            }
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun deleteTempPhoto() {
        currentPhotoUri?.let { uri ->
            requireContext().contentResolver.delete(uri, null, null)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (findNavController().currentDestination?.id == R.id.addFileBottomSheetDialog) findNavController().popBackStack()
    }
}