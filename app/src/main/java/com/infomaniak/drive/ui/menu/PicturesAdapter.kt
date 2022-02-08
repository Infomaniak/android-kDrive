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

class PicturesAdapter(
    private val onItemClick: (file: File) -> Unit
) : LoaderAdapter<Any>() {

    var itemsSelected: OrderedRealmCollection<File> = RealmList()

    private var lastSectionTitle: String = ""
    var pictureList: ArrayList<File> = arrayListOf()

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

    fun clearPictures() {
        itemList.clear()
        pictureList.clear()
        notifyDataSetChanged()
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        when {
            super.getItemViewType(position) == VIEW_TYPE_LOADING -> {
                (holder.itemView as LoaderCardView).startLoading()
            }
            getItemViewType(position) == DisplayType.TITLE.layout -> {
                holder.itemView.title.text = (itemList[position] as String)
            }
            getItemViewType(position) == DisplayType.PICTURE.layout -> {
                val file = (itemList[position] as File)

                (holder.itemView as LoaderCardView).apply {
                    stopLoading()
                    picture.loadGlideUrl(file.thumbnail())
                    picture.contentDescription = file.name

                    if (multiSelectMode) {
                        pictureChecked.isChecked = isSelectedFile(file)
                        pictureChecked.isVisible = true
                    } else {
                        pictureChecked.isGone = true
                    }

                    setOnClickListener {
                        if (multiSelectMode) {
                            pictureChecked.isChecked = !pictureChecked.isChecked
                            onSelectedFile(file, pictureChecked.isChecked)
                        } else {
                            onItemClick(file)
                        }
                    }

                    setOnLongClickListener {
                        if (enabledMultiSelectMode) {
                            pictureChecked.isChecked = !pictureChecked.isChecked
                            onSelectedFile(file, pictureChecked.isChecked)
                            if (!multiSelectMode) openMultiSelectMode?.invoke()
                            true
                        } else false
                    }
                }
            }
        }
    }

    private fun isSelectedFile(file: File): Boolean {
        return itemsSelected.any { it.isUsable() && it.id == file.id }
    }

    fun getValidItemsSelected() = itemsSelected.filter { it.isUsable() }

    private fun onSelectedFile(file: File, isSelected: Boolean) {
        if (file.isUsable()) {
            when {
                isSelected -> addSelectedFile(file)
                else -> removeSelectedFile(file)
            }
        } else {
            itemsSelected = RealmList()
        }
        updateMultiSelectMode?.invoke()
    }

    private fun addSelectedFile(file: File) {
        itemsSelected.add(file)
    }

    private fun removeSelectedFile(file: File) {
        itemsSelected.remove(file)
    }

    private fun indexOf(fileId: Int) = itemList.indexOfFirst { (it as? File)?.id == fileId }

    fun deleteAt(position: Int) {
        itemList.removeAt(position)
        notifyItemRemoved(position)
    }

    fun deleteByFileId(fileId: Int) {
        val position = indexOf(fileId)
        if (position >= 0) deleteAt(position)

        pictureList.removeAt(pictureList.indexOfFirst { it.id == fileId })
    }

    fun updateFileProgressByFileId(fileId: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)? = null) {
        updateFileProgress(indexOf(fileId), progress, onComplete)
    }

    private fun updateFileProgress(position: Int, progress: Int, onComplete: ((position: Int, file: File) -> Unit)? = null) {
        if (position >= 0) {
            val file = getFile(position)
            file.currentProgress = progress
            notifyItemChanged(position, progress)

            if (progress == 100) {
                onComplete?.invoke(position, file)
            }
        }
    }

    private fun getFile(position: Int) = pictureList[position]

    fun notifyFileChanged(fileId: Int, onChange: ((file: File) -> Unit)? = null) {
        val fileIndex = indexOf(fileId)
        if (fileIndex >= 0) {
            onChange?.invoke(getFile(fileIndex))
            notifyItemChanged(fileIndex)
        }
    }

    enum class DisplayType(val layout: Int) {
        TITLE(R.layout.title_recycler_section),
        PICTURE(R.layout.cardview_picture)
    }
}