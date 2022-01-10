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
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.fileList.fileDetails.SelectCategoriesFragment.UsageMode
import com.infomaniak.drive.utils.setCornersRadius
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.cardview_category.view.*
import java.util.*

class CategoriesAdapter(
    private val onCategoryChanged: (id: Int, isSelected: Boolean) -> Unit
) : RecyclerView.Adapter<ViewHolder>() {

    var canEditCategory: Boolean = false
    var canDeleteCategory: Boolean = false
    var isCreateRowVisible: Boolean = false
    var onMenuClicked: ((category: UICategory) -> Unit)? = null
    var allCategories: List<UICategory> = listOf()
    var filteredCategories: List<UICategory> = listOf()
    private var filterQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.cardview_category, parent, false))

    override fun getItemCount() = filteredCategories.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = with(holder.itemView.categoryCard) {
        val category = filteredCategories[position]
        isCheckable = false
        isEnabled = true
        categoryIcon.setBackgroundColor(Color.parseColor(category.color))
        categoryTitle.text = category.name
        setCorners(position)
        setMenuButton(category)
        handleSelectedState(category)
        setClickOnCategory(category)
    }

    private fun MaterialCardView.setCorners(position: Int) {
        val radius = resources.getDimension(R.dimen.cardViewRadius)
        val topCornerRadius = if (position == 0) radius else 0.0f
        val bottomCornerRadius = if (position == itemCount - 1 && !isCreateRowVisible) radius else 0.0f
        setCornersRadius(topCornerRadius, bottomCornerRadius)
    }

    private fun MaterialCardView.setMenuButton(category: UICategory) {
        menuButton.apply {
            isVisible = canEditCategory || (canDeleteCategory && !category.isPredefined)
            setOnClickListener { onMenuClicked?.invoke(category) }
        }
    }

    private fun MaterialCardView.handleSelectedState(category: UICategory) {
        category.selectedState.let {
            categoryProgressBar.isVisible = it == SelectedState.PROCESSING
            checkIcon.isVisible = it == SelectedState.SELECTED
        }
    }

    private fun MaterialCardView.setClickOnCategory(category: UICategory) {
        setOnClickListener {
            isEnabled = false
            categoryProgressBar.isVisible = true
            checkIcon.isGone = true
            val isSelected = category.selectedState == SelectedState.NOT_SELECTED
            category.selectedState = SelectedState.PROCESSING
            onCategoryChanged(category.id, isSelected)
        }
    }

    fun setItems(categories: List<UICategory>, usageMode: UsageMode) {
        val oldCategories = filteredCategories.toDiffUtilCategories()
        allCategories = categories.sorted(usageMode)
        filteredCategories = allCategories.filtered()
        val newCategories = filteredCategories.toDiffUtilCategories()
        notifyAdapter(oldCategories, newCategories)
    }

    fun deleteCategory(categoryId: Int) {
        val oldCategories = filteredCategories.toDiffUtilCategories()
        allCategories = allCategories.filter { it.id != categoryId }
        filteredCategories = allCategories.filtered()
        val newCategories = filteredCategories.toDiffUtilCategories()
        notifyAdapter(oldCategories, newCategories)
    }

    fun selectCategory(categoryId: Int, isSelected: Boolean, usageMode: UsageMode) {
        val oldCategories = filteredCategories.toDiffUtilCategories()

        with(allCategories) {
            this[indexOfFirst { it.id == categoryId }].apply {
                this.selectedState = if (isSelected) SelectedState.SELECTED else SelectedState.NOT_SELECTED
                this.addedToFileAt = if (isSelected) Date() else null
            }
            allCategories = sorted(usageMode)
        }

        filteredCategories = allCategories.filtered()
        val newCategories = filteredCategories.toDiffUtilCategories()

        notifyAdapter(oldCategories, newCategories)
    }

    fun updateFilter(query: String) {
        val oldCategories = filteredCategories.toDiffUtilCategories()
        filterQuery = query
        filteredCategories = allCategories.filtered()
        val newCategories = filteredCategories.toDiffUtilCategories()
        notifyAdapter(oldCategories, newCategories)
    }

    fun doesCategoryExist(query: String): Boolean = filteredCategories.any { it.name.equals(query, true) }

    private fun notifyAdapter(oldItems: List<DiffUtilCategory>, newItems: List<DiffUtilCategory>) {
        DiffUtil.calculateDiff(CategoriesDiffCallback(oldItems, newItems, isCreateRowVisible)).dispatchUpdatesTo(this)
    }

    private fun List<UICategory>.filtered(): List<UICategory> = filter { it.name.contains(filterQuery, true) }

    private fun List<UICategory>.sorted(usageMode: UsageMode): List<UICategory> {
        return if (usageMode == UsageMode.SELECTED_CATEGORIES) sortedSearchCategories() else sortedFileCategories()
    }

    private fun List<UICategory>.sortedFileCategories(): List<UICategory> {
        return sortedByDescending { it.userUsageCount }
            .sortedBy { it.addedToFileAt }
            .sortedByDescending { it.selectedState == SelectedState.SELECTED }
    }

    private fun List<UICategory>.sortedSearchCategories(): List<UICategory> {
        return sortedBy { it.name }
            .sortedByDescending { it.selectedState == SelectedState.SELECTED }
    }

    private fun List<UICategory>.toDiffUtilCategories(): List<DiffUtilCategory> {
        return map { DiffUtilCategory(it.id, it.selectedState) }
    }

    data class UICategory(
        val id: Int,
        var name: String,
        var color: String,
        val isPredefined: Boolean,
        var selectedState: SelectedState,
        val userUsageCount: Int,
        var addedToFileAt: Date?,
    )

    enum class SelectedState {
        SELECTED,
        NOT_SELECTED,
        PROCESSING,
    }

    private data class DiffUtilCategory(
        val id: Int,
        val selectedState: SelectedState,
    )

    private class CategoriesDiffCallback(
        private val oldList: List<DiffUtilCategory>,
        private val newList: List<DiffUtilCategory>,
        private val isCreateRowVisible: Boolean,
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            return oldList[oldIndex].id == newList[newIndex].id
        }

        override fun areContentsTheSame(oldIndex: Int, newIndex: Int): Boolean {
            return when {
                oldIndex == 0 && newIndex != 0 -> false // Remove top corners radius
                oldIndex != 0 && newIndex == 0 -> false // Add top Corners radius
                oldIndex == oldListSize - 1 && newIndex != newListSize - 1 -> false // Remove bot corners radius
                oldIndex != oldListSize - 1 && newIndex == newListSize - 1 -> false // Add bot corners radius
                oldList[oldIndex].selectedState != newList[newIndex].selectedState -> false // Update progress bar
                oldIndex == oldListSize - 1 && newIndex == newListSize - 1 && isCreateRowVisible -> false // Remove bot corners radius
                newIndex == newListSize - 1 && !isCreateRowVisible -> false // Add bot corners radius
                else -> true // Don't update
            }
        }
    }
}
