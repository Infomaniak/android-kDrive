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
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.loadGlideUrl
import com.infomaniak.lib.core.utils.format
import com.infomaniak.lib.core.views.LoaderAdapter
import com.infomaniak.lib.core.views.LoaderCardView
import com.infomaniak.lib.core.views.ViewHolder
import io.realm.OrderedRealmCollection
import io.realm.RealmList
import kotlinx.android.synthetic.main.cardview_picture.view.*
import kotlinx.android.synthetic.main.title_recycler_section.view.*
import java.util.*

class PicturesAdapter(private val onItemClick: (file: File) -> Unit) : LoaderAdapter<Any>() {

    var selectedItems: OrderedRealmCollection<File> = RealmList()

    private var lastSectionTitle: String = ""
    var pictureList: ArrayList<File> = arrayListOf()
    var duplicatedList: ArrayList<File> = arrayListOf()

    var enabledMultiSelectMode: Boolean = false // If the multi selection is allowed to be used
    var multiSelectMode: Boolean = false // If the multi selection is opened
    var openMultiSelectMode: (() -> Unit)? = null
    var updateMultiSelectMode: (() -> Unit)? = null

    fun formatList(context: Context, newPictureList: ArrayList<File>): ArrayList<Any> {
        pictureList.addAll(newPictureList)
        val addItemList: ArrayList<Any> = arrayListOf()

        for (picture in newPictureList) {
            val month = picture.getLastModifiedAt()
                .format(context.getString(R.string.photosHeaderDateFormat))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

            if (lastSectionTitle != month) {
                addItemList.add(month)
                lastSectionTitle = month
            }
            addItemList.add(picture)
        }

        return addItemList
    }

    fun addDuplicatedImages(context: Context) {
        var newestSectionTitle = itemList.firstOrNull()
        var index = 1
        for (file in duplicatedList) {
            pictureList.add(0, file)

            val month = file.getLastModifiedAt()
                .format(context.getString(R.string.photosHeaderDateFormat))
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }

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

    override fun getItemViewType(position: Int): Int {
        return when {
            super.getItemViewType(position) == VIEW_TYPE_LOADING -> DisplayType.PICTURE.layout
            itemList[position] is File -> DisplayType.PICTURE.layout
            else -> DisplayType.TITLE.layout
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when {
            super.getItemViewType(position) == VIEW_TYPE_LOADING -> (holder.itemView as LoaderCardView).startLoading()
            getItemViewType(position) == DisplayType.TITLE.layout -> holder.itemView.title.text = (itemList[position] as String)
            getItemViewType(position) == DisplayType.PICTURE.layout -> bindPictureDisplayType(position, holder)
        }
    }

    private fun bindPictureDisplayType(position: Int, holder: ViewHolder) = with((holder.itemView as LoaderCardView)) {
        val file = (itemList[position] as File)
        displayThumbnail(file)
        handleCheckmark(file)
        setupClickListener(file)
        setupLongClickListener(file)
    }

    private fun LoaderCardView.displayThumbnail(file: File) {
        stopLoading()
        picture.loadGlideUrl(file.thumbnail())
        picture.contentDescription = file.name
    }

    private fun LoaderCardView.handleCheckmark(file: File) {
        if (multiSelectMode) {
            pictureChecked.isChecked = isSelectedFile(file)
            pictureChecked.isVisible = true
        } else {
            pictureChecked.isGone = true
        }
    }

    private fun LoaderCardView.setupClickListener(file: File) {
        setOnClickListener {
            if (multiSelectMode) {
                pictureChecked.isChecked = !pictureChecked.isChecked
                onSelectedFile(file, pictureChecked.isChecked)
            } else {
                onItemClick(file)
            }
        }
    }

    private fun LoaderCardView.setupLongClickListener(file: File) {
        setOnLongClickListener {
            if (enabledMultiSelectMode) {
                pictureChecked.isChecked = !pictureChecked.isChecked
                onSelectedFile(file, pictureChecked.isChecked)
                if (!multiSelectMode) openMultiSelectMode?.invoke()
                true
            } else {
                false
            }
        }
    }

    fun clearPictures() {
        itemList.clear()
        pictureList.clear()
        notifyDataSetChanged()
    }

    fun getValidItemsSelected() = selectedItems.filter { it.isUsable() }

    private fun isSelectedFile(file: File): Boolean = selectedItems.any { it.isUsable() && it.id == file.id }

    private fun onSelectedFile(file: File, isSelected: Boolean) {
        if (file.isUsable()) {
            if (isSelected) addSelectedFile(file) else removeSelectedFile(file)
        } else {
            selectedItems = RealmList()
        }
        updateMultiSelectMode?.invoke()
    }

    private fun addSelectedFile(file: File) {
        selectedItems.add(file)
    }

    private fun removeSelectedFile(file: File) {
        selectedItems.remove(file)
    }

    private fun indexOf(fileId: Int) = itemList.indexOfFirstOrNull { (it as? File)?.id == fileId }

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

    fun deleteByFileId(fileId: Int) {
        indexOf(fileId)?.let(::deleteAt)
        pictureList.indexOfFirstOrNull { (it as? File)?.id == fileId }?.let(pictureList::removeAt)
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

    private fun getFile(position: Int) = itemList[position] as File

    fun updateOfflineStatus(fileId: Int) {
        indexOf(fileId)?.let { position -> (itemList[position] as? File)?.isOffline = true }
    }

    fun notifyFileChanged(fileId: Int, onChange: ((file: File) -> Unit)? = null) {
        indexOf(fileId)?.let { position ->
            onChange?.invoke(getFile(position))
            notifyItemChanged(position)
        }
    }

    private fun List<Any>.indexOfFirstOrNull(predicate: (Any) -> Boolean): Int? {
        val index = indexOfFirst(predicate)
        return if (index >= 0) index else null
    }

    enum class DisplayType(val layout: Int) {
        TITLE(R.layout.title_recycler_section),
        PICTURE(R.layout.cardview_picture),
    }
}
