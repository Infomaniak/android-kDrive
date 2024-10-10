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
package com.infomaniak.drive.ui.menu

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.viewbinding.ViewBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.databinding.CardviewGalleryBinding
import com.infomaniak.drive.databinding.TitleRecyclerSectionBinding
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectManager
import com.infomaniak.drive.utils.loadAny
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.views.LoaderAdapter

class GalleryAdapter(
    private val multiSelectManager: MultiSelectManager,
    private val onFileClicked: (file: File) -> Unit,
) : LoaderAdapter<Any>(), RecyclerViewFastScroller.OnPopupTextUpdate {

    var galleryList: ArrayList<File> = arrayListOf()
    var duplicatedList: ArrayList<File> = arrayListOf()

    private var lastSectionTitle = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GalleryViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val binding = if (viewType == R.layout.title_recycler_section) {
            TitleRecyclerSectionBinding.inflate(layoutInflater, parent, false)
        } else {
            CardviewGalleryBinding.inflate(layoutInflater, parent, false)
        }

        return GalleryViewHolder(binding)
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            super.getItemViewType(position) == VIEW_TYPE_LOADING -> {
                if (position == 0) DisplayType.TITLE.layout else DisplayType.PREVIEW.layout
            }
            itemList[position] is File -> DisplayType.PREVIEW.layout
            else -> DisplayType.TITLE.layout
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with((holder as GalleryViewHolder).binding) {
        when {
            super.getItemViewType(position) == VIEW_TYPE_LOADING -> {
                if (position == 0) {
                    (this as TitleRecyclerSectionBinding).title.resetLoader()
                } else {
                    (this as CardviewGalleryBinding).root.start()
                }
            }
            getItemViewType(position) == DisplayType.TITLE.layout -> {
                (this as TitleRecyclerSectionBinding).title.text = (itemList[position] as String)
            }
            getItemViewType(position) == DisplayType.PREVIEW.layout -> {
                (this as CardviewGalleryBinding).bindGalleryDisplayType(position)
            }
        }
    }

    private fun CardviewGalleryBinding.bindGalleryDisplayType(position: Int) {
        val file = (itemList[position] as File)
        displayThumbnail(file)
        handleCheckmark(file)
        setupCardClicksListeners(file)
    }

    private fun CardviewGalleryBinding.displayThumbnail(file: File) {
        root.stop()
        videoViews.isVisible = file.getFileType() == ExtensionType.VIDEO
        preview.apply {
            loadAny(ApiRoutes.getThumbnailUrl(file))
            contentDescription = file.name
        }
    }

    private fun CardviewGalleryBinding.handleCheckmark(file: File) {
        mediaChecked.apply {

            isClickable = false

            if (multiSelectManager.isMultiSelectOn) {
                isChecked = multiSelectManager.isSelectedFile(file)
                isVisible = true
            } else {
                isGone = true
            }
        }
    }

    private fun CardviewGalleryBinding.setupCardClicksListeners(file: File) = with(multiSelectManager) {

        root.apply {
            setOnClickListener { if (isMultiSelectOn) mediaChecked.onFileSelected(file) else onFileClicked(file) }

            setOnLongClickListener {
                if (isMultiSelectAuthorized) {
                    mediaChecked.onFileSelected(file)
                    if (!isMultiSelectOn) openMultiSelect?.invoke()
                    true
                } else {
                    false
                }
            }
        }
    }

    private fun MaterialCheckBox.onFileSelected(file: File) = with(multiSelectManager) {
        isChecked = !isChecked

        if (file.isUsable()) {
            if (isChecked) {
                selectedItemsIds.add(file.id)
                selectedItems.add(file)
            } else {
                selectedItemsIds.remove(file.id)
                selectedItems.remove(file)
            }
        } else {
            resetSelectedItems()
        }

        updateMultiSelect?.invoke()
    }

    /**
     * TODO: Move to viewModel
     */
    fun formatList(newGalleryList: ArrayList<File>): ArrayList<Any> {
        galleryList.addAll(newGalleryList)
        val addItemList: ArrayList<Any> = arrayListOf()

        for (file in newGalleryList) {
            val month = file.getMonth()
            if (lastSectionTitle != month) {
                addItemList.add(month)
                lastSectionTitle = month
            }
            addItemList.add(file)
        }

        return addItemList
    }

    fun addDuplicatedImages() {
        var newestSectionTitle = itemList.firstOrNull()
        var index = 1
        for (file in duplicatedList) {
            galleryList.add(0, file)

            val month = file.getMonth()
            if (newestSectionTitle != month) {
                // This case can only be hit once, when adding duplicated images.
                // If we need to add a new month section title, it needs to be inserted at position 0.
                // If we only insert images, without creating a new month section title,
                // they need to be inserted at position 1, right after the existing month title.
                index = 0
                itemList.add(index++, month)
                newestSectionTitle = month
            }

            itemList.add(index++, file)
        }

        duplicatedList.clear()
    }

    private fun File.getMonth(): String {
        return getLastModifiedAt().format("MMMM yyyy").capitalizeFirstChar()
    }

    fun clearGallery() {
        itemList.clear()
        galleryList.clear()
        lastSectionTitle = ""
        notifyDataSetChanged()
    }

    fun deleteByFileId(fileId: Int) {
        indexOf(fileId)?.let(::deleteAt)
        galleryList.indexOfFirstOrNull { (it as? File)?.id == fileId }?.let(galleryList::removeAt)
    }

    fun deleteAt(position: Int) {
        val previousItem = itemList.getOrNull(position - 1)
        val nextItem = itemList.getOrNull(position + 1)

        itemList.removeAt(position)

        if (previousItem is String && nextItem is String) {
            itemList.removeAt(position - 1)
            notifyItemRangeRemoved(position - 1, 2)
        } else {
            notifyItemRemoved(position)
        }
    }

    fun updateFileProgressByFileId(fileId: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)? = null) {
        indexOf(fileId)?.let { position -> updateFileProgress(position, progress, onComplete) }
    }

    private fun updateFileProgress(position: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)? = null) {
        val file = getFile(position)
        file.currentProgress = progress
        notifyItemChanged(position, progress)

        if (progress == 100) onComplete?.invoke(position, file)
    }

    fun updateOfflineStatus(fileId: Int) {
        indexOf(fileId)?.let { position -> (itemList[position] as? File)?.isOffline = true }
    }

    fun notifyFileChanged(fileId: Int, onChange: ((file: File) -> Unit)? = null) {
        indexOf(fileId)?.let { position ->
            onChange?.invoke(getFile(position))
            notifyItemChanged(position)
        }
    }

    private fun indexOf(fileId: Int) = itemList.indexOfFirstOrNull { (it as? File)?.id == fileId }

    private fun getFile(position: Int) = itemList[position] as File

    private fun List<Any>.indexOfFirstOrNull(predicate: (Any) -> Boolean): Int? {
        val index = indexOfFirst(predicate)
        return if (index >= 0) index else null
    }

    enum class DisplayType(val layout: Int) {
        TITLE(R.layout.title_recycler_section),
        PREVIEW(R.layout.cardview_gallery),
    }

    override fun onChange(position: Int): CharSequence {
        return when (val item = itemList.getOrNull(position)) {
            null -> lastSectionTitle
            is String -> item
            is File -> item.getMonth()
            else -> ""
        }
    }

    class GalleryViewHolder(val binding: ViewBinding) : ViewHolder(binding.root)
}
