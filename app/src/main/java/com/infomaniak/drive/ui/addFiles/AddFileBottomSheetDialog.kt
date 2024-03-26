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
package com.infomaniak.drive.ui.addFiles

import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.infomaniak.drive.GeniusScanUtils.scanResultProcessing
import com.infomaniak.drive.GeniusScanUtils.startScanFlow
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.MatomoDrive.trackNewElementEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.CreateFile
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Office
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentBottomSheetAddFileBinding
import com.infomaniak.drive.ui.LaunchActivity.*
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListFragment
import com.infomaniak.drive.ui.menu.SharedWithMeFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.AccountUtils.currentUserId
import com.infomaniak.drive.utils.SyncUtils.syncImmediately
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.Date

class AddFileBottomSheetDialog : BottomSheetDialogFragment() {

    private var binding: FragmentBottomSheetAddFileBinding by safeBinding()

    private lateinit var currentFolderFile: File

    private val mainViewModel: MainViewModel by activityViewModels()

    private lateinit var openCameraWritePermissions: DrivePermissions
    private lateinit var openCameraPermissions: CameraPermissions

    private var mediaPhotoPath = ""
    private var mediaVideoPath = ""

    private val captureMediaResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { onCaptureMediaResult() }
        dismiss()
    }

    private val scanFlowResultLauncher = registerForActivityResult(StartActivityForResult()) { activityResult ->
        activityResult.whenResultIsOk {
            it?.let { data ->
                val folder = when (parentFragment?.childFragmentManager?.fragments?.getOrNull(0)?.javaClass) {
                    FileListFragment::class.java, SharedWithMeFragment::class.java -> currentFolderFile
                    else -> null
                }

                requireActivity().scanResultProcessing(data, folder)
            }
        }
        dismiss()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return (mainViewModel.currentFolderOpenAddFileBottom.value ?: mainViewModel.currentFolder.value)?.let { file ->
            currentFolderFile = file
            FragmentBottomSheetAddFileBinding.inflate(inflater, container, false).also { binding = it }.root
        } ?: run {
            findNavController().popBackStack()
            null
        } // TODO Temporary fix
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)
        currentFolder.setFileItem(currentFolderFile)

        openCameraWritePermissions = DrivePermissions().apply {
            registerPermissions(this@AddFileBottomSheetDialog) { authorized -> if (authorized) openCamera() }
        }
        openCameraPermissions = CameraPermissions().apply {
            registerPermissions(this@AddFileBottomSheetDialog) { authorized -> if (authorized) openCamera() }
        }

        openCamera.setOnClickListener { openCamera() }
        documentUpload.setOnClickListener { mainViewModel.uploadFilesHelper?.uploadFiles() }
        documentScanning.setOnClickListener { scanDocuments() }
        folderCreate.setOnClickListener { createFolder() }
        docsCreate.setOnClickListener { createFile(Office.DOCS) }
        pointsCreate.setOnClickListener { createFile(Office.POINTS) }
        gridsCreate.setOnClickListener { createFile(Office.GRIDS) }
        formCreate.setOnClickListener { createFile(Office.FORM) }
        noteCreate.setOnClickListener { createFile(Office.TXT) }

        documentScanning.isVisible = (context.applicationContext as MainApplication).geniusScanIsReady
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        mainViewModel.currentFolderOpenAddFileBottom.value = null
    }

    private fun openCamera() {
        if (openCameraWritePermissions.checkSyncPermissions() && openCameraPermissions.checkCameraPermission()) {
            trackNewElement("takePhotoOrVideo")
            binding.openCamera.isEnabled = false
            try {
                val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, createMediaFile(false))
                }
                val takeVideoIntent = Intent(MediaStore.ACTION_VIDEO_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, createMediaFile(true))
                }
                val chooserIntent = Intent.createChooser(takePictureIntent, getString(R.string.buttonTakePhotoOrVideo)).apply {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(takeVideoIntent))
                }
                captureMediaResultLauncher.launch(chooserIntent)
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    private fun scanDocuments() {
        trackNewElement("scan")
        activity?.startScanFlow(scanFlowResultLauncher)
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

    private fun Office.getEventName(): String {
        return when (this) {
            Office.DOCS -> "createText"
            Office.POINTS -> "createPresentation"
            Office.GRIDS -> "createTable"
            Office.TXT -> "createNote"
            Office.FORM -> "createForm"
        }
    }

    private fun createFile(office: Office) {
        Utils.createPromptNameDialog(
            context = requireContext(),
            title = R.string.modalCreateFileTitle,
            fieldName = R.string.hintInputFileName,
            positiveButton = R.string.buttonCreate,
            iconRes = office.extensionType.icon
        ) { dialog, name ->
            trackNewElement(office.getEventName())
            val createFile = CreateFile(name, office.extension)
            mainViewModel.createOffice(currentFolderFile.driveId, currentFolderFile.id, createFile)
                .observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse.isSuccess()) {
                        showSnackbar(getString(R.string.modalCreateFileSucces, createFile.name), showAboveFab = true)
                        apiResponse.data?.let { file -> requireContext().openOnlyOfficeActivity(file) }
                    } else {
                        showSnackbar(R.string.errorFileCreate, showAboveFab = true)
                    }
                    mainViewModel.refreshActivities.value = true
                    dialog.dismiss()
                    dismiss()
                }
        }
    }

    private fun onCaptureMediaResult() {
        try {
            val file = IOFile(mediaPhotoPath).takeIf { it.length() != 0L } ?: IOFile(mediaVideoPath)
            val fileModifiedAt = Date(file.lastModified())
            val applicationContext = context?.applicationContext
            lifecycleScope.launch(Dispatchers.IO) {
                val cacheUri = Utils.copyDataToUploadCache(requireContext(), file, fileModifiedAt)
                UploadFile(
                    uri = cacheUri.toString(),
                    driveId = currentFolderFile.driveId,
                    fileCreatedAt = fileModifiedAt,
                    fileModifiedAt = fileModifiedAt,
                    fileName = file.name,
                    fileSize = file.length(),
                    remoteFolder = currentFolderFile.id,
                    type = UploadFile.Type.UPLOAD.name,
                    userId = currentUserId,
                ).store()
                applicationContext?.syncImmediately()
                file.delete()
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            showSnackbar(R.string.errorDeviceStorage, showAboveFab = true)
        }
    }

    private fun createMediaFile(isVideo: Boolean): Uri {
        val date = Date()
        val timeStamp: String = date.format(FORMAT_NEW_FILE)
        val fileName = "${timeStamp}.${if (isVideo) "mp4" else "jpg"}"

        val fileData = IOFile(createExposedTempUploadDir(), fileName).apply {
            if (exists()) delete()
            createNewFile()
            setLastModified(date.time)
        }

        if (isVideo) mediaVideoPath = fileData.path else mediaPhotoPath = fileData.path
        return FileProvider.getUriForFile(requireContext(), getString(R.string.FILE_AUTHORITY), fileData)
    }

    private fun createExposedTempUploadDir(): IOFile {
        val directory = getString(R.string.EXPOSED_UPLOAD_DIR)
        return IOFile(requireContext().cacheDir, directory).apply { if (!exists()) mkdirs() }
    }

    private fun deleteExposedTempUploadDir() {
        IOFile(requireContext().cacheDir, getString(R.string.EXPOSED_UPLOAD_DIR)).apply {
            if (exists()) deleteRecursively()
        }
    }

    private fun trackNewElement(trackerName: String) {
        val trackerSource = if (mainViewModel.currentFolderOpenAddFileBottom.value == null) "FromFAB" else "FromFolder"
        trackNewElementEvent(trackerName + trackerSource)
    }

    override fun onDestroy() {
        super.onDestroy()
        // Fix the popBackStack in onViewCreated because onResume is still called
        if (findNavController().currentDestination?.id == R.id.addFileBottomSheetDialog) findNavController().popBackStack()
        deleteExposedTempUploadDir()
    }
}
