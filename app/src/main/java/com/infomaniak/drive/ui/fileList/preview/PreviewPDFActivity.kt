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
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintSet
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.transition.AutoTransition
import androidx.transition.TransitionManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.ActivityPreviewPdfBinding
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.views.ExternalFileInfoActionsView
import com.infomaniak.lib.core.utils.getFileNameAndSize

class PreviewPDFActivity : AppCompatActivity(), ExternalFileInfoActionsView.OnItemClickListener {

    val binding: ActivityPreviewPdfBinding by lazy { ActivityPreviewPdfBinding.inflate(layoutInflater) }

    private val navController by lazy { setupNavController() }
    private val navHostFragment by lazy { supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment }

    private val baseConstraintSet by lazy {
        ConstraintSet().apply {
            clone(binding.pdfContainer)
        }
    }
    private val collapsedConstraintSet by lazy {
        ConstraintSet().apply {
            clone(baseConstraintSet)

            clear(R.id.header, ConstraintSet.TOP)
            connect(R.id.header, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        }
    }
    private val transition by lazy {
        AutoTransition().apply {
            duration = 125
        }
    }

    private val externalPDFUri: Uri by lazy { Uri.parse(intent.dataString) }
    private val fileNameAndSize: Pair<String, Long>? by lazy { getFileNameAndSize(externalPDFUri) }
    private val fileName: String by lazy { fileNameAndSize?.first ?: "" }
    private val fileSize: Long by lazy { fileNameAndSize?.second ?: 0 }

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null
    private var isUiShown = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        with(binding) {
            setContentView(root)
            setColorNavigationBar(appBar = true)

            navController.navigate(R.id.previewPDFFragment)

            binding.header.setup(
                onBackClicked = { finish() },
                onOpenWithClicked = { openPDFWith() },
            )

            initBottomSheet()
        }
    }

    private fun initBottomSheet() = with(binding) {
        bottomSheetBehavior = getBottomSheetFileBehavior(bottomSheetFileInfos, true)
        bottomSheetFileInfos.updateWithExternalFile(getFakeFile())
        bottomSheetFileInfos.init(this@PreviewPDFActivity)
    }

    override fun onStart() {
        super.onStart()
        binding.header.setupWindowInsetsListener(binding.root, bottomSheetBehavior, binding.bottomSheetFileInfos)
        setupTransparentStatusBar()
    }

    override fun openWithClicked(context: Context) {
        super.openWithClicked(context)
        openPDFWith()
    }

    override fun shareFile(context: Context) {
        super.shareFile(context)
        shareFile { externalPDFUri }
    }

    override fun saveToKDriveClicked(context: Context) {
        super.saveToKDriveClicked(context)
        saveToKDrive(externalPDFUri)
    }

    override fun printClicked(context: Context) {
        super.printClicked(context)
        val printManager = getSystemService(Context.PRINT_SERVICE) as PrintManager
        val printAdapter = PDFDocumentAdapter(fileName, getFileForPrint(externalPDFUri))
        printManager.print(fileName, printAdapter, PrintAttributes.Builder().build())
    }

    fun toggleFullscreen() = with(bottomSheetBehavior) {
        TransitionManager.beginDelayedTransition(binding.pdfContainer, transition)

        this?.state?.let {
            state = if (isUiShown) {
                collapsedConstraintSet.applyTo(binding.pdfContainer)
                BottomSheetBehavior.STATE_HIDDEN
            } else {
                baseConstraintSet.applyTo(binding.pdfContainer)
                BottomSheetBehavior.STATE_COLLAPSED
            }
        }
        isUiShown = !isUiShown
        toggleSystemBar(isUiShown)
    }

    private fun openPDFWith() {
        openWith(
            externalPDFUri,
            contentResolver.getType(externalPDFUri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    // This is necessary to be able to use the same view details we have in kDrive (file name, file type and size)
    private fun getFakeFile(): File {
        return File().apply {
            name = fileName
            size = fileSize
            id = ROOT_ID
            extensionType = ExtensionType.PDF.value
            type = ""
        }
    }

    private fun saveToKDrive(uri: Uri) {
        Intent(this, SaveExternalFilesActivity::class.java).apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtras(
                SaveExternalFilesActivityArgs(
                    userId = AccountUtils.currentUserId,
                    driveId = AccountUtils.currentDriveId,
                ).toBundle()
            )
            type = "/pdf"
            startActivity(this)
        }
    }

    private fun setupNavController(): NavController {
        return navHostFragment.navController.apply {
            intent.dataString?.let { fileURI ->
                setGraph(R.navigation.view_pdf, PreviewPDFFragmentArgs(fileURI = fileURI).toBundle())
            }
        }
    }

    private fun getFileForPrint(uri: Uri): IOFile {
        return IOFile(uploadFolder, uri.hashCode().toString()).apply {
            if (exists()) delete()
            createNewFile()
            contentResolver?.openInputStream(uri)?.use { inputStream ->
                outputStream().use { inputStream.copyTo(it) }
            }
        }
    }
}
