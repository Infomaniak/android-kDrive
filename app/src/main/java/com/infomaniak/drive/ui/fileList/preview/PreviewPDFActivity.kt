/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList.preview

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.ActivityPreviewPdfBinding
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.getFileNameAndSize
import io.sentry.Sentry
import io.sentry.SentryLevel

class PreviewPDFActivity : AppCompatActivity(), OnItemClickListener {

    val binding: ActivityPreviewPdfBinding by lazy { ActivityPreviewPdfBinding.inflate(layoutInflater) }

    override val ownerFragment = null
    override val currentContext by lazy { this }
    override val currentFile = null

    private val navController by lazy { setupNavController() }
    private val navHostFragment by lazy { supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment }

    private val externalFileUri by lazy { Uri.parse(intent.dataString) }
    private val fileNameAndSize: Pair<String, Long>? by lazy { getFileNameAndSize(externalFileUri) }
    private val fileName: String by lazy { fileNameAndSize?.first ?: "" }
    private val fileSize: Long by lazy { fileNameAndSize?.second ?: 0 }

    private val bottomSheetBehavior: BottomSheetBehavior<View>
        get() = BottomSheetBehavior.from(binding.bottomSheetFileInfos)

    private var isOverlayShown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        with(binding) {
            setContentView(root)

            navController.navigate(R.id.previewPDFFragment)

            header.setup(
                onBackClicked = { finish() },
                onOpenWithClicked = { openWith(externalFileUri = externalFileUri) },
            )
        }

        initBottomSheet()
    }

    private fun initBottomSheet() = with(binding) {
        setupBottomSheetFileBehavior(bottomSheetBehavior, isDraggable = true, isFitToContents = true)
        bottomSheetFileInfos.updateWithExternalFile(getFakeFile())
        bottomSheetFileInfos.initOnClickListener(this@PreviewPDFActivity)
    }

    override fun onStart() {
        super.onStart()
        binding.header.setupWindowInsetsListener(rootView = binding.root, bottomSheetView = binding.bottomSheetFileInfos)
        setupStatusBarForPreview()
    }

    override fun shareFile() {
        shareFile { externalFileUri }
    }

    override fun saveToKDrive() {
        saveToKDrive(externalFileUri)
    }

    override fun openWith() {
        openWith(externalFileUri = externalFileUri)
    }

    override fun printClicked() {
        super.printClicked()
        getFileForPrint(externalFileUri)?.let { fileToPrint ->
            val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
            val printAdapter = PDFDocumentAdapter(fileName, fileToPrint)
            printManager.print(fileName, printAdapter, PrintAttributes.Builder().build())
        }
    }

    private fun getFileForPrint(uri: Uri): IOFile? {
        return runCatching {
            IOFile(uploadFolder, uri.hashCode().toString()).apply {
                if (exists()) delete()
                createNewFile()
                contentResolver?.openInputStream(uri)?.use { inputStream ->
                    outputStream().use { inputStream.copyTo(it) }
                }
            }
        }.onFailure {
            showSnackbar(R.string.errorFileNotFound)
            Sentry.withScope { scope ->
                scope.level = SentryLevel.ERROR
                scope.setExtra("exception", it.stackTraceToString())
                Sentry.captureMessage("Exception while printing a PDF")
            }
        }.getOrNull()
    }

    fun toggleFullscreen() {
        isOverlayShown = !isOverlayShown
        binding.header.toggleVisibility(isOverlayShown)
        bottomSheetBehavior.state = if (isOverlayShown) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_HIDDEN
        toggleSystemBar(isOverlayShown)
    }

    // This is necessary to be able to use the same view details we have in kDrive (file name, file type and size)
    private fun getFakeFile(): File {
        return File(
            name = fileName,
            size = fileSize,
            id = ROOT_ID,
            extensionType = ExtensionType.PDF.value,
            type = "",
        )
    }

    private fun setupNavController(): NavController {
        return navHostFragment.navController.apply {
            setGraph(R.navigation.view_pdf, PreviewPDFFragmentArgs(fileUri = externalFileUri).toBundle())
        }
    }

    override fun displayInfoClicked() = Unit
    override fun fileRightsClicked() = Unit
    override fun goToFolder() = Unit
    override fun manageCategoriesClicked(fileId: Int) = Unit
    override fun onCacheAddedToOffline() = Unit
    override fun onDeleteFile(onApiResponse: () -> Unit) = Unit
    override fun onDuplicateFile(result: String, onApiResponse: () -> Unit) = Unit
    override fun onLeaveShare(onApiResponse: () -> Unit) = Unit
    override fun onMoveFile(destinationFolder: File) = Unit
    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) = Unit
    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) = Unit
}
