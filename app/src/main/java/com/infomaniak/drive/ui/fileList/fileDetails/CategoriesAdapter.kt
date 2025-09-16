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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.graphics.toColorInt
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView.Adapter
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import com.google.android.material.card.MaterialCardView
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.CardviewCategoryBinding
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter.CategoriesViewHolder
import com.infomaniak.drive.utils.setCornersRadius
import java.util.Date

class CategoriesAdapter(private val onCategoryChanged: (id: Int, isSelected: Boolean) -> Unit) : Adapter<CategoriesViewHolder>() {

    var canEditCategory: Boolean = false
    var canDeleteCategory: Boolean = false
    var isCreateRowVisible: Boolean = false
    var onMenuClicked: ((category: UiCategory) -> Unit)? = null
    var allCategories: List<UiCategory> = listOf()
    var filteredCategories: List<UiCategory> = listOf()
    private var filterQuery: String = ""

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CategoriesViewHolder {
        return CategoriesViewHolder(CardviewCategoryBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun getItemCount() = filteredCategories.size

    override fun onBindViewHolder(holder: CategoriesViewHolder, position: Int) = with(holder.binding) {
        val category = filteredCategories[position]
        categoryCard.apply {
            isCheckable = false
            isEnabled = true
            setCorners(position)
            setMenuButton(category)
            handleSelectedState(category)
            setClickOnCategory(category)
        }

        categoryIcon.setBackgroundColor(category.color.toColorInt())
        categoryTitle.text = category.name
    }

    private fun MaterialCardView.setCorners(position: Int) {
        val radius = resources.getDimension(R.dimen.cardViewRadius)
        val topCornerRadius = if (position == 0) radius else 0.0f
        val bottomCornerRadius = if (position == filteredCategories.lastIndex && !isCreateRowVisible) radius else 0.0f
        setCornersRadius(topCornerRadius, bottomCornerRadius)
    }

    private fun CardviewCategoryBinding.setMenuButton(category: UiCategory) {
        menuButton.apply {
            isVisible = canEditCategory || (canDeleteCategory && !category.isPredefined)
            setOnClickListener { onMenuClicked?.invoke(category) }
        }
    }

    private fun CardviewCategoryBinding.handleSelectedState(category: UiCategory) {
        category.selectedState.let {
            categoryProgressBar.isVisible = it == SelectedState.PROCESSING
            checkIcon.isVisible = it == SelectedState.SELECTED
        }
    }

    private fun CardviewCategoryBinding.setClickOnCategory(category: UiCategory) = with(categoryCard) {
        setOnClickListener {
            isEnabled = false
            categoryProgressBar.isVisible = true
            checkIcon.isGone = true
            val isSelected = category.selectedState == SelectedState.NOT_SELECTED
            category.selectedState = SelectedState.PROCESSING
            onCategoryChanged(category.id, isSelected)
        }
    }

    fun setItems(categories: List<UiCategory>, usageMode: CategoriesUsageMode) {
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

    fun selectCategory(categoryId: Int, isSelected: Boolean, usageMode: CategoriesUsageMode) {
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

    private fun List<UiCategory>.filtered(): List<UiCategory> = filter { it.name.contains(filterQuery, true) }

    private fun List<UiCategory>.sorted(usageMode: CategoriesUsageMode): List<UiCategory> {
        return if (usageMode == CategoriesUsageMode.SELECTED_CATEGORIES) sortedSearchCategories() else sortedFileCategories()
    }

    private fun List<UiCategory>.sortedFileCategories(): List<UiCategory> {
        return sortedByDescending { it.userUsageCount }
            .sortedBy { it.addedToFileAt }
            .sortedByDescending { it.selectedState == SelectedState.SELECTED }
    }

    private fun List<UiCategory>.sortedSearchCategories(): List<UiCategory> {
        return sortedBy { it.name }
            .sortedByDescending { it.selectedState == SelectedState.SELECTED }
    }

    private fun List<UiCategory>.toDiffUtilCategories(): List<DiffUtilCategory> {
        return map { DiffUtilCategory(it.id, it.selectedState) }
    }

    data class UiCategory(
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
                oldIndex == oldList.lastIndex && newIndex != newList.lastIndex -> false // Remove bot corners radius
                oldIndex != oldList.lastIndex && newIndex == newList.lastIndex -> false // Add bot corners radius
                oldList[oldIndex].selectedState != newList[newIndex].selectedState -> false // Update progress bar
                oldIndex == oldList.lastIndex && newIndex == newList.lastIndex && isCreateRowVisible -> false // Remove bot corners radius
                newIndex == newList.lastIndex && !isCreateRowVisible -> false // Add bot corners radius
                else -> true // Don't update
            }
        }
    }

    class CategoriesViewHolder(val binding: CardviewCategoryBinding) : ViewHolder(binding.root)
}
