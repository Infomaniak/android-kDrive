/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.databinding.FragmentPreviewPdfBinding
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.getPageNumberChip
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.PreviewPDFUtils
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.pdfview.listener.OnErrorListener
import com.infomaniak.lib.pdfview.scroll.DefaultScrollHandle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PreviewPDFFragment : PreviewFragment() {

    private var binding: FragmentPreviewPdfBinding by safeBinding()

    private val previewPDFViewModel by viewModels<PreviewPDFViewModel>()
    private val passwordDialog: PasswordDialogFragment by lazy {
        PasswordDialogFragment(
            onPasswordEntered = { password ->
                showPdf(password)
            },
            onCancel = {
                findNavController().popBackStack()
            }
        )
    }

    private val scrollHandle by lazy {
        DefaultScrollHandle(requireContext()).apply {
            val handle: View = layoutInflater.inflate(R.layout.pdf_handle_view, null)
            setPageHandleView(handle, handle.findViewById(R.id.pageIndicator))
            setHandleSize(WIDTH_HANDLE_DP, HEIGHT_HANDLE_DP)
            setHandlePaddings(0, HANDLE_PAGE_PDF_PADDING_TOP_DP, 0, HANDLE_PAGE_PDF_PADDING_BOTTOM_DP)
            setTextColor(ContextCompat.getColor(context, R.color.white))
        }
    }

    private var pdfFile: IOFile? = null
    private var isDownloading = false

    private val onPdfLoadError: OnErrorListener = OnErrorListener {
        passwordDialog.dialog?.let {
            if (it.isShowing.not()) {
                passwordDialog.show(parentFragmentManager, this.javaClass::class.toString())
            } else {
                passwordDialog.onWrongPasswordEntered()
            }
        } ?: passwordDialog.show(parentFragmentManager, this.javaClass::class.toString())
    }


    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPdfBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding.downloadLayout) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return@with

        container.layoutTransition?.setAnimateParentHierarchy(false)

        fileIcon.setImageResource(file.getFileType().icon)
        fileName.text = file.name
        downloadProgress.isVisible = true

        previewDescription.apply {
            setText(R.string.previewDownloadIndication)
            isVisible = true
        }

        root.apply {
            isVisible = true
            setOnClickListener { toggleFullscreen() }
        }

        bigOpenWithButton.apply {
            isGone = true
            setOnClickListener { openWithClicked() }
        }

        previewPDFViewModel.downloadProgress.observe(viewLifecycleOwner) { progress ->
            if (progress >= 100 && previewPDFViewModel.isJobCancelled()) downloadPdf()
            downloadProgress.progress = progress
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (menuVisible) {
            if (isDownloading) previewSliderViewModel.pdfIsDownloading.value = isDownloading else downloadPdf()
        }
    }

    override fun onPause() {
        previewPDFViewModel.cancelJobs()
        super.onPause()
    }

    private fun showPdf() = with(binding) {
        lifecycleScope.launch {
            withResumed {
                downloadLayout.root.isGone = true
                with(pdfView.fromFile(pdfFile)) {
                    disableLongpress()
                    enableAnnotationRendering(true)
                    enableDoubletap(true)
                    pageFling(false)
                    pageSnap(false)
                    scrollHandle(scrollHandle)
                    spacing(PDF_VIEW_HANDLE_TEXT_INDICATOR_SIZE_DP)
                    swipeHorizontal(false)
                    touchPriority(true)
                    onLoad { pageCount -> updatePageNumber(totalPage = pageCount) }
                    onPageChange { currentPage, pageCount ->
                        updatePageNumber(currentPage = currentPage, totalPage = pageCount)
                    }
					onError(onPdfLoadError)
                    load()
                }

                getPageNumberChip()?.isVisible = true
            }
        }
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
                val pdfCore = PreviewPDFUtils.convertPdfFileToPdfCore(context, file, userDrive) {
                    viewModelScope.launch(Dispatchers.Main) {
                        downloadProgress.value = it
                    }
                }
                emit(pdfCore)
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
        private const val WIDTH_HANDLE_DP = 65
        private const val HEIGHT_HANDLE_DP = 40
        private const val HANDLE_PAGE_PDF_PADDING_TOP_DP = 120
        private const val HANDLE_PAGE_PDF_PADDING_BOTTOM_DP = 130
    }
}
