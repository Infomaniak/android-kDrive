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
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.databinding.FragmentMenuPicturesBinding
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectFragment
import com.infomaniak.drive.ui.fileList.multiSelect.PicturesMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.Utils.createRefreshTimer
import com.infomaniak.lib.core.utils.setPagination
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cardview_picture.*
import kotlinx.android.synthetic.main.fragment_pictures.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class PicturesFragment : MultiSelectFragment(MATOMO_CATEGORY) {

    private val picturesViewModel: PicturesViewModel by viewModels()
    private lateinit var picturesAdapter: PicturesAdapter

    private var paginationListener: RecyclerView.OnScrollListener? = null
    private var isDownloadingPictures = false

    var menuPicturesBinding: FragmentMenuPicturesBinding? = null

    private val refreshTimer: CountDownTimer by lazy {
        createRefreshTimer { menuPicturesBinding?.swipeRefreshLayout?.isRefreshing = true }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pictures, container, false)
    }

    override fun initMultiSelectLayout(): MultiSelectLayoutBinding? = menuPicturesBinding?.multiSelectLayout
    override fun initMultiSelectToolbar(): CollapsingToolbarLayout? = menuPicturesBinding?.collapsingToolbarLayout
    override fun initSwipeRefreshLayout(): SwipeRefreshLayout? = menuPicturesBinding?.swipeRefreshLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupPagination()

        val isCurrentlyInGallery = menuPicturesBinding != null

        noPicturesLayout.setup(
            icon = R.drawable.ic_images,
            title = R.string.picturesNoFile,
            initialListView = picturesRecyclerView,
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner, isCurrentlyInGallery) {
            if (multiSelectManager.isMultiSelectOn) closeMultiSelect() else findNavController().popBackStack()
        }

        multiSelectManager.apply {
            openMultiSelect = { openMultiSelect() }
            updateMultiSelect = { onItemSelected() }
        }

        val isPicturesAdapterInitialized = ::picturesAdapter.isInitialized
        if (!isPicturesAdapterInitialized) {
            picturesAdapter = PicturesAdapter(
                multiSelectManager = multiSelectManager,
                onFileClicked = { file ->
                    Utils.displayFile(mainViewModel, findNavController(), file, picturesAdapter.pictureList)
                },
            ).apply {
                numberItemLoader = NUMBER_ITEMS_LOADER
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

                adapter = this
            }
        }

        if (isCurrentlyInGallery) multiSelectManager.isMultiSelectAuthorized = true

        picturesRecyclerView.adapter = picturesAdapter
        configPicturesLayoutManager()

        mainViewModel.observeDownloadOffline(requireContext()).observe(viewLifecycleOwner) { workInfoList ->
            if (workInfoList.isEmpty()) return@observe

            val workInfo = workInfoList.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@observe

            val fileId: Int = workInfo.progress.getInt(DownloadWorker.FILE_ID, 0)
            if (fileId == 0) return@observe

            val progress = workInfo.progress.getInt(DownloadWorker.PROGRESS, 100)
            if (progress == 100) picturesAdapter.updateOfflineStatus(fileId)
        }

        if (!isPicturesAdapterInitialized) {
            if (isCurrentlyInGallery) refreshTimer.start()
            loadMorePictures(AccountUtils.currentDriveId, true)
        }
    }

    private fun setupPagination() {
        picturesRecyclerView.apply {
            paginationListener?.let(::removeOnScrollListener)
            paginationListener = setPagination(
                whenLoadMoreIsPossible = {
                    if (!picturesAdapter.isComplete && !isDownloadingPictures) {
                        picturesViewModel.lastPicturesPage++
                        picturesViewModel.lastPicturesLastPage++

                        loadMorePictures(AccountUtils.currentDriveId)
                    }
                },
                triggerOffset = 100
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configPicturesLayoutManager()
    }

    private fun configPicturesLayoutManager() {


        val numPicturesColumns = requireActivity().getAdjustedColumnNumber(150, minColumns = 3, maxColumns = 15)

        val gridLayoutManager = GridLayoutManager(requireContext(), numPicturesColumns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when {
                        position == 0 || picturesAdapter.itemList.getOrNull(position) is String -> numPicturesColumns
                        else -> 1
                    }
                }
            }
        }

        picturesRecyclerView.layoutManager = gridLayoutManager
    }

    private fun loadMorePictures(driveId: Int, forceDownload: Boolean = false) {
        picturesAdapter.apply {
            if (forceDownload) {
                picturesViewModel.apply {
                    lastPicturesPage = 1
                    lastPicturesLastPage = 1
                }
                clean()
            }

            val ignoreCloud = mainViewModel.isInternetAvailable.value == false
            showLoading()
            isComplete = false
            isDownloadingPictures = true
            picturesViewModel.getLastPictures(driveId, ignoreCloud).observe(viewLifecycleOwner) {
                it?.let { (pictures, isComplete) ->
                    stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                    val pictureList = formatList(requireContext(), pictures)
                    picturesRecyclerView.post { addAll(pictureList) }
                    this.isComplete = isComplete
                    noPicturesLayout.toggleVisibility(pictureList.isEmpty())
                } ?: run {
                    isComplete = true
                    noPicturesLayout.toggleVisibility(
                        noNetwork = ignoreCloud,
                        isVisible = pictureList.isEmpty(),
                        showRefreshButton = true,
                    )
                }

                onDownloadFinished()

                isDownloadingPictures = false
            }
        }
    }

    private fun onDownloadFinished() {
        menuPicturesBinding?.let { binding ->
            refreshTimer.cancel()
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    fun onRefreshPictures() {
        if (isResumed) {
            picturesAdapter.clearPictures()
            loadMorePictures(AccountUtils.currentDriveId, true)
        }
    }

    override fun getAllSelectedFilesCount(): Int? = null

    fun onMoveButtonClicked() {
        // TODO: Use `parentId` when https://github.com/Infomaniak/android-kDrive/issues/532 is merged
        val folderId = if (multiSelectManager.selectedItems.count() == 1) {
            FileController.getParentFileProxy(multiSelectManager.selectedItems[0].id, mainViewModel.realm)?.id
        } else {
            null
        }

        moveFiles(folderId)
    }

    fun onMenuButtonClicked() {
        val (fileIds, onlyFolders, onlyFavorite, onlyOffline, isAllSelected) = multiSelectManager.getMenuNavArgs()
        PicturesMultiSelectActionsBottomSheetDialog().apply {
            arguments = MultiSelectActionsBottomSheetDialogArgs(
                fileIds = fileIds,
                onlyFolders = onlyFolders,
                onlyFavorite = onlyFavorite,
                onlyOffline = onlyOffline,
                isAllSelected = isAllSelected,
                areAllFromTheSameFolder = false,
            ).toBundle()
        }.show(childFragmentManager, "ActionPicturesMultiSelectBottomSheetDialog")
    }

    override fun performBulkOperation(
        type: BulkOperationType,
        areAllFromTheSameFolder: Boolean,
        allSelectedFilesCount: Int?,
        destinationFolder: File?,
        color: String?,
    ) {
        // API doesn't support bulk operations for files originating from
        // different parent folders, so we repeat the action for each file.
        // Hence the `areAllFromTheSameFolder` set at false.
        super.performBulkOperation(type, false, allSelectedFilesCount, destinationFolder, color)
    }

    override fun onIndividualActionSuccess(type: BulkOperationType, data: Any) {
        when (type) {
            BulkOperationType.TRASH -> {
                runBlocking(Dispatchers.Main) { picturesAdapter.deleteByFileId(data as Int) }
            }
            BulkOperationType.COPY -> {
                picturesAdapter.duplicatedList.add(0, data as File)
            }
            BulkOperationType.ADD_FAVORITES -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    picturesAdapter.notifyFileChanged(data as Int) { it.isFavorite = true }
                }
            }
            BulkOperationType.REMOVE_FAVORITES -> {
                lifecycleScope.launch(Dispatchers.Main) {
                    picturesAdapter.notifyFileChanged(data as Int) { it.isFavorite = false }
                }
            }
            BulkOperationType.ADD_OFFLINE, BulkOperationType.REMOVE_OFFLINE,
            BulkOperationType.MOVE,
            BulkOperationType.COLOR_FOLDER,
            BulkOperationType.RESTORE_IN, BulkOperationType.RESTORE_TO_ORIGIN, BulkOperationType.DELETE_PERMANENTLY -> {
                // No-op
            }
        }
    }

    override fun onAllIndividualActionsFinished(type: BulkOperationType) {

        if (type == BulkOperationType.COPY) {
            val oldTotal = picturesAdapter.itemList.size
            val oldFirstItem = picturesAdapter.itemList.firstOrNull()

            picturesAdapter.addDuplicatedImages(requireContext())
            val newTotal = picturesAdapter.itemList.count()
            val newFirstItem = picturesAdapter.itemList.firstOrNull()

            val positionStart = if (oldFirstItem != newFirstItem) 0 else 1
            picturesAdapter.notifyItemRangeInserted(positionStart, newTotal - oldTotal)
        }
    }

    override fun updateFileProgressByFileId(fileId: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)?) {
        picturesAdapter.updateFileProgressByFileId(fileId, progress, onComplete)
    }

    companion object {
        const val TAG = "PicturesFragment"
        const val MATOMO_CATEGORY = "picturesFileAction"
        private const val NUMBER_ITEMS_LOADER = 13
    }
}
