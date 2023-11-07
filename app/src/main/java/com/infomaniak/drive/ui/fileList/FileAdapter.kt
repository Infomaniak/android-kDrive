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
package com.infomaniak.drive.ui.fileList

import android.content.Context
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import androidx.viewbinding.ViewBinding
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.checkbox.MaterialCheckBox
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.CardviewFileGridBinding
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.CardviewFolderGridBinding
import com.infomaniak.drive.ui.fileList.FileAdapter.FileViewHolder
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectManager
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setCornersRadius
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setupFileProgress
import com.infomaniak.drive.views.ProgressLayoutView
import com.infomaniak.lib.core.databinding.ItemLoadingBinding
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.toPx
import com.infomaniak.lib.core.views.LoaderAdapter.Companion.VIEW_TYPE_LOADING
import com.infomaniak.lib.core.views.ViewHolder
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmRecyclerViewAdapter
import java.util.UUID

open class FileAdapter(
    private val multiSelectManager: MultiSelectManager,
    var fileList: OrderedRealmCollection<File> = RealmList(),
) : RealmRecyclerViewAdapter<File, FileViewHolder>(fileList, true, true) {

    var onEmptyList: (() -> Unit)? = null
    var onFileClicked: ((file: File) -> Unit)? = null
    var onMenuClicked: ((selectedFile: File) -> Unit)? = null
    var onStopUploadButtonClicked: ((index: Int, fileName: String) -> Unit)? = null

    var offlineMode = false
    var selectFolder = false
    var showShareFileButton = true
    var viewHolderType: DisplayType = DisplayType.LIST

    var uploadInProgress = false

    var isComplete = false
    var isHomeOffline = false

    private var pendingWifiConnection = false
    private var showLoading = false
    private var fileAdapterObserver: RecyclerView.AdapterDataObserver? = null

    private fun createFileAdapterObserver(recyclerView: RecyclerView) = object : RecyclerView.AdapterDataObserver() {

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

    fun addFileList(newFileList: ArrayList<File>) {
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

    fun setFiles(newItemList: List<File>) {
        fileList = RealmList(*newItemList.toTypedArray())
        hideLoading()
        notifyDataSetChanged()
    }

    fun addAll(newItemList: ArrayList<File>) {
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
                    if (file.isFolder() || file.isDrive()) DisplayType.GRID_FOLDER.layout else DisplayType.GRID.layout
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)

        val (binding, fileItemView) = when (viewType) {
            DisplayType.LIST.layout -> {
                CardviewFileListBinding.inflate(layoutInflater, parent, false).let {
                    it to FileItemViews(
                        cardView = it.fileCardView,
                        disabledView = it.disabled,
                        progressLayoutView = it.itemViewFile.progressLayout,
                        fileChecked = it.itemViewFile.fileChecked,
                        filePreview = it.itemViewFile.filePreview,
                        menuButton = it.itemViewFile.menuButton,
                        fileDate = it.itemViewFile.fileDate,
                        stopUploadButton = it.itemViewFile.stopUploadButton,
                    )
                }
            }
            DisplayType.GRID.layout -> CardviewFileGridBinding.inflate(layoutInflater, parent, false).let {
                it to FileItemViews(
                    cardView = it.fileCardView,
                    disabledView = it.disabled,
                    progressLayoutView = it.progressLayout,
                    fileChecked = it.fileChecked,
                    filePreview = it.filePreview,
                    menuButton = it.menuButton,
                )
            }
            DisplayType.GRID_FOLDER.layout -> CardviewFolderGridBinding.inflate(layoutInflater, parent, false).let {
                it to FileItemViews(
                    cardView = it.fileCardView,
                    disabledView = it.disabled,
                    progressLayoutView = it.progressLayout,
                    fileChecked = it.fileChecked,
                    filePreview = it.filePreview,
                    menuButton = it.menuButton,
                )
            }
            else -> ItemLoadingBinding.inflate(layoutInflater, parent, false) to null
        }

        return FileViewHolder(binding, fileItemView)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int, payloads: List<Any>): Unit = with(holder) {
        if (payloads.firstOrNull() is Int && getItemViewType(position) != VIEW_TYPE_LOADING) {
            val progress = payloads.first() as Int
            val file = getFile(position).apply { currentProgress = progress }
            if (progress != Utils.INDETERMINATE_PROGRESS || !file.isPendingOffline(binding.context)) {
                fileItemViews!!.apply {
                    progressLayoutView.setupFileProgress(file, true)
                    checkIfEnableFile(file, binding.context)
                }
            }
        } else if (payloads.firstOrNull() is Boolean) {
            fileItemViews?.toggleFileCheckedState(isFileChecked = payloads.first() as Boolean, position)
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    private fun FileItemViews.toggleFileCheckedState(isFileChecked: Boolean, position: Int) {
        val isGrid = viewHolderType == DisplayType.GRID

        fileChecked.apply {
            isChecked = isFileChecked
            isVisible = isGrid || isFileChecked
        }
        filePreview.isInvisible = (isFileChecked && !isGrid) || getFile(position).isImporting()
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) = with(holder) {
        val viewType = getItemViewType(position)
        if (viewType == VIEW_TYPE_LOADING) return@with

        val file = getFile(position)
        val isGrid = viewHolderType == DisplayType.GRID
        val fileCardView = fileItemViews!!.cardView

        if (isGrid) {
            if (isHomeOffline) {
                (fileCardView.layoutParams as ConstraintLayout.LayoutParams).apply {
                    dimensionRatio = null
                    height = 150.toPx()
                }
            }
        } else {
            fileCardView.setCorners(position, itemCount)
        }

        when (viewType) {
            DisplayType.LIST.layout -> (binding as CardviewFileListBinding).itemViewFile.setFileItem(file, isGrid)
            DisplayType.GRID.layout -> (binding as CardviewFileGridBinding).setFileItem(file, isGrid)
            DisplayType.GRID_FOLDER.layout -> (binding as CardviewFolderGridBinding).setFileItem(file, isGrid)
        }

        fileItemViews.apply {
            checkIfEnableFile(file, binding.context)

            when {
                uploadInProgress && !file.isPendingUploadFolder() -> displayStopUploadButton(position, file)
                multiSelectManager.isMultiSelectOn -> displayFileChecked(file, isGrid)
                else -> fileChecked.isGone = true
            }

            setupMenuButton(file)
            setupCardClicksListeners(file, position)
        }
    }

    private fun FileItemViews.displayStopUploadButton(position: Int, file: File) {
        stopUploadButton?.apply {
            setOnClickListener { onStopUploadButtonClicked?.invoke(position, file.name) }
            isVisible = true
        }
    }

    private fun FileItemViews.displayFileChecked(file: File, isGrid: Boolean) = with(multiSelectManager) {
        fileChecked.apply {
            isChecked = isSelectedFile(file)
            isVisible = isGrid || isSelectedFile(file)
        }
        filePreview.isInvisible = file.isImporting() || (isSelectedFile(file) && !isGrid)
    }

    private fun FileItemViews.setupMenuButton(file: File) = menuButton.apply {
        isGone = uploadInProgress
                || selectFolder
                || file.isDrive()
                || file.isFromActivities
                || file.isFromSearch
                || (offlineMode && !file.isOffline)

        setOnClickListener { onMenuClicked?.invoke(file) }
    }

    private fun FileItemViews.setupCardClicksListeners(file: File, position: Int) = with(multiSelectManager) {

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

    private fun FileItemViews.checkIfEnableFile(file: File, context: Context) = when {
        uploadInProgress -> {
            if (file.isPendingUploadFolder()) {
                fileDate?.text = file.path
            } else {
                val enable = file.currentProgress > 0 && context.isSyncActive()
                val title = when {
                    enable -> R.string.uploadInProgressTitle
                    pendingWifiConnection -> R.string.uploadNetworkErrorWifiRequired
                    else -> R.string.uploadInProgressPending
                }
                fileDate?.setText(title)
            }
        }
        else -> {
            if (selectFolder || offlineMode) {
                enabledFile(file.isFolder() || file.isDrive() || (offlineMode && file.isOffline))
            } else {
                enabledFile()
            }
        }
    }

    private fun FileItemViews.enabledFile(enable: Boolean = true) {
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                exceptedItemsIds.removeIf { it == file.id }
            } else {
                val index = exceptedItemsIds.indexOf(file.id)
                if (index != -1) exceptedItemsIds.removeAt(index) else return@with
            }
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

    data class FileItemViews(
        val cardView: MaterialCardView,
        val disabledView: View,
        val progressLayoutView: ProgressLayoutView,
        val fileChecked: MaterialCheckBox,
        val filePreview: ImageView,
        val menuButton: MaterialButton,
        val fileDate: TextView? = null,
        val stopUploadButton: MaterialButton? = null,
    )

    companion object {
        fun MaterialCardView.setCorners(position: Int, itemCount: Int) {
            val radius = resources.getDimension(R.dimen.cardViewRadius)
            val topCornerRadius = if (position == 0) radius else 0.0f
            val bottomCornerRadius = if (position == itemCount - 1) radius else 0.0f
            setCornersRadius(topCornerRadius, bottomCornerRadius)
        }
    }

    class FileViewHolder(val binding: ViewBinding, val fileItemViews: FileItemViews?) : ViewHolder(binding.root)
}
