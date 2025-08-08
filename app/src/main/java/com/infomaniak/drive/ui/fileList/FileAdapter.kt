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
package com.infomaniak.drive.ui.fileList

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.AdapterDataObserver
import com.google.android.material.card.MaterialCardView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.CardviewFileGridBinding
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.CardviewFolderGridBinding
import com.infomaniak.drive.ui.fileList.FileItemViewHolder.FileGridViewHolder
import com.infomaniak.drive.ui.fileList.FileItemViewHolder.FileListViewHolder
import com.infomaniak.drive.ui.fileList.FileItemViewHolder.FolderGridViewHolder
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectManager
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setCornersRadius
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setupFileProgress
import com.infomaniak.lib.core.databinding.ItemLoadingBinding
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.lib.core.views.LoaderAdapter.Companion.VIEW_TYPE_LOADING
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmRecyclerViewAdapter
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch
import java.util.UUID

open class FileAdapter(
    private val multiSelectManager: MultiSelectManager,
    var fileList: OrderedRealmCollection<File> = RealmList(),
    override val lifecycle: Lifecycle
) : RealmRecyclerViewAdapter<File, FileViewHolder>(fileList, true, true), LifecycleOwner {

    private var fileAsyncListDiffer: AsyncListDiffer<File>? = null

    var onEmptyList: (() -> Unit)? = null
    var onFileClicked: ((file: File) -> Unit)? = null
    var onMenuClicked: ((selectedFile: File) -> Unit)? = null
    var onStopUploadButtonClicked: ((fileName: String) -> Unit)? = null

    var isSelectingFolder = false
    var showShareFileButton = true
    var viewHolderType: DisplayType = DisplayType.LIST

    var uploadInProgress = false
    var publicShareCanDownload = true

    var isComplete = false
    var isHomeOffline = false

    private var offlineMode = false
    private var pendingWifiConnection = false
    private var showLoading = false
    private var fileAdapterObserver: AdapterDataObserver? = null

    private fun createFileAdapterObserver(recyclerView: RecyclerView) = object : AdapterDataObserver() {

        private fun notifyChanged(position: Int) {
            recyclerView.post { if (fileList.isNotEmpty() && position < fileList.count()) notifyItemChanged(position) }
        }

        override fun onItemRangeRemoved(positionStart: Int, itemCount: Int) {
            if (fileList.isEmpty()) {
                onEmptyList?.invoke()
            } else if (viewHolderType == DisplayType.LIST && fileList.isNotEmpty()) {
                when {
                    positionStart == 0 -> notifyChanged(0)
                    positionStart >= fileList.count() -> notifyChanged(fileList.lastIndex)
                }
            }
        }

        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            if (viewHolderType == DisplayType.LIST && fileList.count() > 1) {
                when {
                    positionStart == 0 -> notifyChanged(itemCount)
                    positionStart + itemCount == fileList.count() -> notifyChanged(fileList.lastIndex - itemCount)
                }
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)

        if (fileAdapterObserver == null) {
            createFileAdapterObserver(recyclerView).also {
                fileAdapterObserver = it
                registerAdapterDataObserver(it)
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        runCatching {
            fileAdapterObserver?.let(::unregisterAdapterDataObserver)
            fileAdapterObserver = null
        }
    }

    private fun getFile(position: Int) = fileList[position]

    fun getFiles() = fileList

    fun showLoading() {
        if (!showLoading) {
            showLoading = true
            notifyItemInserted(itemCount - 1)
        }
    }

    fun initAsyncListDiffer() {
        fileAsyncListDiffer = AsyncListDiffer(this, FileDiffCallback())
    }

    fun addFileList(newFileList: List<File>) {
        val oldItemCount = itemCount
        addAll(newFileList)
        if (oldItemCount > 0) {
            notifyItemChanged(oldItemCount - 1)
        }
    }

    fun updateFileList(newFileList: OrderedRealmCollection<File>) {
        fileList = newFileList
        super.updateData(newFileList)
    }

    fun resetRealmListener() {
        if (fileList.isManaged) super.updateData(fileList)
    }

    fun removeRealmDataListener() {
        if (fileList.isManaged) super.updateData(null)
    }

    fun setFiles(newItemList: List<File>, isFileListResetNeeded: Boolean = false) {
        fileList = RealmList(*newItemList.toTypedArray())
        hideLoading()
        // isFileListResetNeeded is used because when sorting file in PublicShareListFragment, the animation of the asynclist
        // is bugged, so we just redraw the whole list. As it's only once it's not a problem
        if (fileAsyncListDiffer == null || isFileListResetNeeded) {
            notifyDataSetChanged()
        } else {
            fileAsyncListDiffer?.submitList(newItemList)
        }
    }

    fun addAll(newItemList: List<File>) {
        val beforeItemCount = itemCount
        val list = ArrayList(fileList).apply { addAll(newItemList) }
        fileList = RealmList(*list.toTypedArray())
        hideLoading()
        notifyItemRangeInserted(beforeItemCount, newItemList.count())
    }

    fun hideLoading() {
        if (showLoading) {
            showLoading = false
            notifyItemRemoved(itemCount)
        }
    }

    fun deleteAt(position: Int) {
        fileList.removeAt(position)
        notifyItemRemoved(position)
    }

    fun getFileObjectsList(realm: Realm?): ArrayList<File> {
        return if (realm != null && fileList.isManaged) ArrayList(realm.copyFromRealm(fileList, 1)) else ArrayList(fileList)
    }

    fun deleteByFileId(fileId: Int) {
        if (!fileList.isManaged) {
            val position = indexOf(fileId)
            if (position >= 0) deleteAt(position)
        }
    }

    fun deleteByFileName(fileName: String) {
        if (!fileList.isManaged) {
            val position = indexOf(fileName)
            if (position >= 0) deleteAt(position)
        }
    }

    private fun indexOf(fileId: Int) = fileList.indexOfFirst { it.id == fileId }

    fun indexOf(fileName: String) = fileList.indexOfFirst { it.name == fileName }

    fun notifyFileChanged(fileId: Int, onChange: ((file: File) -> Unit)? = null) {
        val fileIndex = indexOf(fileId)
        if (fileIndex >= 0) {
            onChange?.invoke(getFile(fileIndex))
            notifyItemChanged(fileIndex)
        }
    }

    fun updateFileProgressByFileId(fileId: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)? = null) {
        updateFileProgress(indexOf(fileId), progress, onComplete)
    }

    fun updateFileProgress(position: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)? = null) {
        if (position >= 0) {
            val file = getFile(position)
            file.currentProgress = progress
            notifyItemChanged(position, progress)

            if (progress == 100) {
                onComplete?.invoke(position, file)
            }
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < fileList.size) {
            when (viewHolderType) {
                DisplayType.LIST -> DisplayType.LIST.layout
                else -> {
                    val file = getFile(position)
                    if (file.isFolder()) DisplayType.GRID_FOLDER.layout else DisplayType.GRID.layout
                }
            }
        } else {
            VIEW_TYPE_LOADING
        }
    }

    override fun getItemCount() = fileList.size + if (showLoading) 1 else 0

    override fun getItemId(position: Int): Long {
        return if (hasStableIds()) {
            fileList.getOrNull(position)?.id?.toLong() ?: UUID.randomUUID().hashCode().toLong()
        } else {
            super.getItemId(position)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder = when (viewType) {
        DisplayType.LIST.layout -> FileListViewHolder(parent)
        DisplayType.GRID.layout -> FileGridViewHolder(parent)
        DisplayType.GRID_FOLDER.layout -> FolderGridViewHolder(parent)
        else -> FileLoaderViewHolder(ItemLoadingBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: List<Any>): Unit = with(holder) {
        if (this !is FileItemViewHolder) return super.onBindViewHolder(holder, position, payloads)

        if (payloads.firstOrNull() is Int) {
            val progress = payloads.first() as Int
            val file = getFile(position).apply { currentProgress = progress }
            if (progress != Utils.INDETERMINATE_PROGRESS || !file.isMarkedAsOffline) {
                progressLayoutView.setupFileProgress(file, true)
                checkIfEnableFile(file)
            }
        } else if (payloads.firstOrNull() is Boolean) {
            toggleFileCheckedState(isFileChecked = payloads.first() as Boolean, position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun FileItemViewHolder.toggleFileCheckedState(isFileChecked: Boolean, position: Int) {
        val isGrid = viewHolderType == DisplayType.GRID

        fileChecked.apply {
            isChecked = isFileChecked
            isVisible = isGrid || isFileChecked
        }
        filePreview.isInvisible = (isFileChecked && !isGrid) || getFile(position).isImporting()
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) = with(holder) {
        if (this !is FileItemViewHolder) return@with

        val file = getFile(position)
        val isGrid = viewHolderType == DisplayType.GRID

        if (isGrid) {
            if (isHomeOffline) {
                (cardView.layoutParams as ConstraintLayout.LayoutParams).apply {
                    dimensionRatio = null
                    height = 150.toPx()
                }
            }
        } else {
            cardView.setCorners(position, itemCount)
        }

        currentBindScope.launch(start = CoroutineStart.UNDISPATCHED) {
            when (binding) {
                is CardviewFileListBinding -> (binding as CardviewFileListBinding).itemViewFile.setFileItem(file, isGrid)
                is CardviewFileGridBinding -> (binding as CardviewFileGridBinding).setFileItem(file, isGrid)
                is CardviewFolderGridBinding -> (binding as CardviewFolderGridBinding).setFileItem(file, isGrid)
            }
        }

        checkIfEnableFile(file)

        when {
            uploadInProgress && !file.isPendingUploadFolder() -> displayStopUploadButton(file)
            multiSelectManager.isMultiSelectOn -> displayFileChecked(file, isGrid)
            else -> fileChecked.isGone = true
        }

        setupMenuButton(file)
        setupCardClicksListeners(file, position)
    }

    private fun FileItemViewHolder.displayStopUploadButton(file: File) {
        stopUploadButton?.apply {
            setOnClickListener { onStopUploadButtonClicked?.invoke(file.name) }
            isVisible = true
        }
    }

    private fun FileItemViewHolder.displayFileChecked(file: File, isGrid: Boolean) = with(multiSelectManager) {
        fileChecked.apply {
            isChecked = isSelectedFile(file)
            isVisible = isGrid || isSelectedFile(file)
        }
        filePreview.isInvisible = file.isImporting() || (isSelectedFile(file) && !isGrid)
    }

    private fun FileItemViewHolder.setupMenuButton(file: File) = menuButton.apply {
        isGone = uploadInProgress
                || isSelectingFolder
                || file.isFromActivities
                || file.isFromSearch
                || (offlineMode && !file.isOffline)
                || !publicShareCanDownload

        setOnClickListener { onMenuClicked?.invoke(file) }
    }

    private fun FileItemViewHolder.setupCardClicksListeners(file: File, position: Int) = with(multiSelectManager) {

        fun selectFile() {
            onFileSelected(file, !isSelectedFile(file))
            notifyItemChanged(position, isSelectedFile(file))
        }

        cardView.apply {
            setOnClickListener { if (isMultiSelectOn) selectFile() else onFileClicked?.invoke(file) }

            setOnLongClickListener {
                if (isMultiSelectAuthorized) {
                    if (!isMultiSelectOn) {
                        fileChecked.isChecked = false
                        openMultiSelect?.invoke()
                    }
                    selectFile()
                    true
                } else {
                    false
                }
            }
        }
    }

    fun contains(fileName: String) = fileList.any { it.name == fileName }

    private fun FileItemViewHolder.checkIfEnableFile(file: File) = when {
        uploadInProgress -> {
            if (file.isPendingUploadFolder()) {
                fileDate?.text = file.path
            } else {
                val enable = file.currentProgress > 0 && binding.context.isSyncActive()
                val title = when {
                    enable -> R.string.uploadInProgressTitle
                    pendingWifiConnection -> R.string.uploadNetworkErrorWifiRequired
                    else -> R.string.uploadInProgressPending
                }
                fileDate?.setText(title)
            }
        }
        else -> {
            if (isSelectingFolder || offlineMode) {
                enabledFile(file.isFolder() || (offlineMode && file.isOffline))
            } else {
                enabledFile()
            }
        }
    }

    private fun FileItemViewHolder.enabledFile(enable: Boolean = true) {
        disabledView.isGone = enable
        cardView.isEnabled = enable
    }

    private fun onFileSelected(file: File, isSelected: Boolean) = with(multiSelectManager) {
        if (file.isUsable()) {
            when {
                isSelected -> addSelectedFile(file)
                else -> removeSelectedFile(file)
            }
        } else {
            resetSelectedItems()
        }
        updateMultiSelect?.invoke()
    }

    fun configureAllSelected(isSelectedAll: Boolean) = with(multiSelectManager) {
        isSelectAllOn = isSelectedAll
        resetSelectedItems()
        exceptedItemsIds.clear()
        notifyItemRangeChanged(0, itemCount)
    }

    private fun addSelectedFile(file: File) = with(multiSelectManager) {
        if (isSelectAllOn) {
            exceptedItemsIds.removeIf { it == file.id }
        } else {
            selectedItemsIds.add(file.id)
            selectedItems.add(file)
        }
    }

    private fun removeSelectedFile(file: File) = with(multiSelectManager) {
        if (isSelectAllOn) {
            exceptedItemsIds.add(file.id)
        } else {
            selectedItemsIds.remove(file.id)
            selectedItems.remove(file)
        }
    }

    fun toggleOfflineMode(context: Context, isOffline: Boolean) {
        if (offlineMode != isOffline) {
            offlineMode = isOffline
            notifyItemRangeChanged(0, itemCount)
        }

        checkIsPendingWifi(context)
    }

    fun checkIsPendingWifi(context: Context) {
        if (uploadInProgress && AppSettings.onlyWifiSync) {
            pendingWifiConnection = context.isSyncActive(false)
        }
    }

    enum class DisplayType(val layout: Int) {
        GRID(R.layout.cardview_file_grid),
        GRID_FOLDER(R.layout.cardview_folder_grid),
        LIST(R.layout.cardview_file_list)
    }

    class FileDiffCallback : DiffUtil.ItemCallback<File>() {
        override fun areItemsTheSame(oldItem: File, newItem: File): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: File, newItem: File): Boolean {

            val areCategoriesTheSame = oldItem.categories.size == newItem.categories.size &&
                    oldItem.categories.filterIndexed { index, fileCategory ->
                        newItem.categories[index]?.categoryId != fileCategory.categoryId
                    }.isEmpty()

            return oldItem.name == newItem.name &&
                    oldItem.isFavorite == newItem.isFavorite &&
                    oldItem.isOffline == newItem.isOffline &&
                    oldItem.color == newItem.color &&
                    oldItem.lastModifiedAt == newItem.lastModifiedAt &&
                    oldItem.size == newItem.size &&
                    areCategoriesTheSame
        }
    }

    companion object {
        fun MaterialCardView.setCorners(position: Int, itemCount: Int) {
            val radius = resources.getDimension(R.dimen.cardViewRadius)
            val topCornerRadius = if (position == 0) radius else 0.0f
            val bottomCornerRadius = if (position == itemCount - 1) radius else 0.0f
            setCornersRadius(topCornerRadius, bottomCornerRadius)
        }
    }
}
