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
package com.infomaniak.drive.ui.fileList.preview

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewPdfBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.getPreviewPDFHandler
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.setPageNumber
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.setPageNumberChipVisibility
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.PreviewPDFUtils
import com.infomaniak.drive.utils.printPdf
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.pdfview.PDFView
import com.infomaniak.lib.pdfview.scroll.DefaultScrollHandle
import com.shockwave.pdfium.PdfPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.IOException

class PreviewPDFFragment : PreviewFragment(), PDFPrintListener {

    private var binding: FragmentPreviewPdfBinding by safeBinding()

    private val previewPDFViewModel by viewModels<PreviewPDFViewModel>()

    private val passwordDialog: PasswordDialogFragment by lazy {
        PasswordDialogFragment().apply { onPasswordEntered = ::showPdf }
    }

    private val pdfNavigationArgs: PreviewPDFFragmentArgs by navArgs()

    private val previewPDFHandler by lazy { getPreviewPDFHandler() }

    private val scrollHandle by lazy {
        DefaultScrollHandle(requireContext()).apply {
            val handle: View = View.inflate(requireContext(), R.layout.pdf_handle_view, null)
            setPageHandleView(handle, handle.findViewById(R.id.pageIndicator))
            setHandleSize(WIDTH_HANDLE_DP, HEIGHT_HANDLE_DP)
            setHandlePaddings(0, HANDLE_PAGE_PDF_PADDING_TOP_DP, 0, HANDLE_PAGE_PDF_PADDING_BOTTOM_DP)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
    }

    private var pdfFile: IOFile? = null
    private var isDownloading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPdfBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.downloadLayout) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile() && !previewPDFHandler.isExternalFile()) return@with

        if (previewPDFHandler.isExternalFile()) {
            fileIcon.setImageResource(ExtensionType.PDF.icon)
            fileName.text = previewPDFHandler.fileName
            showPdf()
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

        initViewsForFullscreen(root, binding.pdfView)

        bigOpenWithButton.apply {
            isGone = true
            setOnClickListener { if (previewPDFHandler.isPasswordProtected) showPasswordDialog() else openWithClicked() }
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (menuVisible) {
            when {
                isDownloading -> previewSliderViewModel.pdfIsDownloading.value = isDownloading
                pdfFile == null -> downloadPdf()
                else -> showPdf()
            }
        }
    }

    override fun onPause() {
        previewPDFViewModel.cancelJobs()
        super.onPause()
    }

    override fun generatePagesAsBitmaps(fileName: String) {
        // When we try to generate bitmaps for a password protected file with the default PDF reader, we don't have a file
        // So we need to pass the file name
        previewPDFHandler.fileName = fileName
        binding.pdfView.loadPagesForPrinting()
    }

    private fun initViewsForFullscreen(vararg views: View) {
        views.forEach { view ->
            with(view) {
                isVisible = true
                setOnClickListener { toggleFullscreen() }
            }
        }
    }

    private fun getConfigurator(fileUri: Uri?, pdfFile: IOFile?): PDFView.Configurator = with(binding.pdfView) {
        return if (previewPDFHandler.isExternalFile()) fromUri(fileUri) else fromFile(pdfFile)
    }

    private fun showPdf(password: String? = null) = with(previewPDFHandler) {
        if (!binding.pdfView.isShown || isPasswordProtected) {
            lifecycleScope.launch {
                withResumed {
                    with(getConfigurator(pdfNavigationArgs.fileUri, pdfFile)) {
                        password(password)
                        disableLongPress()
                        enableAnnotationRendering(true)
                        enableDoubletap(true)
                        pageFling(false)
                        pageSnap(false)
                        scrollHandle(scrollHandle)
                        spacing(PDF_VIEW_HANDLE_TEXT_INDICATOR_SIZE_DP)
                        startEndSpacing(START_END_SPACING_DP, START_END_SPACING_DP)
                        zoom(MIN_ZOOM, MID_ZOOM, MAX_ZOOM)
                        swipeHorizontal(false)
                        touchPriority(true)
                        thumbnailRatio(THUMBNAIL_RATIO)
                        onLoad { pageCount ->
                            // We can arrive here with a file different from a real PDF like OpenOffice documents
                            val canPrintFile = externalFileUri != null || file.extensionType == ExtensionType.PDF.value
                            shouldHidePrintOption(isGone = !canPrintFile)
                            binding.downloadLayout.root.isGone = true
                            dismissPasswordDialog()
                            updatePageNumber(totalPage = pageCount)
                            pdfViewPrintListener = this@PreviewPDFFragment
                        }
                        onPageChange { currentPage, pageCount ->
                            updatePageNumber(
                                currentPage = currentPage,
                                totalPage = pageCount,
                            )
                        }
                        onReadyForPrinting { pagesAsBitmap ->
                            val fileName = if (isExternalFile()) fileName else file.name
                            requireContext().printPdf(fileName = fileName, bitmaps = pagesAsBitmap)
                        }
                        onError(::handleException)
                        onAttach {
                            // This is to handle the case where we swipe in the ViewPager and we want to go back to
                            // a previously opened PDF. In that case, we want to display the default loader instead of
                            // an empty screen
                            if (pdfFile != null && !binding.pdfView.isShown) binding.downloadLayout.root.isVisible = true
                        }
                        load()
                    }

                    setPageNumberChipVisibility(isVisible = true)
                }
            }
        }
    }

    private fun handleException(exception: Throwable) = with(previewPDFHandler) {
        shouldHidePrintOption(isGone = true)

        when {
            exception is PdfPasswordException -> {
                isPasswordProtected = true
                onPDFPasswordError()
            }
            exception is IOException && fileSize == 0L -> {
                displayError(isEmptyFileError = true)
            }
            else -> {
                displayError()
            }
        }
    }

    private fun onPDFPasswordError() = with(previewPDFHandler) {
        // This is to handle the case where we have opened a PDF with a password so in order
        // for the user to be able to open it, we display the error layout
        isPasswordProtected = true
        binding.downloadLayout.root.isVisible = true
        if (passwordDialog.isAdded) onPDFLoadError() else displayError(isPasswordError = true)
    }

    private fun getErrorString(isPasswordError: Boolean, isEmptyFileError: Boolean): Int {
        return when {
            isPasswordError -> R.string.previewFileProtectedError
            isEmptyFileError -> R.string.emptyFilePreviewError
            else -> R.string.previewLoadError
        }
    }

    private fun displayError(isPasswordError: Boolean = false, isEmptyFileError: Boolean = false) {
        binding.downloadLayout.apply {
            downloadProgressIndicator.isGone = true
            previewDescription.setText(getErrorString(isPasswordError, isEmptyFileError))

            if (isPasswordError) bigOpenWithButton.text = resources.getString(R.string.buttonUnlock)
            bigOpenWithButton.isVisible = isPasswordError
        }

        if (!previewPDFHandler.isExternalFile()) previewSliderViewModel.pdfIsDownloading.value = false
        isDownloading = false
    }

    private fun dismissPasswordDialog() {
        if (passwordDialog.isAdded) passwordDialog.dismiss()
    }

    private fun onPDFLoadError() = with(passwordDialog) {
        if (isAdded) onWrongPasswordEntered() else showPasswordDialog()
    }

    private fun showPasswordDialog() {
        passwordDialog.show(childFragmentManager, PasswordDialogFragment::class.java.toString())
    }

    private fun updatePageNumber(currentPage: Int = 1, totalPage: Int) {
        setPageNumber(currentPage + 1, totalPage)
    }

    private fun downloadPdf() = with(binding.downloadLayout) {
        if (pdfFile == null) {
            previewSliderViewModel.pdfIsDownloading.value = true
            isDownloading = true
            previewPDFViewModel.downloadPdfFile(
                context = requireContext(),
                file = file,
                userDrive = previewSliderViewModel.userDrive,
                shareLinkUuid = previewSliderViewModel.shareLinkUuid,
            ).observe(viewLifecycleOwner) { apiResponse ->
                apiResponse.data?.let { pdfFile ->
                    this@PreviewPDFFragment.pdfFile = pdfFile
                    showPdf()
                } ?: run {
                    downloadProgressIndicator.isGone = true
                    previewDescription.setText(apiResponse.translatedError)
                    bigOpenWithButton.isVisible = true
                }
                previewSliderViewModel.pdfIsDownloading.value = false
                isDownloading = false
            }
        } else {
            showPdf()
        }
    }

    class PreviewPDFViewModel(app: Application) : AndroidViewModel(app) {
        val downloadProgress = MutableLiveData<Int>()

        private var pdfJob = Job()

        fun downloadPdfFile(
            context: Context,
            file: File,
            userDrive: UserDrive,
            shareLinkUuid: String,
        ): LiveData<ApiResponse<IOFile>> {
            pdfJob.cancel()
            pdfJob = Job()

            return liveData(Dispatchers.IO + pdfJob) {
                val pdfFile = PreviewPDFUtils.convertPdfFileToIOFile(context, file, userDrive, shareLinkUuid) {
                    viewModelScope.launch(Dispatchers.Main) {
                        downloadProgress.value = it
                    }
                }
                emit(pdfFile)
            }
        }

        fun isJobCancelled() = pdfJob.isCancelled

        fun cancelJobs() {
            pdfJob.cancel()
        }

        override fun onCleared() {
            cancelJobs()
            super.onCleared()
        }
    }

    companion object {
        private const val PDF_VIEW_HANDLE_TEXT_INDICATOR_SIZE_DP = 16
        private const val START_END_SPACING_DP = 200
        private const val MIN_ZOOM = 0.93f
        private const val MID_ZOOM = 3f
        private const val MAX_ZOOM = 6f
        private const val WIDTH_HANDLE_DP = 65
        private const val HEIGHT_HANDLE_DP = 40
        private const val HANDLE_PAGE_PDF_PADDING_TOP_DP = 120
        private const val HANDLE_PAGE_PDF_PADDING_BOTTOM_DP = 130
        private const val THUMBNAIL_RATIO = 0.5f
    }
}

fun interface PDFPrintListener {
    fun generatePagesAsBitmaps(fileName: String)
}
