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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.shape.CornerFamily
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.sortCategoriesList
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_category.view.*
import java.util.*
import kotlin.collections.ArrayList

class CategoriesAdapter(
    private val onCategoryChanged: (categoryID: Int, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    var canEditCategory: Boolean = false
    var canDeleteCategory: Boolean = false
    var onMenuClicked: ((category: UICategory) -> Unit)? = null
    var allCategories: ArrayList<UICategory> = arrayListOf()
    var filteredCategories: ArrayList<UICategory> = arrayListOf()
    private var filterQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cardview_category, parent, false))
    }

    override fun getItemCount() = filteredCategories.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        with(holder.itemView.categoryCard) {
            val category = filteredCategories[position]
            setLayouts(position)
            setData(category)
            setStates(category)
            setListeners(category)
        }
    }

    private fun MaterialCardView.setLayouts(position: Int) {
        var topCornerRadius = 0.0f
        var bottomCornerRadius = 0.0f
        if (position == 0) topCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
        val trimmedQuery = filterQuery.trim()
        if (position == itemCount - 1 && (trimmedQuery.isBlank() || doesCategoryExist(trimmedQuery))) {
            bottomCornerRadius = context.resources.getDimension(R.dimen.cardViewRadius)
        }

        shapeAppearanceModel = shapeAppearanceModel
            .toBuilder()
            .setTopLeftCorner(CornerFamily.ROUNDED, topCornerRadius)
            .setTopRightCorner(CornerFamily.ROUNDED, topCornerRadius)
            .setBottomLeftCorner(CornerFamily.ROUNDED, bottomCornerRadius)
            .setBottomRightCorner(CornerFamily.ROUNDED, bottomCornerRadius)
            .build()
    }

    private fun MaterialCardView.setData(category: UICategory) {
        categoryIcon.setBackgroundColor(Color.parseColor(category.color))
        categoryTitle.text = category.name
    }

    private fun MaterialCardView.setStates(category: UICategory) {
        categoryProgressBar.isGone = true
        checkIcon.isVisible = category.isSelected
        isCheckable = false
        isEnabled = true
        menuButton.isVisible = canEditCategory || (canDeleteCategory && !category.isPredefined)
    }

    private fun MaterialCardView.setListeners(category: UICategory) {
        setOnClickListener {
            categoryProgressBar.isVisible = true
            checkIcon.isGone = true
            isEnabled = false
            onCategoryChanged(category.id, !category.isSelected)
        }

        menuButton.setOnClickListener { onMenuClicked?.invoke(category) }
    }

    fun setAll(newCategories: List<UICategory>) {
        allCategories = ArrayList(newCategories)
        filterCategories()
    }

    fun addCategory(id: Int, name: String, color: String) {
        with(allCategories.toMutableList()) {
            add(
                UICategory(
                    id = id,
                    name = name,
                    color = color,
                    isPredefined = false,
                    isSelected = true,
                    userUsageCount = 1,
                    addedToFileAt = Date(),
                )
            )
            allCategories = ArrayList(sortCategoriesList())
        }
        filterCategories()
    }

    fun editCategory(id: Int, name: String?, color: String?) {
        val index = allCategories.indexOfFirst { it.id == id }
        with(allCategories[index]) {
            this.name = name ?: this.name
            this.color = color ?: this.color
        }
        filterCategories()
    }

    fun deleteCategory(categoryId: Int) {
        with(allCategories) {
            val index = indexOfFirst { it.id == categoryId }
            removeAt(index)
        }
        filterCategories()
    }

    fun updateCategory(categoryId: Int, isSelected: Boolean) {
        with(allCategories) {
            val index = indexOfFirst { it.id == categoryId }
            with(this[index]) {
                this.isSelected = isSelected
                this.addedToFileAt = if (isSelected) Date() else null
            }
            allCategories = ArrayList(sortCategoriesList())
        }
        filterCategories()
    }

    fun updateFilter(query: String) {
        filterQuery = query
        filterCategories()
    }

    fun doesCategoryExist(query: String): Boolean {
        return !filteredCategories.none { it.name.equals(query, true) }
    }

    private fun filterCategories() {
        filteredCategories = ArrayList(allCategories.filter { it.name.contains(filterQuery, true) })
        notifyDataSetChanged()
    }

    data class UICategory(
        val id: Int,
        var name: String,
        var color: String,
        val isPredefined: Boolean,
        var isSelected: Boolean,
        val userUsageCount: Int,
        var addedToFileAt: Date?,
    )
}
