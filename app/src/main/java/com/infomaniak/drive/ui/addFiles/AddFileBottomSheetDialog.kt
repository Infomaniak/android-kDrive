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
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.AccountUtils.currentUserId
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.lib.core.utils.FORMAT_NEW_FILE
import com.infomaniak.lib.core.utils.format
import kotlinx.android.synthetic.main.fragment_bottom_sheet_add_file.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.*


class AddFileBottomSheetDialog : BottomSheetDialogFragment() {

    private lateinit var currentFolderFile: File
    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var openCameraPermissions: DrivePermissions
    private lateinit var uploadFilesPermissions: DrivePermissions

    private var currentPhotoUri: Uri? = null
    private var mediaPhotoPath = ""
    private var mediaVideoPath = ""

    companion object {
        const val SELECT_FILES_REQ = 2
        const val CAPTURE_MEDIA_REQ = 3
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return (mainViewModel.currentFolderOpenAddFileBottom.value ?: mainViewModel.currentFolder.value)?.let {
            currentFolderFile = it
            inflater.inflate(R.layout.fragment_bottom_sheet_add_file, container, false)
        } ?: run {
            findNavController().popBackStack()
            null
        } // TODO Temporary fix
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        currentFolder.setFileItem(currentFolderFile)

        openCameraPermissions = DrivePermissions()
        openCameraPermissions.registerPermissions(this) { autorized ->
            if (autorized) openCamera()
        }
        uploadFilesPermissions = DrivePermissions()
        uploadFilesPermissions.registerPermissions(this) { autorized ->
            if (autorized) uploadFiles()
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
        }
        dismiss()
    }

    private fun openCamera() {
        if (openCameraPermissions.checkSyncPermissions()) {
            openCamera.isEnabled = false
            try {
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    currentPhotoUri = createMediaFile(false)
                    putExtra(MediaStore.EXTRA_OUTPUT, currentPhotoUri)
                }
                val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, createMediaFile(true))
                }
                val chooserIntent = Intent.createChooser(takePictureIntent, getString(R.string.buttonTakePhotoOrVideo))
                chooserIntent.putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takeVideoIntent))
                startActivityForResult(chooserIntent, CAPTURE_MEDIA_REQ)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun uploadFiles() {
        if (uploadFilesPermissions.checkSyncPermissions()) {
            documentUpload.isEnabled = false
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "*/*"
                flags = (Intent.FLAG_GRANT_READ_URI_PERMISSION
                        or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                addCategory(Intent.CATEGORY_OPENABLE)
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
                userDrive = UserDrive(driveId = currentFolderFile.driveId)
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
            mainViewModel.createOffice(currentFolderFile.driveId, currentFolderFile.id, createFile)
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
        var launchSync = false

        try {
            if (clipData != null) {
                val count = clipData.itemCount
                for (i in 0 until count) {
                    initUpload(clipData.getItemAt(i).uri)
                    launchSync = true
                }
            } else if (uri != null) {
                initUpload(uri)
                launchSync = true
            }
        } catch (exception: Exception) {
            requireActivity().showSnackbar(R.string.errorDeviceStorage)
        } finally {
            if (launchSync) requireContext().syncImmediately()
        }
    }

    private fun onCaptureMediaResult(data: Intent?) {
        try {
            if (data?.data == null) {
                val photoFile = java.io.File(mediaPhotoPath)
                val file = when {
                    photoFile.length() != 0L -> photoFile
                    else -> java.io.File(mediaVideoPath)
                }

                val fileModifiedAt = Date(file.lastModified())
                val fileSize = file.length()
                val applicationContext = context?.applicationContext
                lifecycleScope.launch(Dispatchers.IO) {
                    val cacheUri = Utils.copyDataToUploadCache(requireContext(), file, fileModifiedAt)
                    UploadFile(
                        uri = cacheUri.toString(),
                        driveId = currentFolderFile.driveId,
                        fileCreatedAt = Date(file.lastModified()),
                        fileModifiedAt = fileModifiedAt,
                        fileName = file.name,
                        fileSize = fileSize,
                        remoteFolder = currentFolderFile.id,
                        type = UploadFile.Type.UPLOAD.name,
                        userId = currentUserId,
                    ).store()
                    applicationContext?.syncImmediately()
                    file.delete()
                }

            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            requireActivity().showSnackbar(R.string.errorDeviceStorage)
        }
    }

    @Throws(Exception::class)
    private fun initUpload(uri: Uri) {
        DocumentFile.fromSingleUri(requireContext(), uri)?.let { documentFile ->
            val fileName = documentFile.name
            val fileSize = documentFile.length()
            val fileModifiedAt = Date(documentFile.lastModified())

            val memoryInfo = requireContext().getAvailableMemory()
            val isLowMemory = memoryInfo.lowMemory || memoryInfo.availMem < UploadTask.chunkSize

            when {
                isLowMemory -> {
                    requireActivity().showSnackbar(R.string.uploadOutOfMemoryError)
                }
                fileName == null -> {
                    requireActivity().showSnackbar(R.string.anErrorHasOccurred)
                }
                else -> {
                    CoroutineScope(Dispatchers.IO).launch {
                        UploadFile(
                            uri = uri.toString(),
                            driveId = currentFolderFile.driveId,
                            fileCreatedAt = fileModifiedAt,
                            fileModifiedAt = fileModifiedAt,
                            fileName = fileName,
                            fileSize = fileSize,
                            remoteFolder = currentFolderFile.id,
                            type = UploadFile.Type.UPLOAD.name,
                            userId = currentUserId,
                        ).store()
                    }
                }
            }
        }
    }

    private fun createMediaFile(isVideo: Boolean): Uri {
        val date = Date()
        val timeStamp: String = date.format(FORMAT_NEW_FILE)
        val fileName = "${timeStamp}.${if (isVideo) "mp4" else "jpg"}"

        val fileData = java.io.File(createExposedTempUploadDir(), fileName).apply {
            if (exists()) delete()
            createNewFile()
            setLastModified(date.time)
        }

        if (isVideo) mediaVideoPath = fileData.path else mediaPhotoPath = fileData.path
        return FileProvider.getUriForFile(requireContext(), getString(R.string.FILE_AUTHORITY), fileData)
    }

    private fun createExposedTempUploadDir(): java.io.File {
        val directory = getString(R.string.EXPOSED_UPLOAD_DIR)
        return java.io.File(requireContext().cacheDir, directory).apply { if (!exists()) mkdirs() }
    }

    private fun deleteExposedTempUploadDir() {
        java.io.File(requireContext().cacheDir, getString(R.string.EXPOSED_UPLOAD_DIR)).apply {
            if (exists()) deleteRecursively()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix the popBackStack in onViewCreated because onResume is still called
        if (findNavController().currentDestination?.id == R.id.addFileBottomSheetDialog) findNavController().popBackStack()
        deleteExposedTempUploadDir()
    }
}