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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts.*
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.databinding.FragmentMenuPicturesBinding
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectActionsBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectFragment
import com.infomaniak.drive.ui.fileList.multiSelect.PicturesMultiSelectActionsBottomSheetDialog
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.toDp
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cardview_picture.*
import kotlinx.android.synthetic.main.fragment_pictures.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min

class PicturesFragment(private val onFinish: (() -> Unit)? = null) : MultiSelectFragment(MATOMO_CATEGORY) {

    private val picturesViewModel: PicturesViewModel by viewModels()
    private lateinit var picturesAdapter: PicturesAdapter

    var menuPicturesBinding: FragmentMenuPicturesBinding? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        multiSelectLayout = menuPicturesBinding?.multiSelectLayout
        return inflater.inflate(R.layout.fragment_pictures, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        noPicturesLayout.setup(
            icon = R.drawable.ic_images,
            title = R.string.picturesNoFile,
            initialListView = picturesRecyclerView,
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (multiSelectManager.multiSelectMode) closeMultiSelect() else findNavController().popBackStack()
        }

        multiSelectManager.apply {
            openMultiSelectMode = {
                openMultiSelect()
                menuPicturesBinding?.swipeRefreshLayout?.isEnabled = false
            }
            updateMultiSelectMode = { onItemSelected() }
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

        if (menuPicturesBinding != null) multiSelectManager.enabledMultiSelectMode = true

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

        getPictures()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configPicturesLayoutManager()
    }

    private fun configPicturesLayoutManager() {

        fun getNumPicturesColumns(): Int {
            val minColumns = 2
            val maxColumns = 5
            val expectedItemSize = 300
            val screenWidth = requireActivity().getScreenSizeInDp().x
            return min(max(minColumns, screenWidth / expectedItemSize.toDp()), maxColumns)
        }

        val numPicturesColumns = getNumPicturesColumns()

        val gridLayoutManager = GridLayoutManager(requireContext(), numPicturesColumns).apply {
            spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                override fun getSpanSize(position: Int): Int {
                    return when {
                        picturesAdapter.itemList.getOrNull(position) is String -> numPicturesColumns
                        else -> 1
                    }
                }
            }
        }

        picturesRecyclerView.layoutManager = gridLayoutManager
    }

    private fun getPictures() {
        picturesAdapter.apply {
            val ignoreCloud = mainViewModel.isInternetAvailable.value == false
            showLoading()
            isComplete = false
            picturesViewModel.getAllPicturesFiles(AccountUtils.currentDriveId, ignoreCloud).observe(viewLifecycleOwner) {
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
                onFinish?.invoke()
            }
        }
    }

    fun onRefreshPictures() {
        if (isResumed) {
            picturesAdapter.clearPictures()
            getPictures()
        }
    }

    override fun closeMultiSelect() {
        super.closeMultiSelect()
        menuPicturesBinding?.swipeRefreshLayout?.isEnabled = true
    }

    override fun getSelectedFilesCount(): Int? = null

    fun onMoveButtonClicked() {
        // TODO use "parentId" when https://github.com/Infomaniak/android-kDrive/issues/532 is merged
        val folderId = if (multiSelectManager.selectedItems.count() == 1) {
            FileController.getParentFileProxy(multiSelectManager.selectedItems[0].id, mainViewModel.realm)?.id
        } else {
            null
        }

        moveFiles(folderId)
    }

    fun onMenuButtonClicked() {
        val (fileIds, onlyFolders, onlyFavorite, onlyOffline) = multiSelectManager.getMenuNavArgs()
        PicturesMultiSelectActionsBottomSheetDialog().apply {
            arguments = MultiSelectActionsBottomSheetDialogArgs(
                fileIds = fileIds,
                onlyFolders = onlyFolders,
                onlyFavorite = onlyFavorite,
                onlyOffline = onlyOffline,
                isFromGallery = true,
            ).toBundle()
        }.show(childFragmentManager, "ActionPicturesMultiSelectBottomSheetDialog")
    }

    override fun performBulkOperation(
        type: BulkOperationType,
        areAllFromTheSameFolder: Boolean,
        selectedFilesCount: Int?,
        destinationFolder: File?,
        color: String?,
    ) {
        // API doesn't support bulk operations for files originating from
        // different parent folders, so we repeat the action for each file.
        // Hence the `areAllFromTheSameFolder` set at false.
        super.performBulkOperation(type, false, selectedFilesCount, destinationFolder, color)
    }

    override fun onIndividualActionSuccess(type: BulkOperationType, data: Any) {
        when (type) {
            BulkOperationType.TRASH -> {
                runBlocking(Dispatchers.Main) { picturesAdapter.deleteByFileId(data as Int) }
            }
            BulkOperationType.COPY -> {
                picturesAdapter.duplicatedList.add(0, data as File)
            }
            BulkOperationType.ADD_OFFLINE, BulkOperationType.REMOVE_OFFLINE -> {
                menuPicturesBinding?.swipeRefreshLayout?.isEnabled = true
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
            BulkOperationType.MOVE, BulkOperationType.COLOR_FOLDER -> {
                // No-op
            }
        }
    }

    override fun onAllIndividualActionsFinished(type: BulkOperationType) {
        menuPicturesBinding?.swipeRefreshLayout?.isEnabled = true

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
        private const val NUMBER_ITEMS_LOADER = 12
        const val MATOMO_CATEGORY = "pictures"
    }
}
