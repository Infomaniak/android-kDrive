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
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewPdfBinding
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.getPageNumberChip
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.PreviewPDFUtils
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.pdfview.PDFView
import com.infomaniak.lib.pdfview.scroll.DefaultScrollHandle
import com.shockwave.pdfium.PdfPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PreviewPDFFragment : PreviewFragment() {

    private var binding: FragmentPreviewPdfBinding by safeBinding()

    private val previewPDFViewModel by viewModels<PreviewPDFViewModel>()

    private val passwordDialog: PasswordDialogFragment by lazy {
        PasswordDialogFragment().apply { onPasswordEntered = ::showPdf }
    }

    private val navigationArgs: PreviewPDFFragmentArgs by navArgs()

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
    private var isPasswordProtected = false
    private var isDownloading = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPdfBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.downloadLayout) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile() && navigationArgs.fileURI == null) return@with

        if (navigationArgs.fileURI != null) {
            showPdf()
        } else {
            container.layoutTransition?.setAnimateParentHierarchy(false)

            fileIcon.setImageResource(file.getFileType().icon)
            fileName.text = file.name
            downloadProgress.isVisible = true

            previewPDFViewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
                if (progress >= 100 && previewPDFViewModel.isJobCancelled()) downloadPdf()
                downloadProgress.progress = progress
            }
        }

        previewDescription.apply {
            setText(R.string.previewDownloadIndication)
            isVisible = true
        }

        initViewsForFullscreen(root, binding.pdfView)

        bigOpenWithButton.apply {
            isGone = true
            setOnClickListener { showPasswordDialog() }
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

    private fun initViewsForFullscreen(vararg views: View) {
        views.forEach { view ->
            with(view) {
                isVisible = true
                setOnClickListener { toggleFullscreen() }
            }
        }
    }

    private fun showPdf(password: String? = null) {
        if (!binding.pdfView.isShown || isPasswordProtected) {
            lifecycleScope.launch {
                withResumed {
                    with(getConfigurator(navigationArgs.fileURI, pdfFile)) {
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
                        onLoad { pageCount ->
                            binding.downloadLayout.root.isGone = true
                            dismissPasswordDialog()
                            updatePageNumber(totalPage = pageCount)
                        }
                        onPageChange { currentPage, pageCount ->
                            updatePageNumber(
                                currentPage = currentPage,
                                totalPage = pageCount
                            )
                        }
                        onError { exception -> if (exception is PdfPasswordException) onPdfPasswordError() }
                        onAttach {
                            // This is to handle the case where we swipe in the ViewPager and we want to go back to
                            // a previously opened PDF. In that case, we want to display the default loader instead of
                            // an empty screen
                            if (pdfFile != null && !binding.pdfView.isShown) binding.downloadLayout.root.isVisible = true
                        }
                        load()
                    }

                    getPageNumberChip()?.isVisible = true
                }
            }
        }
    }

    private fun getConfigurator(uriString: String?, pdfFile: IOFile?): PDFView.Configurator {
        return uriString?.let { binding.pdfView.fromUri(Uri.parse(uriString)) } ?: binding.pdfView.fromFile(pdfFile)
    }

    private fun onPdfPasswordError() {
        // This is to handle the case where we have opened a PDF with a password so in order
        // for the user to be able to open it, we display the error layout
        binding.downloadLayout.root.isVisible = true
        isPasswordProtected = true
        if (passwordDialog.isAdded) onPDFLoadError() else displayError()
    }

    private fun displayError() {
        binding.downloadLayout.apply {
            downloadProgress.isGone = true
            previewDescription.setText(R.string.previewFileProtectedError)
            bigOpenWithButton.text = resources.getString(R.string.buttonUnlock)
            bigOpenWithButton.isVisible = true
        }

        if (navigationArgs.fileURI == null) previewSliderViewModel.pdfIsDownloading.value = false
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
        getPageNumberChip()?.text = getString(R.string.previewPdfPages, currentPage + 1, totalPage)
    }

    private fun downloadPdf() = with(binding.downloadLayout) {
        if (pdfFile == null) {
            previewSliderViewModel.pdfIsDownloading.value = true
            isDownloading = true
            previewPDFViewModel.downloadPdfFile(requireContext(), file, previewSliderViewModel.userDrive)
                .observe(viewLifecycleOwner) { apiResponse ->
                    apiResponse.data?.let { pdfFile ->
                        this@PreviewPDFFragment.pdfFile = pdfFile
                        showPdf()
                    } ?: run {
                        downloadProgress.isGone = true
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

        fun downloadPdfFile(context: Context, file: File, userDrive: UserDrive): LiveData<ApiResponse<IOFile>> {
            pdfJob.cancel()
            pdfJob = Job()

            return liveData(Dispatchers.IO + pdfJob) {
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
        const val TAG = "PreviewPDFFragment"

        private const val PDF_VIEW_HANDLE_TEXT_INDICATOR_SIZE_DP = 16
        private const val START_END_SPACING_DP = 200
        private const val MIN_ZOOM = 0.93f
        private const val MID_ZOOM = 3f
        private const val MAX_ZOOM = 6f
        private const val WIDTH_HANDLE_DP = 65
        private const val HEIGHT_HANDLE_DP = 40
        private const val HANDLE_PAGE_PDF_PADDING_TOP_DP = 120
        private const val HANDLE_PAGE_PDF_PADDING_BOTTOM_DP = 130
    }
}
