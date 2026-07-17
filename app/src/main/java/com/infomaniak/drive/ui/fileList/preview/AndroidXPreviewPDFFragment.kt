/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
import android.os.Build
import android.os.Bundle
import android.os.OperationCanceledException
import android.view.ContextThemeWrapper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.commitNow
import androidx.fragment.app.viewModels
import androidx.pdf.PdfDocument
import androidx.pdf.viewer.fragment.PdfViewerFragment
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.databinding.FragmentAndroidxPreviewPdfBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.getPreviewPDFHandler
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.setFullscreen
import com.infomaniak.drive.utils.IOFile

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class AndroidXPreviewPDFFragment : PreviewFragment() {

    private var binding: FragmentAndroidxPreviewPdfBinding by safeBinding()

    private val previewPDFViewModel: PreviewPDFFragment.PreviewPDFViewModel by viewModels()
    private val pdfNavigationArgs: PreviewPDFFragmentArgs by lazy {
        PreviewPDFFragmentArgs.fromBundle(requireArguments())
    }
    private val previewPDFHandler by lazy { getPreviewPDFHandler() }

    private var pdfFile: IOFile? = null
    private var isDownloading = false

    private val pdfViewerFragment: KDrivePdfViewerFragment
        get() = childFragmentManager.findFragmentByTag(PDF_VIEWER_TAG) as KDrivePdfViewerFragment

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentAndroidxPreviewPdfBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.downloadLayout) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile() && !previewPDFHandler.isExternalFile()) return@with

        ensurePdfViewerFragment()

        if (previewPDFHandler.isExternalFile()) {
            fileIcon.setImageResource(ExtensionType.PDF.icon)
            fileName.text = previewPDFHandler.fileName
            showPdf(pdfNavigationArgs.fileUri)
        } else {
            container.layoutTransition?.setAnimateParentHierarchy(false)
            fileIcon.setImageResource(file.getFileType().icon)
            fileName.text = file.name
            downloadProgressIndicator.isVisible = true

            previewPDFViewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
                if (progress >= 100 && previewPDFViewModel.isJobCancelled()) downloadPdf()
                downloadProgressIndicator.progress = progress
            }
        }

        previewDescription.apply {
            setText(R.string.previewDownloadIndication)
            isVisible = true
        }

        bigOpenWithButton.apply {
            isGone = true
            setOnClickListener { openWithClicked() }
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (menuVisible) {
            when {
                isDownloading -> previewSliderViewModel.pdfIsDownloading.value = true
                pdfFile == null && !previewPDFHandler.isExternalFile() -> downloadPdf()
                pdfFile != null -> showPdf(pdfFile!!.toUri())
            }
        }
    }

    override fun onPause() {
        previewPDFViewModel.cancelJobs()
        super.onPause()
    }

    override fun canDisplayWithoutCurrentFile() = previewPDFHandler.isExternalFile()

    internal fun onLoadDocumentSuccess(document: PdfDocument) {
        previewPDFHandler.apply {
            pdfViewPrintListener = null
            shouldHidePrintOption(isGone = !canPrintFile())
        }
        binding.downloadLayout.root.isGone = true
    }

    internal fun onLoadDocumentError(error: Throwable) {
        if (error is OperationCanceledException || error.cause is OperationCanceledException) return

        previewPDFHandler.shouldHidePrintOption(isGone = true)
        displayError(isEmptyFileError = previewPDFHandler.fileSize == 0L)
    }

    internal fun onRequestImmersiveMode(enterImmersive: Boolean) {
        setFullscreen(enterImmersive)
    }

    private fun ensurePdfViewerFragment() {
        if (childFragmentManager.findFragmentByTag(PDF_VIEWER_TAG) != null) return

        childFragmentManager.commitNow {
            replace(R.id.pdfViewerContainer, KDrivePdfViewerFragment(), PDF_VIEWER_TAG)
        }
    }

    private fun showPdf(uri: Uri?) {
        uri ?: return displayError()
        binding.downloadLayout.root.isGone = true
        pdfViewerFragment.documentUri = uri
    }

    private fun canPrintFile(): Boolean {
        return previewPDFHandler.isExternalFile() || file.isPDF()
    }

    private fun displayError(isEmptyFileError: Boolean = false) {
        binding.downloadLayout.apply {
            downloadProgressIndicator.isGone = true
            previewDescription.setText(if (isEmptyFileError) R.string.emptyFilePreviewError else R.string.previewLoadError)
            bigOpenWithButton.isVisible = previewPDFHandler.isExternalFile()
            root.isVisible = true
        }

        if (!previewPDFHandler.isExternalFile()) previewSliderViewModel.pdfIsDownloading.value = false
        isDownloading = false
    }

    private fun downloadPdf() = with(binding.downloadLayout) {
        if (pdfFile != null) {
            showPdf(pdfFile!!.toUri())
            return@with
        }

        previewSliderViewModel.pdfIsDownloading.value = true
        isDownloading = true
        previewPDFViewModel.downloadPdfFile(
            context = requireContext(),
            file = file,
            userDrive = previewSliderViewModel.userDrive,
        ).observe(viewLifecycleOwner) { apiResponse ->
            apiResponse.data?.let { downloadedFile ->
                pdfFile = downloadedFile
                showPdf(downloadedFile.toUri())
            } ?: run {
                downloadProgressIndicator.isGone = true
                previewDescription.setText(apiResponse.translateError())
                bigOpenWithButton.isVisible = true
            }
            previewSliderViewModel.pdfIsDownloading.value = false
            isDownloading = false
        }
    }

    companion object {
        private const val PDF_VIEWER_TAG = "androidx-pdf-viewer"
    }
}

@RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
class KDrivePdfViewerFragment : PdfViewerFragment() {

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.PdfViewerTheme)
        return super.onGetLayoutInflater(savedInstanceState).cloneInContext(themedContext)
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        (parentFragment as? AndroidXPreviewPDFFragment)?.onLoadDocumentSuccess(document)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        (parentFragment as? AndroidXPreviewPDFFragment)?.onLoadDocumentError(error)
    }

    override fun onRequestImmersiveMode(enterImmersive: Boolean) {
        super.onRequestImmersiveMode(enterImmersive)
        (parentFragment as? AndroidXPreviewPDFFragment)?.onRequestImmersiveMode(enterImmersive)
    }
}
