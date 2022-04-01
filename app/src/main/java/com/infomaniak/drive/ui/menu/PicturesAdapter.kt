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

import android.content.Context
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.checkbox.MaterialCheckBox
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.multiSelect.MultiSelectManager
import com.infomaniak.drive.utils.loadAny
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.views.LoaderAdapter
import com.infomaniak.lib.core.views.LoaderCardView
import com.infomaniak.lib.core.views.ViewHolder
import com.qtalk.recyclerviewfastscroller.RecyclerViewFastScroller
import kotlinx.android.synthetic.main.cardview_picture.view.*
import kotlinx.android.synthetic.main.title_recycler_section.view.*

class PicturesAdapter(
    private val multiSelectManager: MultiSelectManager,
    private val onFileClicked: (file: File) -> Unit,
) : LoaderAdapter<Any>(), RecyclerViewFastScroller.OnPopupTextUpdate {

    var pictureList: ArrayList<File> = arrayListOf()
    var duplicatedList: ArrayList<File> = arrayListOf()

    var scrollBarTagList: ArrayList<String> = arrayListOf()

    private var lastSectionTitle = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
    }

    override fun getItemViewType(position: Int): Int {
        return when {
            super.getItemViewType(position) == VIEW_TYPE_LOADING -> {
                if (position == 0) DisplayType.TITLE.layout else DisplayType.PICTURE.layout
            }
            itemList[position] is File -> DisplayType.PICTURE.layout
            else -> DisplayType.TITLE.layout
        }
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when {
            super.getItemViewType(position) == VIEW_TYPE_LOADING -> {
                if (position == 0) holder.itemView.title.resetLoader() else (holder.itemView as LoaderCardView).start()
            }
            getItemViewType(position) == DisplayType.TITLE.layout -> holder.itemView.title.text = (itemList[position] as String)
            getItemViewType(position) == DisplayType.PICTURE.layout -> bindPictureDisplayType(position, holder)
        }
    }

    private fun bindPictureDisplayType(position: Int, holder: ViewHolder) = with((holder.itemView as LoaderCardView)) {
        val file = (itemList[position] as File)
        displayThumbnail(file)
        handleCheckmark(file)
        setupCardClicksListeners(file)
    }

    private fun LoaderCardView.displayThumbnail(file: File) {
        stop()
        picture.apply {
            loadAny(file.thumbnail())
            contentDescription = file.name
        }
    }

    private fun LoaderCardView.handleCheckmark(file: File) {
        pictureChecked.apply {

            isClickable = false

            if (multiSelectManager.isMultiSelectOn) {
                isChecked = multiSelectManager.isSelectedFile(file)
                isVisible = true
            } else {
                isGone = true
            }
        }
    }

    private fun LoaderCardView.setupCardClicksListeners(file: File) = with(multiSelectManager) {

        setOnClickListener {
            if (isMultiSelectOn) {
                pictureChecked.onFileSelected(file)
            } else {
                onFileClicked(file)
            }
        }

        setOnLongClickListener {
            if (isMultiSelectAuthorized) {
                pictureChecked.onFileSelected(file)
                if (!isMultiSelectOn) openMultiSelect?.invoke()
                true
            } else {
                false
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

    fun formatList(context: Context, newPictureList: ArrayList<File>): ArrayList<Any> {
        pictureList.addAll(newPictureList)
        val addItemList: ArrayList<Any> = arrayListOf()

        for (file in newPictureList) {
            val month = file.getMonth(context)
            scrollBarTagList.add(month)
            if (lastSectionTitle != month) {
                addItemList.add(month)
                lastSectionTitle = month
                scrollBarTagList.add(month)
            }
            addItemList.add(file)
        }

        return addItemList
    }

    fun addDuplicatedImages(context: Context) {
        var newestSectionTitle = itemList.firstOrNull()
        var index = 1
        for (file in duplicatedList) {
            pictureList.add(0, file)

            val month = file.getMonth(context)
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

    private fun File.getMonth(context: Context): String {
        return getLastModifiedAt()
            .format(context.getString(R.string.photosHeaderDateFormat))
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    }

    fun clearPictures() {
        itemList.clear()
        pictureList.clear()
        lastSectionTitle = ""
        notifyDataSetChanged()
    }

    fun deleteByFileId(fileId: Int) {
        indexOf(fileId)?.let(::deleteAt)
        pictureList.indexOfFirstOrNull { (it as? File)?.id == fileId }?.let(pictureList::removeAt)
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
        PICTURE(R.layout.cardview_picture),
    }

    override fun onChange(position: Int): CharSequence {
        return when {
            position >= itemList.count() -> scrollBarTagList.last()
            itemList[position] is String -> itemList[position] as String
            itemList[position] is File -> scrollBarTagList[position]
            else -> ""
        }
    }
}
