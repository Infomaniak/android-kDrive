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
package com.infomaniak.drive.ui.fileList.multiSelect

import android.app.Dialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.CollapsingToolbarLayout
import com.infomaniak.core.legacy.utils.ApiErrorCode.Companion.translateError
import com.infomaniak.core.legacy.utils.whenResultIsOk
import com.infomaniak.drive.MatomoDrive.MatomoCategory
import com.infomaniak.drive.MatomoDrive.trackBulkActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.UploadTask.Companion.LIMIT_EXCEEDED_ERROR_CODE
import com.infomaniak.drive.data.models.BulkOperation
import com.infomaniak.drive.data.models.BulkOperationType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.MqttClientWrapper
import com.infomaniak.drive.databinding.MultiSelectLayoutBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.MainViewModel.MultiSelectMediatorState
import com.infomaniak.drive.ui.fileList.SelectFolderActivity
import com.infomaniak.drive.ui.fileList.SelectFolderActivityArgs
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectManager.MultiSelectResult
import com.infomaniak.drive.ui.menu.OfflineFileFragment
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.BulkOperationsUtils
import com.infomaniak.drive.utils.BulkOperationsUtils.launchBulkOperationWorker
import com.infomaniak.drive.utils.NotificationPermission
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.OTHER_ROOT_ID
import com.infomaniak.drive.utils.Utils.downloadAsOfflineFiles
import com.infomaniak.drive.utils.Utils.duplicateFilesClicked
import com.infomaniak.drive.utils.Utils.moveFileClicked
import com.infomaniak.drive.utils.showSnackbar

abstract class MultiSelectFragment(private val matomoCategory: MatomoCategory) : Fragment(), MultiSelectResult {

    protected val mainViewModel: MainViewModel by activityViewModels()
    protected val multiSelectManager = MultiSelectManager()
    protected var adapter: RecyclerView.Adapter<*>? = null
    protected var multiSelectLayout: MultiSelectLayoutBinding? = null
    private var multiSelectToolbar: CollapsingToolbarLayout? = null
    private var swipeRefresh: SwipeRefreshLayout? = null

    private val notificationPermission by lazy { NotificationPermission() }

    protected abstract val userDrive: UserDrive?

    private val selectFolderResultLauncher = registerForActivityResult(StartActivityForResult()) {
        it.whenResultIsOk { data ->
            data?.extras?.let { bundle ->
                SelectFolderActivityArgs.fromBundle(bundle).apply {
                    val bulkOperationType = customArgs?.getParcelable<BulkOperationType>(BULK_OPERATION_CUSTOM_TAG)!!
                    val areAllFromTheSameFolder = customArgs.getBoolean(ARE_ALL_FROM_THE_SAME_FOLDER_CUSTOM_TAG, true)

                    performBulkOperation(
                        type = bulkOperationType,
                        areAllFromTheSameFolder = areAllFromTheSameFolder,
                        allSelectedFilesCount = getAllSelectedFilesCount(),
                        destinationFolder = File(id = folderId, name = folderName, driveId = AccountUtils.currentDriveId),
                    )
                }
            }
        }
    }

    abstract fun initMultiSelectLayout(): MultiSelectLayoutBinding?
    abstract fun initMultiSelectToolbar(): CollapsingToolbarLayout?
    abstract fun initSwipeRefreshLayout(): SwipeRefreshLayout?
    abstract fun getAllSelectedFilesCount(): Int?

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        notificationPermission.registerPermission(this)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        multiSelectLayout = initMultiSelectLayout()
        multiSelectToolbar = initMultiSelectToolbar()
        swipeRefresh = initSwipeRefreshLayout()

        if (multiSelectManager.isMultiSelectOn) {
            openMultiSelect()
            onItemSelected()
        }

        super.onViewCreated(view, savedInstanceState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        multiSelectLayout = null
        multiSelectToolbar = null
        swipeRefresh = null
    }

    fun openMultiSelect() {
        swipeRefresh?.isEnabled = false

        multiSelectManager.isMultiSelectOn = true

        adapter?.apply { notifyItemRangeChanged(0, itemCount) }

        multiSelectToolbar?.isGone = true
        multiSelectLayout?.root?.isVisible = true
    }

    fun onItemSelected(selectedNumber: Int? = null) = with(multiSelectManager) {

        val fileSelectedNumber = when {
            selectedNumber != null -> selectedNumber
            isSelectAllOn -> (getAllSelectedFilesCount() ?: 0) - exceptedItemsIds.size
            else -> getValidSelectedItems().size
        }

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

    fun setupBasicMultiSelectLayout() {
        multiSelectLayout?.apply {
            moveButtonMultiSelect.isInvisible = true
            deleteButtonMultiSelect.isInvisible = true
        }
    }

    open fun closeMultiSelect() {
        // This is to avoid to close the multiselect layout for every items we have selected
        if (multiSelectLayout?.root?.isVisible == true) {
            swipeRefresh?.isEnabled = true

            multiSelectManager.apply {
                resetSelectedItems()
                exceptedItemsIds.clear()
                isSelectAllOn = false
                isMultiSelectOn = false
            }

            adapter?.apply { notifyItemRangeChanged(0, itemCount) }

            multiSelectToolbar?.isVisible = true
            multiSelectLayout?.root?.isGone = true
        }
    }

    open fun onMenuButtonClicked(
        multiSelectBottomSheet: MultiSelectActionsBottomSheetDialog,
        areAllFromTheSameFolder: Boolean,
    ) {
        multiSelectBottomSheet.apply {
            arguments = getMultiSelectBottomSheetArguments(areAllFromTheSameFolder)
        }.show(childFragmentManager, "MultiSelectActionsBottomSheetDialog")
    }

    private fun getMultiSelectBottomSheetArguments(areAllFromTheSameFolder: Boolean): Bundle {
        val (fileIds, exceptFileIds, onlyFolders, onlyFavorite, onlyOffline, isAllSelected) = multiSelectManager.getMenuNavArgs()
        return MultiSelectActionsBottomSheetDialogArgs(
            userDrive = userDrive,
            parentId = if (userDrive?.sharedWithMe == true) OTHER_ROOT_ID else mainViewModel.currentFolder.value?.id!!,
            fileIds = if (isAllSelected && this !is OfflineFileFragment) intArrayOf() else fileIds,
            exceptFileIds = if (isAllSelected) exceptFileIds else intArrayOf(),
            onlyFolders = onlyFolders,
            onlyFavorite = onlyFavorite,
            onlyOffline = onlyOffline,
            isAllSelected = isAllSelected,
            areAllFromTheSameFolder = areAllFromTheSameFolder,
        ).toBundle()
    }

    fun moveFiles(disabledFolderId: Int?) {
        requireContext().moveFileClicked(disabledFolderId, selectFolderResultLauncher, mainViewModel)
    }

    fun deleteFiles(allSelectedFilesCount: Int? = null) {
        performBulkOperation(type = BulkOperationType.TRASH, allSelectedFilesCount = allSelectedFilesCount)
    }

    fun duplicateFiles() {
        requireContext().duplicateFilesClicked(selectFolderResultLauncher, mainViewModel)
    }

    fun restoreIn() {
        Intent(requireContext(), SelectFolderActivity::class.java).apply {
            putExtras(
                SelectFolderActivityArgs(
                    userId = AccountUtils.currentUserId,
                    driveId = AccountUtils.currentDriveId,
                    customArgs = bundleOf(
                        BULK_OPERATION_CUSTOM_TAG to BulkOperationType.RESTORE_IN,
                        ARE_ALL_FROM_THE_SAME_FOLDER_CUSTOM_TAG to false,
                    )
                ).toBundle()
            )
            selectFolderResultLauncher.launch(this)
        }
    }

    open fun performBulkOperation(
        type: BulkOperationType,
        folderId: Int? = null,
        areAllFromTheSameFolder: Boolean = true,
        allSelectedFilesCount: Int? = null,
        destinationFolder: File? = null,
        color: String? = null,
    ) = with(requireContext()) {
        // Canceling sync of online files because everytime we put the app in background and then foreground,
        // we try to sync offline files but if at the same time, we try to remove/add some at the same time, it can
        // lead to weird behavior.
        mainViewModel.cancelSyncOfflineFiles()

        val selectedFiles = multiSelectManager.getValidSelectedItems(type)
        val fileCount = (allSelectedFilesCount?.minus(multiSelectManager.exceptedItemsIds.size)) ?: selectedFiles.size

        trackBulkActionEvent(matomoCategory, type, fileCount)

        val sendActions: (dialog: Dialog?) -> Unit = sendActions(
            type, areAllFromTheSameFolder, folderId, fileCount, selectedFiles, destinationFolder, color
        )

        when (type) {
            BulkOperationType.TRASH -> Utils.createConfirmation(
                context = this,
                title = getString(R.string.modalMoveTrashTitle),
                // 2 to always get "other"
                message = resources.getQuantityString(R.plurals.modalMoveTrashDescription, 2, fileCount),
                autoDismiss = false,
                isDeletion = true,
                onConfirmation = sendActions,
            )
            BulkOperationType.DELETE_PERMANENTLY -> {
                Utils.confirmFileDeletion(
                    context = this,
                    fileCount = fileCount,
                    fromTrash = true,
                    onConfirmation = sendActions,
                )
            }
            BulkOperationType.ADD_OFFLINE -> {
                // Set all selected file as offline in order for BulkDownloadWorker to download them
                mainViewModel.markFilesAsOffline(selectedFiles.map { selectedFile -> selectedFile.id }, true)
                sendActions(null)
            }
            else -> sendActions(null)
        }
    }

    private fun sendActions(
        type: BulkOperationType,
        areAllFromTheSameFolder: Boolean,
        folderId: Int?,
        fileCount: Int,
        selectedFiles: List<File>,
        destinationFolder: File?,
        color: String?,
    ): (Dialog?) -> Unit = { dialog ->
        val canBulkAllSelectedFiles = multiSelectManager.isSelectAllOn
        val hasEnoughSelectedFilesToBulk = selectedFiles.size > BulkOperationsUtils.MIN_SELECTED
        val isNotOfflineBulk = type != BulkOperationType.ADD_OFFLINE && type != BulkOperationType.REMOVE_OFFLINE

        if (areAllFromTheSameFolder && isNotOfflineBulk && (canBulkAllSelectedFiles || hasEnoughSelectedFilesToBulk)) {
            with(multiSelectManager) {
                sendBulkAction(
                    fileCount = fileCount,
                    bulkOperation = BulkOperation(
                        action = type,
                        fileIds = if (isSelectAllOn) null else selectedFiles.map { it.id },
                        exceptedFilesIds = if (isSelectAllOn) exceptedItemsIds else null,
                        parent = currentFolder!!,
                        destinationFolderId = destinationFolder?.id,
                    ),
                    dialog = dialog,
                )
            }
        } else {
            val mediator = mainViewModel.createMultiSelectMediator()
            enableMultiSelectButtons(false)
            when (type) {
                BulkOperationType.ADD_OFFLINE -> sendAddOfflineAction(type, folderId, mediator)
                BulkOperationType.REMOVE_OFFLINE -> sendRemoveOfflineAction(type, selectedFiles, mediator)
                else -> sendAllIndividualActions(selectedFiles, type, mediator, destinationFolder, color)
            }
            observeMediator(mediator, fileCount, type, destinationFolder, dialog)
        }
    }

    private fun sendBulkAction(fileCount: Int = 0, bulkOperation: BulkOperation, dialog: Dialog? = null) {
        MqttClientWrapper.start(coroutineScope = viewLifecycleOwner.lifecycleScope) {
            multiSelectManager.performCancellableBulkOperation(bulkOperation).observe(viewLifecycleOwner) { apiResponse ->
                dialog?.dismiss()
                if (apiResponse.isSuccess()) {
                    apiResponse.data?.let { cancellableAction ->
                        requireContext().launchBulkOperationWorker(
                            BulkOperationsUtils.generateWorkerData(cancellableAction.cancelId, fileCount, bulkOperation.action)
                        )
                    }
                } else {
                    val messageRes = if (apiResponse.error?.code == LIMIT_EXCEEDED_ERROR_CODE) {
                        R.string.errorFilesLimitExceeded
                    } else {
                        apiResponse.translateError()
                    }

                    showSnackbar(messageRes)
                }
                closeMultiSelect()
            }
        }
    }

    private fun sendAddOfflineAction(
        type: BulkOperationType,
        folderId: Int?,
        mediator: MediatorLiveData<MultiSelectMediatorState>,
    ) {
        if (folderId != null) {
            notificationPermission.checkNotificationPermission(requestPermission = true)
            mediator.addSource(
                downloadAsOfflineFiles(
                    context = requireContext(),
                    folderId = folderId,
                    onSuccess = { onIndividualActionSuccess(type) }
                ),
                mainViewModel.updateMultiSelectMediator(mediator),
            )
        }
    }

    private fun sendRemoveOfflineAction(
        type: BulkOperationType,
        selectedFiles: List<File>,
        mediator: MediatorLiveData<MultiSelectMediatorState>,
    ) {
        mediator.addSource(
            mainViewModel.removeSelectedFilesFromOffline(
                files = selectedFiles,
                onSuccess = { onIndividualActionSuccess(type) }
            ),
            mainViewModel.updateMultiSelectMediator(mediator),
        )
    }

    private fun sendAllIndividualActions(
        selectedFiles: List<File>,
        type: BulkOperationType,
        mediator: MediatorLiveData<MultiSelectMediatorState>,
        destinationFolder: File?,
        color: String?,
    ) {
        selectedFiles.reversed().forEach { selectedFile ->
            getFile(selectedFile)?.let { file -> sendIndividualAction(file, type, mediator, destinationFolder, color) }
        }
    }

    private fun getFile(file: File): File? {
        return when {
            file.isManagedAndValidByRealm() -> {
                file.realm.copyFromRealm(file, 0)
            }
            file.isNotManagedByRealm() -> file
            else -> null
        }
    }

    private fun sendIndividualAction(
        file: File,
        type: BulkOperationType,
        mediator: MediatorLiveData<MultiSelectMediatorState>,
        destinationFolder: File?,
        color: String?,
    ) = with(mainViewModel) {
        when (type) {
            BulkOperationType.TRASH -> {
                mediator.addSource(
                    deleteFile(file, onSuccess = { onIndividualActionSuccess(type, it) }),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.MOVE -> {
                mediator.addSource(
                    moveFile(
                        file = file,
                        newParent = destinationFolder!!,
                        onSuccess = { onIndividualActionSuccess(type, it) },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.COPY -> {
                mediator.addSource(
                    duplicateFile(
                        file = file,
                        destinationId = destinationFolder!!.id,
                        onSuccess = { it.data?.let { file -> onIndividualActionSuccess(type, file) } },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.MANAGE_CATEGORIES -> Unit
            BulkOperationType.COLOR_FOLDER -> {
                if (color != null && file.isAllowedToBeColored()) {
                    val userDrive = UserDrive(sharedWithMe = file.getVisibilityType() == File.VisibilityType.IS_IN_SHARED_SPACE)
                    mediator.addSource(
                        updateFolderColor(file, color, userDrive),
                        updateMultiSelectMediator(mediator),
                    )
                } else {
                    mediator.value = mediator.value?.let {
                        MultiSelectMediatorState(it.numberOfSuccessfulActions, it.totalOfActions + 1, it.errorCode)
                    }
                }
            }
            BulkOperationType.ADD_OFFLINE,
            BulkOperationType.REMOVE_OFFLINE -> Unit
            BulkOperationType.ADD_FAVORITES -> {
                mediator.addSource(
                    addFileToFavorites(
                        file = file,
                        onSuccess = { onIndividualActionSuccess(type, file.id) }),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.REMOVE_FAVORITES -> {
                mediator.addSource(
                    deleteFileFromFavorites(
                        file = file,
                        onSuccess = { onIndividualActionSuccess(type, file.id) },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.RESTORE_IN -> {
                mediator.addSource(
                    restoreTrashFile(
                        file = file,
                        newFolderId = destinationFolder!!.id,
                        onSuccess = { onIndividualActionSuccess(type, file.id) },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.RESTORE_TO_ORIGIN -> {
                mediator.addSource(
                    restoreTrashFile(
                        file = file,
                        onSuccess = { onIndividualActionSuccess(type, file.id) },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
            BulkOperationType.DELETE_PERMANENTLY -> {
                mediator.addSource(
                    deleteTrashFile(
                        file = file,
                        onSuccess = { onIndividualActionSuccess(type, file.id) },
                    ),
                    updateMultiSelectMediator(mediator),
                )
            }
        }
    }

    private fun observeMediator(
        mediator: MediatorLiveData<MultiSelectMediatorState>,
        fileCount: Int,
        type: BulkOperationType,
        destinationFolder: File?,
        dialog: Dialog? = null,
    ) {
        mediator.observe(viewLifecycleOwner) { (success, total, error) ->
            if (total == fileCount) {
                dialog?.dismiss()
                handleIndividualActionsResult(success, error, type, destinationFolder)
            }
        }
    }

    private fun handleIndividualActionsResult(
        success: Int,
        errorCode: String?,
        type: BulkOperationType,
        destinationFolder: File?,
    ) {
        val title = if (errorCode == LIMIT_EXCEEDED_ERROR_CODE) {
            getString(R.string.errorFilesLimitExceeded)
        } else {
            if (success == 0) {
                getString(R.string.anErrorHasOccurred)
            } else {
                resources.getQuantityString(type.successMessage, success, success, destinationFolder?.name + "/")
            }
        }

        showSnackbar(title, showAboveFab = true)
        closeMultiSelect()

        onAllIndividualActionsFinished(type)
    }

    companion object {
        const val BULK_OPERATION_CUSTOM_TAG = "bulk_operation_type"
        const val ARE_ALL_FROM_THE_SAME_FOLDER_CUSTOM_TAG = "are_all_from_the_same_folder"
    }
}
