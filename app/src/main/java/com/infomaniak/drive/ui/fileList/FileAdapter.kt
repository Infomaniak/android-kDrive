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

import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setupFileProgress
import com.infomaniak.drive.views.PaginationAdapter
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_file_list.view.*
import kotlinx.android.synthetic.main.item_file.view.*

open class FileAdapter(
    override val itemList: ArrayList<File> = arrayListOf()
) : PaginationAdapter<File>() {

    val itemSelected: ArrayList<File> = arrayListOf()

    private var currentFileIdProgress: Int? = null
    var importContainsProgress: Boolean = false

    var onFileClicked: ((file: File) -> Unit)? = null
    var onMenuClicked: ((selectedFile: File) -> Unit)? = null
    var onStopUploadButtonClicked: ((fileName: String) -> Unit)? = null
    var openMultiSelectMode: (() -> Unit)? = null
    var updateMultiSelectMode: (() -> Unit)? = null

    var enabledMultiSelectMode: Boolean = false
    var multiSelectMode: Boolean = false
    var offlineMode: Boolean = false
    var selectFolder: Boolean = false
    var showShareFileButton: Boolean = true
    var uploadInProgress: Boolean = false
    var viewHolderType: DisplayType = DisplayType.LIST

    fun addActivities(newFileList: ArrayList<File>, activities: Map<out Int, File.LocalFileActivity>) {
        var currentIndex = 0
        var fileIndex = 0
        while (fileIndex in 0 until newFileList.size) {
            val file = newFileList[fileIndex]
            if (currentIndex <= itemList.lastIndex) {
                val currentAdapterFile = itemList[currentIndex]

                when {
                    activities[currentAdapterFile.id] == File.LocalFileActivity.IS_DELETE -> {
                        deleteAt(currentIndex)
                        fileIndex--
                    }
                    activities[file.id] == null -> {
                        if (currentAdapterFile.id != file.id) {
                            updateAt(currentIndex, file)
                        }
                        currentIndex++
                    }
                    else -> {
                        when (activities[file.id]) {
                            File.LocalFileActivity.IS_UPDATE -> {
                                updateAt(currentIndex, file)
                                currentIndex++
                            }
                            File.LocalFileActivity.IS_NEW -> {
                                addAt(currentIndex, file)
                                currentIndex++
                            }
                            File.LocalFileActivity.IS_DELETE -> {
                                deleteAt(currentIndex)
                            }
                        }
                    }
                }
            } else {
                addFileList(ArrayList(newFileList.subList(fileIndex, newFileList.size)))
                return
            }
            fileIndex++
        }

        if (itemList.size > newFileList.size) {
            val oldCount = itemList.size
            itemList.subList(currentIndex, oldCount).clear()
            if (currentIndex > 0) {
                notifyItemChanged(currentIndex - 1)
            }
            notifyItemRangeRemoved(currentIndex, oldCount - newFileList.size)
        }
    }

    fun addFileList(newFileList: ArrayList<File>) {
        val oldItemCount = itemCount
        addAll(newFileList)
        if (oldItemCount > 0) {
            notifyItemChanged(oldItemCount - 1)
        }
    }

    fun addAt(position: Int, newFile: File) {
        itemList.add(position, newFile)
        notifyItemInserted(position)

        if (viewHolderType == DisplayType.LIST) {
            if (position == 0 && itemList.size > 1) {
                notifyItemChanged(1)
            } else if (position == itemList.size - 1 && itemList.size > 1) {
                notifyItemChanged(itemList.size - 2)
            }
        }
    }

    private fun updateAt(position: Int, newFile: File) {
        itemList[position] = newFile
        notifyItemChanged(position)
    }

    fun deleteAt(position: Int) {
        itemList.removeAt(position)
        notifyItemRemoved(position)

        if (viewHolderType == DisplayType.LIST) {
            if (position == 0 && itemList.size > 0) {
                notifyItemChanged(0)
            } else if (position == itemList.size && itemList.size > 0) {
                notifyItemChanged(itemList.size - 1)
            }
        }
    }

    fun deleteByFileId(fileId: Int) {
        val position = indexOf(fileId)
        if (position >= 0) deleteAt(position)
    }

    fun indexOf(fileId: Int) = itemList.indexOfFirst { it.id == fileId }

    fun notifyFileChanged(fileId: Int, onChange: ((file: File) -> Unit)? = null) {
        val fileIndex = indexOf(fileId)
        if (fileIndex >= 0) {
            onChange?.invoke(itemList[fileIndex])
            notifyItemChanged(fileIndex)
        }
    }

    open fun updateFileProgress(fileId: Int, progress: Int, onComplete: (file: File) -> Unit) {
        val fileIndex = indexOf(fileId)
        if (fileIndex >= 0) {
            itemList[fileIndex].currentProgress = progress
            importContainsProgress = uploadInProgress && progress <= 100
            notifyItemChanged(fileIndex, progress)

            if (progress == 100) {
                onComplete(itemList[fileIndex])
            }
        }
        currentFileIdProgress = fileId
    }

    override fun getItemViewType(position: Int): Int {
        var itemViewType = super.getItemViewType(position)
        if (itemViewType == VIEW_TYPE_NORMAL) {
            itemViewType = when (viewHolderType) {
                DisplayType.LIST -> DisplayType.LIST.layout
                else -> if (itemList[position].isFolder() || itemList[position].isDrive()) DisplayType.GRID_FOLDER.layout else DisplayType.GRID.layout
            }
        }
        return itemViewType
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return when (viewType) {
            VIEW_TYPE_LOADING -> createLoadingViewHolder(parent)
            else -> ViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int, payloads: List<Any>) {
        if (payloads.firstOrNull() is Int && getItemViewType(position) != VIEW_TYPE_LOADING) {
            val file = itemList[position]
            val progress = payloads.first() as Int
            FileController.getFileById(file.id)
            if (progress != Utils.INDETERMINATE_PROGRESS || !file.isPendingOffline(holder.itemView.context)) {
                holder.itemView.apply {
                    setupFileProgress(file, progress)
                    checkIfEnableFile(file, position)
                }

            }
        } else {
            super.onBindViewHolder(holder, position, payloads)
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        if (getItemViewType(position) != VIEW_TYPE_LOADING) {
            val file = itemList[position]

            holder.itemView.apply {
                val isGrid = viewHolderType == DisplayType.GRID

                if (!isGrid) {
                    var topCornerRadius = 0F
                    var bottomCornerRadius = 0F
                    if (position == 0) topCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
                    if (position == itemCount - 1) bottomCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)

                    fileCardView.shapeAppearanceModel = fileCardView.shapeAppearanceModel
                        .toBuilder()
                        .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
                        .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
                        .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                        .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
                        .build()
                }

                setFileItem(file, isGrid)

                checkIfEnableFile(file, position)

                when {
                    uploadInProgress -> {
                        stopUploadButton?.setOnClickListener { onStopUploadButtonClicked?.invoke(file.name) }
                        stopUploadButton?.visibility = VISIBLE
                    }
                    multiSelectMode -> {
                        fileChecked.isChecked = isSelectedFile(file)
                        fileChecked.visibility = VISIBLE
                        filePreview.visibility = if (isGrid) VISIBLE else GONE
                    }
                    else -> {
                        filePreview.visibility = VISIBLE
                        fileChecked.visibility = GONE
                    }
                }

                val isInProgress = (position == 0 && importContainsProgress)
                menuButton?.visibility = when {
                    uploadInProgress || isInProgress || selectFolder ||
                            file.isDrive() || file.isTrashed() ||
                            file.isFromActivities || file.isFromSearch ||
                            (offlineMode && !file.isOffline) -> GONE
                    else -> VISIBLE
                }
                menuButton?.setOnClickListener { onMenuClicked?.invoke(file) }

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

    fun contains(fileName: String): Boolean {
        return itemList.find { it.name == fileName } != null
    }

    private fun View.checkIfEnableFile(file: File, position: Int) = when {
        uploadInProgress -> {
            val enable = position == 0 && importContainsProgress
            fileDate?.setText(if (enable) R.string.uploadInProgressTitle else R.string.uploadInProgressPending)
        }
        else -> {
            if (selectFolder || offlineMode) enabledFile(file.isFolder() || file.isDrive() || (offlineMode && file.isOffline))
            else enabledFile()
        }
    }

    private fun View.enabledFile(enable: Boolean = true) {
        disabled.visibility = if (enable) GONE else VISIBLE
        fileCardView.isEnabled = enable
    }

    private fun onSelectedFile(file: File, isSelected: Boolean) {
        if (isSelected) {
            addSelectedFile(file)
        } else {
            removeSelectedFile(file)
        }
        updateMultiSelectMode?.invoke()
    }

    private fun addSelectedFile(file: File) {
        itemSelected.add(file)
    }

    private fun removeSelectedFile(file: File) {
        itemSelected.remove(file)
    }

    fun isSelectedFile(file: File): Boolean {
        return itemSelected.find { it.id == file.id } != null
    }

    fun toggleOfflineMode(isOffline: Boolean) {
        if (offlineMode != isOffline) {
            offlineMode = isOffline
            notifyItemRangeChanged(0, itemCount)
        }
    }

    enum class DisplayType(val layout: Int) {
        GRID(R.layout.cardview_file_grid),
        GRID_FOLDER(R.layout.cardview_folder_grid),
        LIST(R.layout.cardview_file_list)
    }
}
