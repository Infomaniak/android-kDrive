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
import android.util.Log
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
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.android.synthetic.main.title_recycler_section.view.*

class PicturesAdapter(
    private val onItemClick: (file: File) -> Unit
) : LoaderAdapter<Any>() {

    var itemsSelected: OrderedRealmCollection<File> = RealmList()

    private var lastSectionTitle: String = ""
    var pictureList: ArrayList<File> = arrayListOf()

    var enabledMultiSelectMode: Boolean = false
    var multiSelectMode: Boolean = false
    var allSelected = false
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
                        pictureChecked.isChecked = isSelectedFile(file) || allSelected
                        pictureChecked.isVisible = true
                    }
                    else {
                        pictureChecked.isGone = true
                    }

                    setOnClickListener {
                        if (multiSelectMode) {
                            Log.e("pic-sel", "setOnLongClickListener: before - ${pictureChecked.isChecked}", )
                            pictureChecked.isChecked = !pictureChecked.isChecked
                            Log.e("pic-sel", "setOnLongClickListener: after - ${pictureChecked.isChecked}", )
                            onSelectedFile(file, pictureChecked.isChecked)
                        } else {
                            onItemClick(file)
                        }
                    }

                    // Checker par rapport au FileAdapter.kt comment c'est fait là bas le fait de gérer la multi sélection tout ça tout ça
//                    setOnClickListener {
//                        if (multiSelectMode) {
//                            fileChecked.isChecked = !fileChecked.isChecked
//                            onSelectedFile(file, fileChecked.isChecked)
//                        } else {
//                            onFileClicked?.invoke(file)
//                        }
//                    }
//                    setOnLongClickListener {
//                        if (enabledMultiSelectMode) {
//                            fileChecked.isChecked = !fileChecked.isChecked
//                            onSelectedFile(file, fileChecked.isChecked)
//                            if (!multiSelectMode) openMultiSelectMode?.invoke()
//                            true
//                        } else false
//                    }
                    setOnLongClickListener {
                        if (enabledMultiSelectMode) {
                            Log.e("pic-sel", "setOnLongClickListener: before - ${pictureChecked.isChecked}", )
                            pictureChecked.isChecked = !pictureChecked.isChecked
                            Log.e("pic-sel", "setOnLongClickListener: after - ${pictureChecked.isChecked}", )
//                            Log.e("pic-sel", "setOnLongClickListener: selected: $file, ${pictureChecked.isChecked}")
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
        Log.e("pic-sel", "onSelectedFile: selected: $file, $isSelected")

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

    enum class DisplayType(val layout: Int) {
        TITLE(R.layout.title_recycler_section),
        PICTURE(R.layout.cardview_picture)
    }
}