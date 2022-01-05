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
package com.infomaniak.drive.ui.fileList

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.AppSettings
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.SyncUtils.isSyncActive
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setupFileProgress
import com.infomaniak.lib.core.views.LoaderAdapter.Companion.VIEW_TYPE_LOADING
import com.infomaniak.lib.core.views.LoaderAdapter.Companion.createLoadingViewHolder
import com.infomaniak.lib.core.views.ViewHolder
import io.realm.OrderedRealmCollection
import io.realm.Realm
import io.realm.RealmList
import io.realm.RealmRecyclerViewAdapter
import kotlinx.android.synthetic.main.cardview_file_list.view.*
import kotlinx.android.synthetic.main.item_file.view.*

open class FileAdapter(
    var fileList: OrderedRealmCollection<File> = RealmList()
) : RealmRecyclerViewAdapter<File, ViewHolder>(fileList, true, true) {

    var itemsSelected: OrderedRealmCollection<File> = RealmList()

    var onEmptyList: (() -> Unit)? = null
    var onFileClicked: ((file: File) -> Unit)? = null
    var onMenuClicked: ((selectedFile: File) -> Unit)? = null
    var onStopUploadButtonClicked: ((index: Int, fileName: String) -> Unit)? = null
    var openMultiSelectMode: (() -> Unit)? = null
    var updateMultiSelectMode: (() -> Unit)? = null

    var enabledMultiSelectMode: Boolean = false
    var multiSelectMode: Boolean = false
    var allSelected = false
    var offlineMode: Boolean = false
    var selectFolder: Boolean = false
    var showShareFileButton: Boolean = true
    var viewHolderType: DisplayType = DisplayType.LIST

    var uploadInProgress: Boolean = false

    var isComplete = false

    private var pendingWifiConnection: Boolean = false
    private var showLoading = false
    private var fileAdapterObserver: RecyclerView.AdapterDataObserver? = null

    private fun createFileAdapterObserver(recyclerView: RecyclerView): RecyclerView.AdapterDataObserver {
        return object : RecyclerView.AdapterDataObserver() {

            private fun notifyChanged(position: Int) {
                recyclerView.post {
                    if (fileList.isNotEmpty() && position < fileList.count()) notifyItemChanged(position)
                }
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
        kotlin.runCatching {
            fileAdapterObserver?.let(::unregisterAdapterDataObserver)
            fileAdapterObserver = null
        }
    }

    private fun getFile(position: Int) = fileList[position]

    fun getFiles() = fileList

    fun getValidItemsSelected() = itemsSelected.filter { it.isUsable() }

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

    fun setFiles(newItemList: ArrayList<File>) {
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
                else -> if (getFile(position).isFolder() || getFile(position).isDrive()) DisplayType.GRID_FOLDER.layout else DisplayType.GRID.layout
            }
        } else VIEW_TYPE_LOADING
    }

    override fun getItemCount() = fileList.size + if (showLoading) 1 else 0

    override fun getItemId(position: Int): Long {
        val file = fileList.getOrNull(position)
        return if (hasStableIds() && file != null) file.id.toLong() else super.getItemId(position)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_LOADING -> createLoadingViewHolder(parent)
            else -> ViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.firstOrNull() is Int && getItemViewType(position) != VIEW_TYPE_LOADING) {
            val file = getFile(position)
            val progress = payloads.first() as Int
            if (progress != Utils.INDETERMINATE_PROGRESS || !file.isPendingOffline(holder.itemView.context)) {
                holder.itemView.apply {
                    setupFileProgress(file, true)
                    checkIfEnableFile(file)
                }
            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) != VIEW_TYPE_LOADING) {
            val file = getFile(position)

            holder.itemView.apply {
                val isGrid = viewHolderType == DisplayType.GRID

                if (!isGrid) {
                    val radius = resources.getDimension(R.dimen.cardViewRadius)
                    val topCornerRadius = if (position == 0) radius else 0.0f
                    val bottomCornerRadius = if (position == itemCount - 1) radius else 0.0f

                    fileCardView.shapeAppearanceModel = fileCardView.shapeAppearanceModel
                        .toBuilder()
                        .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
                        .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
                        .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                        .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                        .build()
                }

                setFileItem(file, isGrid)

                checkIfEnableFile(file)

                when {
                    uploadInProgress && !file.isPendingUploadFolder() -> {
                        stopUploadButton?.setOnClickListener { onStopUploadButtonClicked?.invoke(position, file.name) }
                        stopUploadButton?.isVisible = true
                    }
                    multiSelectMode -> {
                        fileChecked.isChecked = isSelectedFile(file) || allSelected
                        fileChecked.isVisible = true
                        filePreview.isVisible = isGrid
                    }
                    else -> {
                        filePreview.isVisible = true
                        fileChecked.isGone = true
                    }
                }

                menuButton?.apply {
                    isGone = uploadInProgress
                            || selectFolder
                            || file.isDrive()
                            || file.isTrashed()
                            || file.isFromActivities
                            || file.isFromSearch
                            || (offlineMode && !file.isOffline)
                    setOnClickListener { onMenuClicked?.invoke(file) }
                }

                fileChecked.setOnClickListener {
                    onSelectedFile(file, fileChecked.isChecked)
                }
                setOnClickListener {
                    if (multiSelectMode) {
                        fileChecked.isChecked = !fileChecked.isChecked
                        onSelectedFile(file, fileChecked.isChecked)
                    } else {
                        onFileClicked?.invoke(file)
                    }
                }
                setOnLongClickListener {
                    if (enabledMultiSelectMode) {
                        fileChecked.isChecked = !fileChecked.isChecked
                        onSelectedFile(file, fileChecked.isChecked)
                        if (!multiSelectMode) openMultiSelectMode?.invoke()
                        true
                    } else false
                }
            }
        }
    }

    fun contains(fileName: String) = fileList.any { it.name == fileName }

    private fun View.checkIfEnableFile(file: File) = when {
        uploadInProgress -> {
            if (file.isPendingUploadFolder()) {
                fileDate?.setText(file.path)
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
            if (selectFolder || offlineMode) enabledFile(file.isFolder() || file.isDrive() || (offlineMode && file.isOffline))
            else enabledFile()
        }
    }

    private fun View.enabledFile(enable: Boolean = true) {
        disabled.isGone = enable
        fileCardView.isEnabled = enable
    }

    private fun onSelectedFile(file: File, isSelected: Boolean) {
        if (file.isUsable()) {
            when {
                allSelected -> { // if all selected, unselect everything and only select the clicked one (like web-app)
                    configureAllSelected(false)
                    addSelectedFile(file)
                }
                isSelected -> addSelectedFile(file)
                else -> removeSelectedFile(file)
            }
        } else {
            itemsSelected = RealmList()
        }
        updateMultiSelectMode?.invoke()
    }

    fun configureAllSelected(isSelectedAll: Boolean) {
        allSelected = isSelectedAll
        itemsSelected = RealmList()
        notifyItemRangeChanged(0, itemCount)
    }

    private fun addSelectedFile(file: File) {
        itemsSelected.add(file)
    }

    private fun removeSelectedFile(file: File) {
        itemsSelected.remove(file)
    }

    private fun isSelectedFile(file: File): Boolean {
        return itemsSelected.any { it.isUsable() && it.id == file.id }
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
}
