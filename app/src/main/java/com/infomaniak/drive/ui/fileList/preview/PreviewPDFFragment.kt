/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2026 Infomaniak Network SA
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

import android.annotation.SuppressLint
import android.app.Application
import android.content.Context
import android.graphics.RectF
import android.os.Bundle
import android.os.OperationCanceledException
import android.util.SparseArray
import android.view.ContextThemeWrapper
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.withResumed
import androidx.navigation.fragment.navArgs
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.network.models.ApiResponse
import com.infomaniak.core.network.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewPdfBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.getHeader
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.getPreviewPDFHandler
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.ui.publicShare.PublicSharePreviewSliderFragment
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.PreviewPDFUtils
import com.infomaniak.drive.utils.printPdf
import com.infomaniak.lib.pdfview.UnifiedPdfPreviewView
import com.infomaniak.lib.pdfview.scroll.DefaultScrollHandle
import com.shockwave.pdfium.PdfPasswordException
import androidx.pdf.ExperimentalPdfApi
import androidx.pdf.PdfDocument
import androidx.pdf.view.PdfView
import androidx.pdf.viewer.fragment.PdfViewerFragment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import okio.IOException

class PreviewPDFFragment : PreviewFragment(), PDFPrintListener {

    private var binding: FragmentPreviewPdfBinding by safeBinding()

    private val previewPDFViewModel: PreviewPDFViewModel by viewModels()

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
    private var totalPageCount: Int? = null
    private var currentPageIndex: Int? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPdfBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.downloadLayout) {
        super.onViewCreated(view, savedInstanceState)
        binding.pdfView.setNativeFragmentFactory { KDrivePdfViewerFragment() }
        binding.pdfView.attach(childFragmentManager, viewLifecycleOwner)

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

        initViewsForFullscreen(root)

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

    override fun onDestroyView() {
        view?.findViewById<UnifiedPdfPreviewView>(R.id.pdfView)?.detach()
        super.onDestroyView()
    }

    override fun canDisplayWithoutCurrentFile() = previewPDFHandler.isExternalFile()

    override fun generatePagesAsBitmaps(fileName: String) {
        // UnifiedPdfPreviewView does not expose page bitmap extraction from its internal backend.
        // For password-protected files, fallback to external app handling.
        previewPDFHandler.fileName = fileName
        openWithClicked()
    }

    private fun initViewsForFullscreen(vararg views: View) {
        views.forEach { view ->
            with(view) {
                isVisible = true
                setOnClickListener { toggleFullscreen() }
            }
        }
    }

    private fun showPdf(password: String? = null) = with(previewPDFHandler) {
        lifecycleScope.launch {
            withResumed {
                val uri = if (previewPDFHandler.isExternalFile()) pdfNavigationArgs.fileUri else pdfFile?.toUri()
                if (uri == null) {
                    displayError()
                    return@withResumed
                }
                binding.pdfView.loadFromUri(
                    uri = uri,
                    password = password,
                    fallbackConfigurator = UnifiedPdfPreviewView.FallbackConfigurator { configurator ->
                        configurator
                            .disableLongPress()
                            .enableAnnotationRendering(true)
                            .enableDoubletap(true)
                            .pageFling(false)
                            .pageSnap(false)
                            .scrollHandle(scrollHandle)
                            .pageSeparatorSpacing(PDF_VIEW_HANDLE_TEXT_INDICATOR_SIZE_DP)
                            .startEndSpacing(START_SPACING_DP, END_SPACING_DP)
                            .zoom(MIN_ZOOM, MID_ZOOM, MAX_ZOOM)
                            .swipeHorizontal(false)
                            .touchPriority(true)
                            .thumbnailRatio(THUMBNAIL_RATIO)
                            .onTap {
                                toggleFullscreen()
                                true
                            }
                            .onLoad { pageCount ->
                                // We can arrive here with a file different from a real PDF like OpenOffice documents
                                shouldHidePrintOption(isGone = !canPrintFile())

                                dismissPasswordDialog()
                                updatePageNumber(totalPage = pageCount)

                                pdfViewPrintListener = this@PreviewPDFFragment

                                binding.downloadLayout.root.isGone = true

                                totalPageCount = pageCount
                                setPageNumberChipVisibility(true)
                            }
                            .onPageChange { currentPage, pageCount ->
                                currentPageIndex = currentPage
                                updatePageNumber(currentPage = currentPage, totalPage = pageCount)
                            }
                            .onReadyForPrinting { pagesAsBitmap ->
                                val fileName = if (isExternalFile()) fileName else file.name
                                requireContext().printPdf(fileName = fileName, bitmaps = pagesAsBitmap)
                            }
                            .onError(::handleException)
                            .onAttach {
                                // This is to handle the case where we swipe in the ViewPager and we want to go back to
                                // a previously opened PDF. In that case, we want to display the default loader instead of
                                // an empty screen
                                if (pdfFile != null && !binding.pdfView.isShown) binding.downloadLayout.root.isVisible = true
                            }
                    },
                )
                if (binding.pdfView.getCurrentPdfViewerMode() == UnifiedPdfPreviewView.ActivePdfViewerMode.ANDROIDX_NATIVE) {
                    totalPageCount = null
                    currentPageIndex = null
                    setPageNumberChipVisibility(false)
                    pdfViewPrintListener = null
                }
            }
        }
    }

    internal fun onNativeLoadSuccess(document: PdfDocument) {
        previewPDFHandler.apply {
            pdfViewPrintListener = null
            shouldHidePrintOption(isGone = !canPrintFile())
        }
        binding.downloadLayout.root.isGone = true
        totalPageCount = document.pageCount
        currentPageIndex = 0
        if (document.pageCount > 0) {
            updatePageNumber(currentPage = 0, totalPage = document.pageCount)
            setPageNumberChipVisibility(true)
        } else {
            setPageNumberChipVisibility(false)
        }
    }

    internal fun onNativeLoadError(error: Throwable) {
        if (error is OperationCanceledException || error.cause is OperationCanceledException) return

        previewPDFHandler.shouldHidePrintOption(isGone = true)
        displayError(isEmptyFileError = previewPDFHandler.fileSize == 0L)
    }

    internal fun onNativeSingleTap() {
        toggleFullscreen()
    }

    internal fun onNativePageChanged(currentPage: Int) {
        currentPageIndex = currentPage
        totalPageCount?.let { updatePageNumber(currentPage = currentPage, totalPage = it) }
    }

    private fun canPrintFile(): Boolean {
        return if (parentFragment is PublicSharePreviewSliderFragment) {
            (parentFragment as PublicSharePreviewSliderFragment).publicShareViewModel.canDownloadFiles && file.isPDF()
        } else {
            previewPDFHandler.externalFileUri != null || file.isPDF()
        }
    }

    fun tryToUpdatePageCount(): Boolean {
        return if (currentPageIndex != null && totalPageCount != null) {
            updatePageNumber(currentPage = currentPageIndex!!, totalPage = totalPageCount!!)
            true
        } else {
            false
        }
    }

    private fun handleException(exception: Throwable) = with(previewPDFHandler) {
        shouldHidePrintOption(isGone = true)

        when (exception) {
            is PdfPasswordException -> {
                isPasswordProtected = true
                onPDFPasswordError()
            }
            is IOException if fileSize == 0L -> {
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
            ).observe(viewLifecycleOwner) { apiResponse ->
                apiResponse.data?.let { pdfFile ->
                    this@PreviewPDFFragment.pdfFile = pdfFile
                    showPdf()
                } ?: run {
                    downloadProgressIndicator.isGone = true
                    previewDescription.setText(apiResponse.translateError())
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

        fun downloadPdfFile(context: Context, file: File, userDrive: UserDrive): LiveData<ApiResponse<IOFile>> {
            pdfJob.cancel()
            pdfJob = Job()

            return liveData(pdfJob) {
                val pdfFile = PreviewPDFUtils.convertPdfFileToIOFile(context, file, userDrive) {
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
        private const val START_SPACING_DP = 150
        private const val END_SPACING_DP = 75
        private const val MIN_ZOOM = 0.93f
        private const val MID_ZOOM = 3f
        private const val MAX_ZOOM = 6f
        private const val WIDTH_HANDLE_DP = 65
        private const val HEIGHT_HANDLE_DP = 40
        private const val HANDLE_PAGE_PDF_PADDING_TOP_DP = 120
        private const val HANDLE_PAGE_PDF_PADDING_BOTTOM_DP = 130
        private const val THUMBNAIL_RATIO = 0.5f

        //region PDF Preview
        fun Fragment.setPageNumberChipVisibility(isVisible: Boolean) {
            getHeader()?.setPageNumberVisibility(isVisible)
        }

        fun Fragment.setPageNumber(currentPage: Int, totalPage: Int) {
            getHeader()?.setPageNumberValue(currentPage, totalPage)
        }
        //endregion
    }
}

fun interface PDFPrintListener {
    fun generatePagesAsBitmaps(fileName: String)
}

class KDrivePdfViewerFragment : PdfViewerFragment() {

    override fun onGetLayoutInflater(savedInstanceState: Bundle?): LayoutInflater {
        val themedContext = ContextThemeWrapper(requireContext(), R.style.PdfViewerTheme)
        return super.onGetLayoutInflater(savedInstanceState).cloneInContext(themedContext)
    }

    @SuppressLint("ClickableViewAccessibility")
    @OptIn(ExperimentalPdfApi::class)
    override fun onPdfViewCreated(pdfView: PdfView) {
        super.onPdfViewCreated(pdfView)
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.marginStandardMedium)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.recyclerViewPaddingBottom)
        pdfView.clipToPadding = false
        // Bug in AndroidX PDFViewerFragment, we have to only apply the right padding (x2) to have a centered PDF
        pdfView.setPadding(0, verticalPadding, horizontalPadding * 2, verticalPadding)
        pdfView.addOnViewportChangedListener(
            object : PdfView.OnViewportChangedListener {
                override fun onViewportChanged(
                    firstVisiblePage: Int,
                    visiblePagesCount: Int,
                    pageLocations: SparseArray<RectF>,
                    zoomLevel: Float,
                ) {
                    (parentFragment as? PreviewPDFFragment)?.onNativePageChanged(firstVisiblePage)
                }
            },
        )
        val singleTapDetector = GestureDetector(
            pdfView.context,
            object : GestureDetector.SimpleOnGestureListener() {
                override fun onDown(e: MotionEvent): Boolean = true

                override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                    (parentFragment as? PreviewPDFFragment)?.onNativeSingleTap()
                    return true
                }
            },
        )
        pdfView.setOnTouchListener { _, event ->
            singleTapDetector.onTouchEvent(event)
            false
        }
    }

    override fun onLoadDocumentSuccess(document: PdfDocument) {
        super.onLoadDocumentSuccess(document)
        (parentFragment as? PreviewPDFFragment)?.onNativeLoadSuccess(document)
    }

    override fun onLoadDocumentError(error: Throwable) {
        super.onLoadDocumentError(error)
        (parentFragment as? PreviewPDFFragment)?.onNativeLoadError(error)
    }

}
