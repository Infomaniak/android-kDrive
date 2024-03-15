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
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
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
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.getFileNameAndSize
import com.infomaniak.lib.core.utils.setMargins

class PreviewPDFActivity : AppCompatActivity(), ExternalFileInfoActionsView.OnItemClickListener {

    private val navController by lazy { setupNavController() }
    private val navHostFragment by lazy { supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment }

    private val binding: ActivityPreviewPdfBinding by lazy { ActivityPreviewPdfBinding.inflate(layoutInflater) }

    private val externalPDFUri: Uri by lazy { Uri.parse(intent.dataString) }
    private val fileNameAndSize: Pair<String, Long>? by lazy { getFileNameAndSize(externalPDFUri) }
    private val fileName: String by lazy { fileNameAndSize?.first ?: "" }
    private val fileSize: Long by lazy { fileNameAndSize?.second ?: 0 }

    private lateinit var bottomSheetBehavior: BottomSheetBehavior<View>

    private val baseConstraintSet by lazy {
        ConstraintSet().apply {
            clone(binding.pdfContainer)
        }
    }

    private val collapsedConstraintSet by lazy {
        ConstraintSet().apply {
            clone(baseConstraintSet)
            clear(R.id.backButton, ConstraintSet.TOP)
            connect(R.id.backButton, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        setColorNavigationBar(appBar = true)

        navController.navigate(R.id.previewPDFFragment)

        with(binding) {
            backButton.setOnClickListener { finish() }
            bottomSheetFileInfos.updateWithExternalFile(getFakeFile())
            bottomSheetFileInfos.init(this@PreviewPDFActivity)
            bottomSheetBehavior = BottomSheetBehavior.from(bottomSheetFileInfos)
        }
    }

    override fun onStart() {
        super.onStart()
        setupWindowInsetsListener()
        setupTransparentStatusBar()
    }

    override fun openWithClicked(context: Context) {
        super.openWithClicked(context)
        openWith(
            externalPDFUri,
            contentResolver.getType(externalPDFUri),
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
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
        val transition = AutoTransition().apply {
            duration = 125
        }

        TransitionManager.beginDelayedTransition(binding.pdfContainer, transition)

        val shouldHide = state != BottomSheetBehavior.STATE_HIDDEN

        isHideable = shouldHide
        state = if (shouldHide) {
            collapsedConstraintSet.applyTo(binding.pdfContainer)
            BottomSheetBehavior.STATE_HIDDEN
        } else {
            baseConstraintSet.applyTo(binding.pdfContainer)
            BottomSheetBehavior.STATE_COLLAPSED
        }

        toggleSystemBar(show = !shouldHide)
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

    private fun setupWindowInsetsListener() = with(binding) {
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, windowInsets ->
            with(windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())) {
                val defaultMargin = context.resources.getDimension(R.dimen.marginStandardMedium).toInt()
                backButton.setMargins(top = top + defaultMargin)
                bottomSheetFileInfos.setPadding(0, 0, 0, bottom)
            }
            windowInsets
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
