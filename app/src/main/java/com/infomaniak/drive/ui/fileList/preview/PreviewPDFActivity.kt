/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackPdfActivityActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.ActivityPreviewPdfBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.openWith
import com.infomaniak.drive.utils.saveToKDrive
import com.infomaniak.drive.utils.setupBottomSheetFileBehavior
import com.infomaniak.drive.utils.shareFile
import com.infomaniak.drive.utils.toggleSystemBar
import com.infomaniak.drive.views.FileInfoActionsView.OnItemClickListener
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import androidx.core.net.toUri

class PreviewPDFActivity : AppCompatActivity(), OnItemClickListener {

    val binding: ActivityPreviewPdfBinding by lazy { ActivityPreviewPdfBinding.inflate(layoutInflater) }

    override val ownerFragment = null
    override val currentContext by lazy { this }
    override val currentFile = null

    val previewPDFHandler by lazy {
        PreviewPDFHandler(
            context = this,
            externalFileUri = intent.dataString?.toUri(),
            setPrintVisibility = binding.bottomSheetFileInfos::isPrintingHidden,
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

            header.setup(onBackClicked = ::finish, onOpenWithClicked = ::openWith)
            header.enableEdgeToEdge(withBottom = false)
        }

        initBottomSheet()
    }

    private fun initBottomSheet() = with(binding) {
        setupBottomSheetFileBehavior(bottomSheetBehavior, isDraggable = true, isFitToContents = true)
        bottomSheetFileInfos.apply {
            lifecycleScope.launch(start = CoroutineStart.UNDISPATCHED) { updateWithExternalFile(getFakeFile()) }
            initOnClickListener(this@PreviewPDFActivity)
            isDownloadHidden(isGone = true)
        }
    }

    override fun onStart() = with(binding) {

        super.onStart()

        header.setupWindowInsetsListener(
            rootView = root,
            bottomSheetView = bottomSheetFileInfos,
        ) {
            pdfContainer.setMargins(right = it?.right ?: 0)
        }
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
                R.navigation.preview_pdf_navigation,
                PreviewPDFFragmentArgs(fileUri = previewPDFHandler.externalFileUri).toBundle(),
            )
        }
    }

    override fun shareFile() {
        trackPdfActivityActionEvent(MatomoName.SendFileCopy)
        shareFile { previewPDFHandler.externalFileUri }
    }

    override fun saveToKDrive() {
        trackPdfActivityActionEvent(MatomoName.SaveToKDrive)
        previewPDFHandler.externalFileUri?.let(::saveToKDrive)
    }

    override fun openWith() {
        openWith(externalFileUri = previewPDFHandler.externalFileUri)
    }

    override fun printClicked() {
        trackPdfActivityActionEvent(MatomoName.PrintPdf)
        previewPDFHandler.printClicked(
            context = this,
            onError = { showSnackbar(R.string.errorFileNotFound) },
        )
    }

    override fun displayInfoClicked() = Unit
    override fun fileRightsClicked() = Unit
    override fun goToFolder() = Unit
    override fun manageCategoriesClicked(fileId: Int) = Unit
    override fun onCacheAddedToOffline() = Unit
    override fun onDeleteFile(onApiResponse: () -> Unit) = Unit
    override fun onDuplicateFile(destinationFolder: File) = Unit
    override fun onLeaveShare(onApiResponse: () -> Unit) = Unit
    override fun onMoveFile(destinationFolder: File, isSharedWithMe: Boolean) = Unit
    override fun onRenameFile(newName: String, onApiResponse: () -> Unit) = Unit
    override fun removeOfflineFile(offlineLocalPath: IOFile, cacheFile: IOFile) = Unit
}
