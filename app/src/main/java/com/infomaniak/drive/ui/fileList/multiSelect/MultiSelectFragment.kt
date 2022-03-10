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
package com.infomaniak.drive.ui.fileList.multiSelect

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.BulkOperation
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListFragmentDirections
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.Companion.BULK_OPERATION_CUSTOM_TAG
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.Companion.CUSTOM_ARGS_TAG
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.Companion.FOLDER_ID_TAG
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.Companion.FOLDER_NAME_TAG
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.Companion.USER_DRIVE_ID_TAG
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.Companion.USER_ID_TAG
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectManager.MultiSelectResult
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.BulkOperationsUtils.launchBulkOperationWorker
import com.infomaniak.drive.utils.MatomoUtils.trackEvent
import com.infomaniak.drive.utils.Utils.moveFileClicked
import io.realm.RealmList
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

abstract class MultiSelectFragment(private val matomoCategory: String) : Fragment(), MultiSelectResult {

    protected val mainViewModel: MainViewModel by activityViewModels()
    protected val multiSelectManager = MultiSelectManager()
    protected var adapter: RecyclerView.Adapter<*>? = null
    protected var multiSelectLayout: MultiSelectLayoutBinding? = null
    private var multiSelectToolbar: CollapsingToolbarLayout? = null

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            with(data?.extras!!) {
                val folderId = getInt(FOLDER_ID_TAG)
                val folderName = getString(FOLDER_NAME_TAG).toString()
                val customArgs = getBundle(CUSTOM_ARGS_TAG)
                val bulkOperationType = customArgs?.getParcelable<BulkOperationType>(BULK_OPERATION_CUSTOM_TAG)!!

                performBulkOperation(
                    type = bulkOperationType,
                    allSelectedFileCount = getAllSelectedFileCount(),
                    destinationFolder = File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId),
                )
            }
        }
    }

    abstract fun initMultiSelectLayout(): MultiSelectLayoutBinding?
    abstract fun initMultiSelectToolbar(): CollapsingToolbarLayout?
    abstract fun getAllSelectedFileCount(): Int?

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        multiSelectLayout = initMultiSelectLayout()
        multiSelectToolbar = initMultiSelectToolbar()
        super.onViewCreated(view, savedInstanceState)
    }

    fun openMultiSelect() {
        multiSelectManager.isMultiSelectOpened = true

        adapter?.apply { notifyItemRangeChanged(0, itemCount) }

        multiSelectToolbar?.isGone = true
        multiSelectLayout?.root?.isVisible = true
    }

    fun onItemSelected(selectedNumber: Int? = null) {
        val fileSelectedNumber = selectedNumber ?: multiSelectManager.getValidSelectedItems().size
        if (fileSelectedNumber in 0..1) enableMultiSelectButtons(fileSelectedNumber == 1)
        multiSelectLayout?.titleMultiSelect?.text = resources.getQuantityString(
            R.plurals.fileListMultiSelectedTitle, fileSelectedNumber, fileSelectedNumber
        )
    }

    fun enableMultiSelectButtons(isEnabled: Boolean) {
        multiSelectLayout?.apply {
            deleteButtonMultiSelect.isEnabled = isEnabled
            moveButtonMultiSelect.isEnabled = isEnabled
            menuButtonMultiSelect.isEnabled = isEnabled
        }
    }

    open fun closeMultiSelect() {
        multiSelectManager.apply {
            selectedItems = RealmList()
            areAllSelected = false
            isMultiSelectOpened = false
        }

        adapter?.apply { notifyItemRangeChanged(0, itemCount) }

        multiSelectToolbar?.isVisible = true
        multiSelectLayout?.root?.isGone = true
    }

    fun moveFiles(folderId: Int?) {
        requireContext().moveFileClicked(folderId, selectFolderResultLauncher)
    }

    fun deleteFiles(allSelectedFileCount: Int? = null) {
        performBulkOperation(type = BulkOperationType.TRASH, allSelectedFileCount = allSelectedFileCount)
    }

    fun colorFolders() {
        if (AccountUtils.getCurrentDrive()?.pack == Drive.DrivePack.FREE.value) {
            safeNavigate(R.id.colorFolderUpgradeBottomSheetDialog)
        } else {
            safeNavigate(FileListFragmentDirections.actionFileListToColorFolder(null))
        }
    }

    fun duplicateFiles() {
        val intent = Intent(requireContext(), SelectFolderActivity::class.java).apply {
            putExtra(USER_ID_TAG, AccountUtils.currentUserId)
            putExtra(USER_DRIVE_ID_TAG, AccountUtils.currentDriveId)
            putExtra(CUSTOM_ARGS_TAG, bundleOf(BULK_OPERATION_CUSTOM_TAG to BulkOperationType.COPY))
        }
        selectFolderResultLauncher.launch(intent)
    }

    open fun performBulkOperation(
        type: BulkOperationType,
        areAllFromTheSameFolder: Boolean = true,
        allSelectedFileCount: Int? = null,
        destinationFolder: File? = null,
        color: String? = null,
    ) = with(requireContext()) {

        val selectedFiles = multiSelectManager.getValidSelectedItems(type)
        val fileCount = allSelectedFileCount ?: selectedFiles.size

        applicationContext?.trackBulkActionEvent(matomoCategory, type, fileCount)

        val sendActions: (dialog: Dialog?) -> Unit = sendActions(
            type, areAllFromTheSameFolder, fileCount, selectedFiles, destinationFolder, color
        )

        if (type == BulkOperationType.TRASH) {
            Utils.createConfirmation(
                context = this,
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
        type: BulkOperationType,
        areAllFromTheSameFolder: Boolean,
        fileCount: Int,
        selectedFiles: List<File>,
        destinationFolder: File?,
        color: String?,
    ): (Dialog?) -> Unit = {

        val canBulkAllSelectedFiles = multiSelectManager.areAllSelected && fileCount > BulkOperationsUtils.MIN_SELECTED
        val hasEnoughSelectedFilesToBulk = selectedFiles.size > BulkOperationsUtils.MIN_SELECTED

        if (areAllFromTheSameFolder && (canBulkAllSelectedFiles || hasEnoughSelectedFilesToBulk)) {
            sendBulkAction(
                fileCount, BulkOperation(
                    action = type,
                    fileIds = if (multiSelectManager.areAllSelected) null else selectedFiles.map { it.id },
                    parent = multiSelectManager.currentFolder!!,
                    destinationFolderId = destinationFolder?.id,
                )
            )

        } else {
            val mediator = mainViewModel.createMultiSelectMediator()
            enableMultiSelectButtons(false)
            sendAllIndividualActions(selectedFiles, type, mediator, destinationFolder, color)
            observeMediator(mediator, fileCount, type, destinationFolder)
        }
    }

    private fun sendBulkAction(fileCount: Int = 0, bulkOperation: BulkOperation) {
        MqttClientWrapper.start {
            multiSelectManager.performCancellableBulkOperation(bulkOperation).observe(viewLifecycleOwner) { apiResponse ->
                if (apiResponse.isSuccess()) {
                    apiResponse.data?.let { cancellableAction ->
                        requireContext().launchBulkOperationWorker(
                            BulkOperationsUtils.generateWorkerData(cancellableAction.cancelId, fileCount, bulkOperation.action)
                        )
                    }
                } else {
                    requireActivity().showSnackbar(apiResponse.translateError())
                }
                closeMultiSelect()
            }
        }
    }

    private fun sendAllIndividualActions(
        selectedFiles: List<File>,
        type: BulkOperationType,
        mediator: MediatorLiveData<Pair<Int, Int>>,
        destinationFolder: File?,
        color: String?,
    ) {
        selectedFiles.reversed().forEach {
            val file = when {
                it.isManagedAndValidByRealm() -> it.realm.copyFromRealm(it, 0)
                it.isNotManagedByRealm() -> it
                else -> return@forEach
            }
            sendIndividualAction(file, type, mediator, destinationFolder, color)
        }
    }

    private fun sendIndividualAction(
        file: File,
        type: BulkOperationType,
        mediator: MediatorLiveData<Pair<Int, Int>>,
        destinationFolder: File?,
        color: String?,
    ) = with(mainViewModel) {
        when (type) {
            BulkOperationType.TRASH -> {
                mediator.addSource(
                    deleteFile(file, onSuccess = { onIndividualActionSuccess(BulkOperationType.TRASH, it) }),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.MOVE -> {
                mediator.addSource(
                    moveFile(
                        file = file,
                        newParent = destinationFolder!!,
                        onSuccess = { onIndividualActionSuccess(BulkOperationType.MOVE, it) },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.COPY -> {
                val fileName = file.getFileName()
                mediator.addSource(
                    duplicateFile(
                        file = file,
                        folderId = destinationFolder!!.id,
                        copyName = getString(R.string.allDuplicateFileName, fileName, file.getFileExtension()),
                        onSuccess = { it.data?.let { file -> onIndividualActionSuccess(BulkOperationType.COPY, file) } },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.COLOR_FOLDER -> {
                if (color != null && file.isAllowedToBeColored()) {
                    mediator.addSource(
                        updateFolderColor(file, color),
                        updateMultiSelectMediator(mediator),
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
                    addFileToFavorites(
                        file = file,
                        onSuccess = { onIndividualActionSuccess(BulkOperationType.ADD_FAVORITES, file.id) }),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.REMOVE_FAVORITES -> {
                mediator.addSource(
                    deleteFileFromFavorites(
                        file = file,
                        onSuccess = { onIndividualActionSuccess(BulkOperationType.REMOVE_FAVORITES, file.id) },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
        }
    }

    private fun observeMediator(
        mediator: MediatorLiveData<Pair<Int, Int>>,
        fileCount: Int,
        type: BulkOperationType,
        destinationFolder: File?,
    ) {
        mediator.observe(viewLifecycleOwner) { (success, total) ->
            if (total == fileCount) handleIndividualActionsResult(success, type, destinationFolder)
        }
    }

    private fun handleIndividualActionsResult(success: Int, type: BulkOperationType, destinationFolder: File?) {
        val title = if (success == 0) {
            getString(R.string.anErrorHasOccurred)
        } else {
            resources.getQuantityString(type.successMessage, success, success, destinationFolder?.name + "/")
        }
        requireActivity().showSnackbar(title, anchorView = requireActivity().mainFab)
        closeMultiSelect()

        onAllIndividualActionsFinished(type)
    }

    private fun addOrRemoveSelectedFilesToOffline(file: File, type: BulkOperationType) {
        if (!file.isFolder()) {
            val offlineFile = file.getOfflineFile(requireContext())
            val cacheFile = file.getCacheFile(requireContext())
            if (type == BulkOperationType.ADD_OFFLINE) {
                addSelectedFileToOffline(file, offlineFile, cacheFile)
                onIndividualActionSuccess(BulkOperationType.ADD_OFFLINE, Unit)
            } else {
                removeSelectedFileFromOffline(file, offlineFile, cacheFile)
                onIndividualActionSuccess(BulkOperationType.REMOVE_OFFLINE, Unit)
            }
            closeMultiSelect()
        }
    }

    private fun addSelectedFileToOffline(file: File, offlineFile: java.io.File?, cacheFile: java.io.File) {
        if (offlineFile != null && !file.isObsoleteOrNotIntact(cacheFile)) {
            Utils.moveCacheFileToOffline(file, cacheFile, offlineFile)
            runBlocking(Dispatchers.IO) { FileController.updateOfflineStatus(file.id, true) }

            updateFileProgressByFileId(file.id, 100) { _, currentFile ->
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

    private fun Context.trackBulkActionEvent(category: String, action: BulkOperationType, modifiedFileNumber: Int) {
        val trackerName = "bulk" + (if (modifiedFileNumber == 1) "Single" else "") + action.toString()
        trackEvent(category, TrackerAction.CLICK, trackerName, modifiedFileNumber.toFloat())
    }
}
