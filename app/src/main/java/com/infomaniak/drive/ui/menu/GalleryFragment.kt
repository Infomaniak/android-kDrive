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
package com.infomaniak.drive.ui.menu

import android.content.res.Configuration
import android.os.Bundle
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.work.WorkInfo
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.databinding.FragmentGalleryBinding
import com.infomaniak.drive.databinding.FragmentMenuGalleryBinding
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectFragment
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.getAdjustedColumnNumber
import com.infomaniak.drive.views.NoItemsLayoutView
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer
import com.infomaniak.lib.core.utils.setPagination
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class GalleryFragment : MultiSelectFragment(MATOMO_CATEGORY), NoItemsLayoutView.INoItemsLayoutView {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    override val noItemsIcon = R.drawable.ic_images
    override val noItemsTitle = R.string.picturesNoFile
    override val noItemsInitialListView: View by lazy { binding.galleryFastScroller }

    private val galleryViewModel: GalleryViewModel by viewModels()
    private lateinit var galleryAdapter: GalleryAdapter

    private var paginationListener: RecyclerView.OnScrollListener? = null
    private var isDownloadingGallery = false

    var menuGalleryBinding: FragmentMenuGalleryBinding? = null

    private val refreshTimer: CountDownTimer by lazy {
        createRefreshTimer { menuGalleryBinding?.swipeRefreshLayout?.isRefreshing = true }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentGalleryBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun initMultiSelectLayout(): MultiSelectLayoutBinding? = menuGalleryBinding?.multiSelectLayout
    override fun initMultiSelectToolbar(): CollapsingToolbarLayout? = menuGalleryBinding?.collapsingToolbarLayout
    override fun initSwipeRefreshLayout(): SwipeRefreshLayout? = menuGalleryBinding?.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPagination()

        val isCurrentlyInGallery = menuGalleryBinding != null

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, isCurrentlyInGallery) {
            if (multiSelectManager.isMultiSelectOn) closeMultiSelect() else findNavController().popBackStack()
        }

        multiSelectManager.apply {
            openMultiSelect = { openMultiSelect() }
            updateMultiSelect = { onItemSelected() }
        }

        val isGalleryAdapterInitialized = ::galleryAdapter.isInitialized
        if (!isGalleryAdapterInitialized) {
            galleryAdapter = GalleryAdapter(
                multiSelectManager = multiSelectManager,
                onFileClicked = { file ->
                    Utils.displayFile(mainViewModel, findNavController(), file, galleryAdapter.galleryList)
                },
            ).apply {
                numberItemLoader = NUMBER_ITEMS_LOADER
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

                adapter = this
            }
        }

        if (isCurrentlyInGallery) multiSelectManager.isMultiSelectAuthorized = true

        with(binding) {
            galleryRecyclerView.adapter = galleryAdapter
            configGalleryLayoutManager()

            noGalleryLayout.iNoItemsLayoutView = this@GalleryFragment
        }

        mainViewModel.observeDownloadOffline(requireContext()).observe(viewLifecycleOwner) { workInfoList ->
            if (workInfoList.isEmpty()) return@observe

            val workInfo = workInfoList.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@observe

            val fileId: Int = workInfo.progress.getInt(DownloadWorker.FILE_ID, 0)
            if (fileId == 0) return@observe

            val progress = workInfo.progress.getInt(DownloadWorker.PROGRESS, 100)
            if (progress == 100) galleryAdapter.updateOfflineStatus(fileId)
        }

        if (!isGalleryAdapterInitialized) {
            if (isCurrentlyInGallery) refreshTimer.start()
            loadMoreGallery(AccountUtils.currentDriveId, true)
        }

        observeApiResultPagination()
    }

    private fun observeApiResultPagination() = with(galleryAdapter) {
        galleryViewModel.galleryApiResult.observe(viewLifecycleOwner) {
            it?.let { (galleryFiles, isComplete) ->
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                val galleryList = formatList(galleryFiles)
                binding.galleryRecyclerView.post { addAll(galleryList) }
                this.isComplete = isComplete
                binding.noGalleryLayout.toggleVisibility(galleryList.isEmpty())
            } ?: run {
                isComplete = true
                binding.noGalleryLayout.toggleVisibility(
                    noNetwork = mainViewModel.isInternetAvailable.value == false,
                    isVisible = galleryList.isEmpty(),
                    showRefreshButton = true,
                )
            }

            onDownloadFinished()

            isDownloadingGallery = false
        }
    }

    private fun setupPagination() {
        binding.galleryRecyclerView.apply {
            paginationListener?.let(::removeOnScrollListener)
            paginationListener = setPagination(
                whenLoadMoreIsPossible = {
                    if (!galleryAdapter.isComplete && !isDownloadingGallery) {
                        galleryViewModel.lastGalleryPage++
                        galleryViewModel.lastGalleryLastPage++

                        loadMoreGallery(AccountUtils.currentDriveId)
                    }
                },
                triggerOffset = 100
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configGalleryLayoutManager()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun configGalleryLayoutManager() {
        val numGalleryColumns = requireActivity().getAdjustedColumnNumber(150, minColumns = 3, maxColumns = 15)

        val gridLayoutManager = GridLayoutManager(requireContext(), numGalleryColumns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when {
                        position == 0 || galleryAdapter.itemList.getOrNull(position) is String -> numGalleryColumns
                        else -> 1
                    }
                }
            }
        }

        binding.galleryRecyclerView.layoutManager = gridLayoutManager
    }

    private fun loadMoreGallery(driveId: Int, forceDownload: Boolean = false) {
        galleryAdapter.apply {
            if (forceDownload) {
                galleryViewModel.apply {
                    lastGalleryPage = 1
                    lastGalleryLastPage = 1
                }
                clean()
            }

            showLoading()
            isComplete = false
            isDownloadingGallery = true

            val ignoreCloud = mainViewModel.isInternetAvailable.value == false
            galleryViewModel.loadMoreGallery.value = driveId to ignoreCloud
        }
    }

    private fun onDownloadFinished() {
        menuGalleryBinding?.let { binding ->
            refreshTimer.cancel()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    fun onRefreshGallery() {
        if (isResumed) {
            galleryAdapter.clearGallery()
            loadMoreGallery(AccountUtils.currentDriveId, true)
        }
    }

    override fun getAllSelectedFilesCount(): Int? = null

    fun onMoveButtonClicked() = with(multiSelectManager.selectedItems) {
        moveFiles(disabledFolderId = if (count() == 1) first()?.parentId else null)
    }

    override fun performBulkOperation(
        type: BulkOperationType,
        folderId: Int?,
        areAllFromTheSameFolder: Boolean,
        allSelectedFilesCount: Int?,
        destinationFolder: File?,
        color: String?,
    ) {
        // API doesn't support bulk operations for files originating from
        // different parent folders, so we repeat the action for each file.
        // Hence the `areAllFromTheSameFolder` set at false.
        super.performBulkOperation(type, folderId, false, allSelectedFilesCount, destinationFolder, color)
    }

    override fun onIndividualActionSuccess(type: BulkOperationType, data: Any) {
        when (type) {
            BulkOperationType.TRASH -> {
                runBlocking(Dispatchers.Main) { galleryAdapter.deleteByFileId(data as Int) }
            }
            BulkOperationType.COPY -> {
                galleryAdapter.duplicatedList.add(0, data as File)
            }
            BulkOperationType.ADD_FAVORITES -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    galleryAdapter.notifyFileChanged(data as Int) { it.isFavorite = true }
                }
            }
            BulkOperationType.REMOVE_FAVORITES -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    galleryAdapter.notifyFileChanged(data as Int) { it.isFavorite = false }
                }
            }
            BulkOperationType.ADD_OFFLINE, BulkOperationType.REMOVE_OFFLINE,
            BulkOperationType.MOVE,
            BulkOperationType.MANAGE_CATEGORIES,
            BulkOperationType.COLOR_FOLDER,
            BulkOperationType.RESTORE_IN, BulkOperationType.RESTORE_TO_ORIGIN, BulkOperationType.DELETE_PERMANENTLY -> Unit
        }
    }

    override fun onAllIndividualActionsFinished(type: BulkOperationType) {

        if (type == BulkOperationType.COPY) {
            val oldTotal = galleryAdapter.itemList.size
            val oldFirstItem = galleryAdapter.itemList.firstOrNull()

            galleryAdapter.addDuplicatedImages()
            val newTotal = galleryAdapter.itemList.count()
            val newFirstItem = galleryAdapter.itemList.firstOrNull()

            val positionStart = if (oldFirstItem != newFirstItem) 0 else 1
            galleryAdapter.notifyItemRangeInserted(positionStart, newTotal - oldTotal)
        }
    }

    override fun updateFileProgressByFileId(fileId: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)?) {
        galleryAdapter.updateFileProgressByFileId(fileId, progress, onComplete)
    }

    fun setScrollbarTrackOffset(offset: Int) {
        _binding?.galleryFastScroller?.trackMarginEnd = offset
    }

    companion object {
        const val TAG = "GalleryFragment"
        const val MATOMO_CATEGORY = "picturesFileAction"
        private const val NUMBER_ITEMS_LOADER = 13
    }
}
