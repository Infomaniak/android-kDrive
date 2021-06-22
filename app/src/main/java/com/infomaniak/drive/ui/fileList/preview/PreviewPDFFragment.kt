/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.PreviewSliderViewModel
import com.infomaniak.drive.utils.PdfCore
import com.infomaniak.drive.utils.PreviewPDFUtils
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_preview_others.*
import kotlinx.android.synthetic.main.fragment_preview_pdf.*
import kotlinx.android.synthetic.main.fragment_preview_pdf.container
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PreviewPDFFragment : PreviewFragment {

    private var previewPDFAdapter: PreviewPDFAdapter? = null
    private val previewPDFViewModel by viewModels<PreviewPDFViewModel>()
    private val previewSliderViewModel: PreviewSliderViewModel by navGraphViewModels(R.id.previewSliderFragment)

    private var pdfCore: PdfCore? = null
    private var isDownloading = false

    constructor() : super()
    constructor(file: File) : super(file)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview_pdf, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container?.layoutTransition?.setAnimateParentHierarchy(false)

        fileIcon.setImageResource(previewViewModel.currentFile.getFileType().icon)
        fileName.text = previewViewModel.currentFile.name
        downloadProgress.visibility = VISIBLE
        previewDescription.setText(R.string.previewDownloadIndication)
        previewDescription.visibility = VISIBLE
        downloadLayout.visibility = VISIBLE
        pdfViewRecycler.visibility = GONE

        previewPDFViewModel.downloadProgress.observe(viewLifecycleOwner, Observer { progress ->
            if (progress >= 100 && previewPDFViewModel.pdfJob.isCancelled) downloadPdf()
            downloadProgress.progress = progress
        })

        pdfViewRecycler.onClicked = {
            (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
        }
        downloadLayout.setOnClickListener {
            (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
        }
    }

    override fun setMenuVisibility(menuVisible: Boolean) {
        super.setMenuVisibility(menuVisible)
        if (menuVisible) {
            if (!isDownloading) downloadPdf() else previewSliderViewModel.pdfIsDownloading.value = isDownloading
        }
    }

    override fun onPause() {
        previewPDFViewModel.cancelJobs()
        super.onPause()
    }

    override fun onDestroy() {
        pdfCore?.clear()
        super.onDestroy()
    }

    private fun showPdf(pdfCore: PdfCore) = lifecycleScope.launchWhenResumed {
        withContext(Dispatchers.Main) {
            downloadLayout.visibility = GONE
            pdfViewRecycler.visibility = VISIBLE
            pageNumberChip.visibility = VISIBLE
            previewPDFAdapter = PreviewPDFAdapter(pdfCore)
            pdfViewRecycler.adapter = previewPDFAdapter
            previewPDFAdapter?.itemCount?.let { updatePageNumber(totalPage = it) }

            pdfViewRecycler.addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                    super.onScrolled(recyclerView, dx, dy)
                    val currentPage =
                        (pdfViewRecycler.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition() + 1
                    previewPDFAdapter?.itemCount?.let { updatePageNumber(currentPage = currentPage, totalPage = it) }
                }
            })
        }
    }

    fun updatePageNumber(currentPage: Int = 1, totalPage: Int) {
        if (currentPage >= 1) {
            pageNumberChip.text = getString(R.string.previewPdfPages, currentPage, totalPage)
        }
    }

    private fun downloadPdf() {
        if (previewPDFAdapter == null || previewPDFAdapter?.itemCount == 0) {
            previewSliderViewModel.pdfIsDownloading.value = true
            isDownloading = true
            previewPDFViewModel.downloadPdfFile(requireContext(), previewViewModel.currentFile)
                .observe(viewLifecycleOwner, Observer { apiResponse ->
                    apiResponse.data?.let { pdfCore ->
                        this.pdfCore = pdfCore
                        showPdf(pdfCore)
                    } ?: run {
                        downloadProgress.visibility = GONE
                        previewDescription.setText(R.string.previewNoPreview)
                    }
                    previewSliderViewModel.pdfIsDownloading.value = false
                    isDownloading = false
                })
        }
    }

    class PreviewPDFViewModel(app: Application) : AndroidViewModel(app) {
        var pdfJob = Job()
        val downloadProgress = MutableLiveData<Int>()

        fun downloadPdfFile(context: Context, file: File): LiveData<ApiResponse<PdfCore>> {
            pdfJob.cancel()
            pdfJob = Job()
            return liveData(Dispatchers.IO + pdfJob) {
                val pdfCore = PreviewPDFUtils.convertPdfFileToPdfCore(context, file) {
                    viewModelScope.launch(Dispatchers.Main) {
                        downloadProgress.value = it
                    }
                }
                emit(pdfCore)
            }
        }

        fun cancelJobs() {
            pdfJob.cancel()
        }

        override fun onCleared() {
            cancelJobs()
            super.onCleared()
        }
    }
}