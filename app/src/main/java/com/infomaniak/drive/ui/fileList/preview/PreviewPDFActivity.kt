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

import android.net.Uri
import android.os.Bundle
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
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.setMargins

class PreviewPDFActivity : AppCompatActivity(), OnItemClickListener {

    val binding: ActivityPreviewPdfBinding by lazy { ActivityPreviewPdfBinding.inflate(layoutInflater) }

    override val ownerFragment = null
    override val currentContext by lazy { this }
    override val currentFile = null

    val previewPDFHandler by lazy {
        PreviewPDFHandler(
            context = this,
            externalFileUri = Uri.parse(intent.dataString),
            setPrintVisibility = { isGone -> binding.bottomSheetFileInfos.isPrintingHidden(isGone) },
        )
    }

    private val navController by lazy { setupNavController() }
    private val navHostFragment by lazy { supportFragmentManager.findFragmentById(R.id.hostFragment) as NavHostFragment }

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
                onOpenWithClicked = { openWith(externalFileUri = previewPDFHandler.externalFileUri) },
            )
        }

        initBottomSheet()
    }

    private fun initBottomSheet() = with(binding) {
        setupBottomSheetFileBehavior(bottomSheetBehavior, isDraggable = true, isFitToContents = true)
        bottomSheetFileInfos.updateWithExternalFile(getFakeFile())
        bottomSheetFileInfos.initOnClickListener(this@PreviewPDFActivity)
    }

    override fun onStart() = with(binding) {

        super.onStart()

        header.setupWindowInsetsListener(
            rootView = root,
            bottomSheetView = bottomSheetFileInfos,
        ) {
            pdfContainer.setMargins(right = it?.right ?: 0)
        }

        setupStatusBarForPreview()
    }

    fun toggleFullscreen() {
        isOverlayShown = !isOverlayShown
        binding.header.toggleVisibility(isOverlayShown)
        bottomSheetBehavior.state = if (isOverlayShown) BottomSheetBehavior.STATE_COLLAPSED else BottomSheetBehavior.STATE_HIDDEN
        toggleSystemBar(isOverlayShown)
    }

    // This is necessary to be able to use the same view details we have in kDrive (file name, file type and size)
    private fun getFakeFile(): File = with(previewPDFHandler) {
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
            setGraph(
                R.navigation.view_pdf,
                PreviewPDFFragmentArgs(fileUri = previewPDFHandler.externalFileUri).toBundle()
            )
        }
    }

    override fun shareFile() {
        shareFile { previewPDFHandler.externalFileUri }
    }

    override fun saveToKDrive() {
        previewPDFHandler.externalFileUri?.let { saveToKDrive(it) }
    }

    override fun openWith() {
        openWith(externalFileUri = previewPDFHandler.externalFileUri)
    }

    override fun printClicked() {
        super.printClicked()
        previewPDFHandler.printClicked(this) {
            showSnackbar(R.string.errorFileNotFound)
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
