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

import android.app.Dialog
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.work.WorkInfo
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.*
import com.infomaniak.drive.data.services.DownloadWorker
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.ActionMultiSelectBottomSheetDialogArgs
import com.infomaniak.drive.ui.bottomSheetDialogs.ActionPicturesMultiSelectBottomSheetDialog
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.Utils.moveFileClicked
import com.infomaniak.lib.core.utils.toDp
import io.realm.RealmList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.cardview_picture.*
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.fragment_pictures.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.math.max
import kotlin.math.min

class PicturesFragment(
    private val multiSelectParent: MultiSelectParent? = null,
    private val onFinish: (() -> Unit)? = null
) : Fragment(), MultiSelectListener {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val picturesViewModel: PicturesViewModel by viewModels()
    private val picturesAdapter: PicturesAdapter by lazy {
        PicturesAdapter { file ->
            Utils.displayFile(mainViewModel, findNavController(), file, picturesAdapter.pictureList)
        }
    }

    private val selectFolderResultLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        it.whenResultIsOk { data ->
            with(data?.extras!!) {
                val folderName = getString(SelectFolderActivity.FOLDER_NAME_TAG).toString()
                val folderId = getInt(SelectFolderActivity.FOLDER_ID_TAG)
                val customArgs = getBundle(SelectFolderActivity.CUSTOM_ARGS_TAG)
                val bulkOperationType =
                    customArgs?.getParcelable<BulkOperationType>(SelectFolderActivity.BULK_OPERATION_CUSTOM_TAG)!!

                performBulkOperation(
                    type = bulkOperationType,
                    destinationFolder = File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId)
                )
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pictures, container, false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configPicturesLayoutManager()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        noPicturesLayout.setup(
            icon = R.drawable.ic_images,
            title = R.string.picturesNoFile,
            initialListView = picturesRecyclerView
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (picturesAdapter.multiSelectMode) {
                closeMultiSelect()
            } else {
                findNavController().popBackStack()
            }
        }

        picturesAdapter.apply {
            numberItemLoader = PicturesFragment.numberItemLoader
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

            openMultiSelectMode = { openMultiSelect() }
            updateMultiSelectMode = { onUpdateMultiSelect() }
        }

        if (multiSelectParent != null) enableMultiSelect()

        picturesRecyclerView.adapter = picturesAdapter
        configPicturesLayoutManager()

        mainViewModel.observeDownloadOffline(requireContext()).observe(viewLifecycleOwner) { workInfoList ->
            if (workInfoList.isEmpty()) return@observe

            val workInfo = workInfoList.firstOrNull { it.state == WorkInfo.State.RUNNING } ?: return@observe

            val fileId: Int = workInfo.progress.getInt(DownloadWorker.FILE_ID, 0)
            if (fileId == 0) return@observe

            val progress = workInfo.progress.getInt(DownloadWorker.PROGRESS, 100)

            if(progress == 100) picturesAdapter.updateOfflineStatus(fileId)
        }

        getPictures()
    }

    override fun onCloseMultiSelection() {
        closeMultiSelect()
    }

    override fun onMove() {
        val folderId = if (picturesAdapter.itemsSelected.count() == 1) {
            FileController.getParentFile(picturesAdapter.itemsSelected[0].id, UserDrive(), mainViewModel.realm)?.id
        } else {
            null
        }
        requireContext().moveFileClicked(folderId, selectFolderResultLauncher)
    }

    override fun onDelete() {
        performBulkOperation(BulkOperationType.TRASH)
    }

    override fun onMenu() {
        val fileIds = arrayListOf<Int>()
        var (onlyFolders, onlyFavorite, onlyOffline) = arrayOf(true, true, true)
        picturesAdapter.itemsSelected.forEach {
            fileIds.add(it.id)
            if (!it.isFolder()) onlyFolders = false
            if (!it.isFavorite) onlyFavorite = false
            if (!it.isOffline) onlyOffline = false
        }

        ActionPicturesMultiSelectBottomSheetDialog().apply {
            arguments = ActionMultiSelectBottomSheetDialogArgs(
                fileIds = picturesAdapter.getValidItemsSelected().map { it.id }.toIntArray(),
                onlyFolders = onlyFolders,
                onlyFavorite = onlyFavorite,
                onlyOffline = onlyOffline,
            ).toBundle()
        }.show(childFragmentManager, "ActionMultiSelectBottomSheetDialog")
    }

    fun onRefreshPictures() {
        if (isResumed) {
            picturesAdapter.clearPictures()
            getPictures()
        }
    }

    fun performBulkOperation(type: BulkOperationType, destinationFolder: File? = null, color: String? = null) {

        val selectedFiles = picturesAdapter.getValidItemsSelected()

        val fileCount = selectedFiles.size

        val sendActions: (dialog: Dialog?) -> Unit = sendActions(fileCount, selectedFiles, type, destinationFolder, color)

        if (type == BulkOperationType.TRASH) {
            Utils.createConfirmation(
                context = requireContext(),
                title = getString(R.string.modalMoveTrashTitle),
                message = resources.getQuantityString(R.plurals.modalMoveTrashDescription, fileCount, fileCount),
                isDeletion = true,
                onConfirmation = sendActions,
            )
        } else {
            sendActions(null)
        }
    }

    private fun sendActions(
        fileCount: Int,
        selectedFiles: List<File>,
        type: BulkOperationType,
        destinationFolder: File?,
        color: String?,
    ): (Dialog?) -> Unit = {

        // API doesn't support bulk operations for files originating from different parent folder so we repeat the action for each file
        val mediator = mainViewModel.createMultiSelectMediator()
        enableButtonMultiSelect(false)

        selectedFiles.reversed().forEach {
            val file = when {
                it.isManagedAndValidByRealm() -> it.realm.copyFromRealm(it, 0)
                it.isNotManagedByRealm() -> it
                else -> return@forEach
            }
            sendAction(file, type, mediator, destinationFolder, color)
        }

        mediator.observe(viewLifecycleOwner) { (success, total) ->
            if (total == fileCount) handleBulkActionResult(success, type, destinationFolder)
        }
    }

    private fun sendAction(
        file: File,
        type: BulkOperationType,
        mediator: MediatorLiveData<Pair<Int, Int>>,
        destinationFolder: File?,
        color: String?,
    ) {

        when (type) {
            BulkOperationType.TRASH -> {
                mediator.addSource(
                    mainViewModel.deleteFile(
                        file,
                        onSuccess = { fileID -> runBlocking(Dispatchers.Main) { picturesAdapter.deleteByFileId(fileID) } },
                    ),
                    mainViewModel.updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.MOVE -> {
                mediator.addSource(
                    mainViewModel.moveFile(file, destinationFolder!!),
                    mainViewModel.updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.COPY -> {
                val fileName = file.getFileName()
                mediator.addSource(
                    mainViewModel.duplicateFile(
                        file = file,
                        folderId = destinationFolder!!.id,
                        copyName = requireContext().getString(R.string.allDuplicateFileName, fileName, file.getFileExtension()),
                        onSuccess = { apiResponse ->
                            apiResponse.data?.let {
                                picturesAdapter.duplicatedList.add(0, it)
                            }
                        },
                    ),
                    mainViewModel.updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.COLOR_FOLDER -> {
                if (color != null && file.isAllowedToBeColored()) {
                    mediator.addSource(
                        mainViewModel.updateFolderColor(file, color),
                        mainViewModel.updateMultiSelectMediator(mediator),
                    )
                } else {
                    mediator.apply {
                        val success = value?.first ?: 0
                        val total = (value?.second ?: 0) + 1
                        value = success to total
                    }
                }
            }
            BulkOperationType.ADD_OFFLINE, BulkOperationType.REMOVE_OFFLINE -> addOrRemoveSelectedFilesToOffline(file, type)
            BulkOperationType.ADD_FAVORITES -> {
                mediator.addSource(
                    mainViewModel.addFileToFavorites(
                        file,
                        onSuccess = {
                            lifecycleScope.launch(Dispatchers.Main) {
                                picturesAdapter.notifyFileChanged(file.id) { it.isFavorite = true }
                            }
                        },
                    ),
                    mainViewModel.updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.REMOVE_FAVORITES -> {
                mediator.addSource(
                    mainViewModel.deleteFileFromFavorites(
                        file,
                        callback = {
                            lifecycleScope.launch(Dispatchers.Main) {
                                picturesAdapter.notifyFileChanged(file.id) { it.isFavorite = false }
                            }
                        }
                    ),
                    mainViewModel.updateMultiSelectMediator(mediator),
                )
            }
        }
    }

    private fun handleBulkActionResult(success: Int, type: BulkOperationType, destinationFolder: File?) {
        val title = if (success == 0) {
            getString(R.string.anErrorHasOccurred)
        } else {
            resources.getQuantityString(type.successMessage, success, success, destinationFolder?.name + "/")
        }
        requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab)
        closeMultiSelect()

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

    private fun addOrRemoveSelectedFilesToOffline(file: File, type: BulkOperationType) {
        if (!file.isFolder()) {
            val cacheFile = file.getCacheFile(requireContext())
            val offlineFile = file.getOfflineFile(requireContext())
            if (type == BulkOperationType.ADD_OFFLINE) {
                addSelectedFileToOffline(file, offlineFile, cacheFile)
            } else {
                removeSelectedFileFromOffline(file, offlineFile, cacheFile)
            }
            closeMultiSelect()
        }
    }

    private fun addSelectedFileToOffline(file: File, offlineFile: java.io.File?, cacheFile: java.io.File) {
        if (offlineFile != null && !file.isObsoleteOrNotIntact(cacheFile)) {
            Utils.moveCacheFileToOffline(file, cacheFile, offlineFile)
            runBlocking(Dispatchers.IO) { FileController.updateOfflineStatus(file.id, true) }

            picturesAdapter.updateFileProgressByFileId(file.id, 100) { _, currentFile ->
                currentFile.apply {
                    if (isNotManagedByRealm()) {
                        isOffline = true
                        currentProgress = 0
                    }
                }
            }
        } else {
            Utils.downloadAsOfflineFile(requireContext(), file)
            file.isOffline = true
        }
    }

    private fun removeSelectedFileFromOffline(file: File, offlineFile: java.io.File?, cacheFile: java.io.File) {
        lifecycleScope.launch {
            if (offlineFile != null) {
                mainViewModel.removeOfflineFile(file, offlineFile, cacheFile)
                file.isOffline = false
            }
        }
    }

    private fun enableMultiSelect() {
        picturesAdapter.enabledMultiSelectMode = true
    }

    private fun onUpdateMultiSelect(selectedNumber: Int? = null) {
        val fileSelectedNumber = selectedNumber ?: picturesAdapter.getValidItemsSelected().size
        when (fileSelectedNumber) {
            0, 1 -> {
                val isEnabled = fileSelectedNumber == 1
                enableButtonMultiSelect(isEnabled)
            }
        }
        multiSelectParent?.setTitleMultiSelect(
            resources.getQuantityString(
                R.plurals.fileListMultiSelectedTitle,
                fileSelectedNumber,
                fileSelectedNumber
            )
        )
    }

    private fun enableButtonMultiSelect(isEnabled: Boolean) {
        if (isEnabled) multiSelectParent?.enableMultiSelectActionButtons()
        else multiSelectParent?.disableMultiSelectActionButtons()
    }

    private fun configPicturesLayoutManager() {
        val numPicturesColumns = getNumPicturesColumns()
        val gridLayoutManager = GridLayoutManager(requireContext(), numPicturesColumns)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when {
                    picturesAdapter.itemList.getOrNull(position) is String -> numPicturesColumns
                    else -> 1
                }
            }
        }
        picturesRecyclerView.layoutManager = gridLayoutManager
    }

    private fun getNumPicturesColumns(minColumns: Int = 2, maxColumns: Int = 5, expectedItemSize: Int = 300): Int {
        val screenWidth = requireActivity().getScreenSizeInDp().x
        return min(max(minColumns, screenWidth / expectedItemSize.toDp()), maxColumns)
    }

    private fun openMultiSelect() {
        picturesAdapter.multiSelectMode = true
        picturesAdapter.notifyItemRangeChanged(0, picturesAdapter.itemCount)
        multiSelectParent?.openMultiSelectBar()
        multiSelectParent?.disableSwipeRefresh()
    }

    fun duplicateFiles() {
        val intent = Intent(requireContext(), SelectFolderActivity::class.java).apply {
            putExtra(SelectFolderActivity.USER_ID_TAG, AccountUtils.currentUserId)
            putExtra(SelectFolderActivity.USER_DRIVE_ID_TAG, AccountUtils.currentDriveId)
            putExtra(
                SelectFolderActivity.CUSTOM_ARGS_TAG,
                bundleOf(SelectFolderActivity.BULK_OPERATION_CUSTOM_TAG to BulkOperationType.COPY),
            )
        }
        selectFolderResultLauncher.launch(intent)
    }

    fun closeMultiSelect() {
        picturesAdapter.apply {
            itemsSelected = RealmList()
            multiSelectMode = false
            notifyItemRangeChanged(0, itemCount)
        }

        multiSelectParent?.closeMultiSelectBar()
        multiSelectParent?.enableSwipeRefresh()
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
                        showRefreshButton = true
                    )
                }
                onFinish?.invoke()
            }
        }
    }

    companion object {
        private const val numberItemLoader = 12
    }
}

interface MultiSelectListener {
    fun onCloseMultiSelection()
    fun onMove()
    fun onDelete()
    fun onMenu()
}

interface MultiSelectParent {
    fun openMultiSelectBar()
    fun closeMultiSelectBar()
    fun enableMultiSelectActionButtons()
    fun disableMultiSelectActionButtons()
    fun setTitleMultiSelect(title: String)
    fun disableSwipeRefresh()
    fun enableSwipeRefresh()
}