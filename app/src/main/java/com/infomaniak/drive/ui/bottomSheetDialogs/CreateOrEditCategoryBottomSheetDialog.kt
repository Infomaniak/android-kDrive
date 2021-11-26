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
package com.infomaniak.drive.ui.bottomSheetDialogs

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.dpToPx
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.drive.utils.setMargin
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.showProgress
import kotlinx.android.synthetic.main.fragment_create_category.*
import kotlinx.android.synthetic.main.view_shapeable_image.view.*
import kotlinx.coroutines.Dispatchers

class CreateOrEditCategoryBottomSheetDialog : FullScreenBottomSheetDialog() {

    private val navigationArgs: CreateOrEditCategoryBottomSheetDialogArgs by navArgs()

    private val createOrEditCategoryViewModel: CreateOrEditCategoryViewModel by viewModels()
    private val selectCategoriesViewModel: SelectCategoriesBottomSheetDialog.SelectCategoriesViewModel by viewModels()

    private val colorLayouts = mutableListOf<ConstraintLayout>()
    private var selected: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_create_category, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editCategoryWarningText.isGone = navigationArgs.categoryId == NO_PREVIOUS_CATEGORY_ID

        categoryName.isGone = navigationArgs.categoryIsPredefined
        saveButton.isEnabled = navigationArgs.categoryIsPredefined

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        categoryName.addTextChangedListener { saveButton.isEnabled = it.toString().isNotEmpty() }

        val previousCategoryName = navigationArgs.categoryName
        if (previousCategoryName != null) {
            categoryName.setText(previousCategoryName)
        }

        configureSaveButton()

        configureColorsGrid()

        // Select first element
        val previousColor = navigationArgs.categoryColor
        val previousColorPosition = CATEGORY_COLORS.indexOfFirst { it == previousColor }
        val position =
            if (previousColor == null || previousColorPosition == -1) 0
            else previousColorPosition
        onColorClicked(position)
    }

    private fun configureSaveButton() {
        saveButton.setOnClickListener {
            saveButton.showProgress()

            val previousCategoryId = navigationArgs.categoryId
            if (previousCategoryId == NO_PREVIOUS_CATEGORY_ID) {
                createCategory() // Create a new Category
            } else {
                editCategory(previousCategoryId) // Edit an existing Category
            }
        }

    }

    private fun createCategory() {

        val name = categoryName.text.toString()
        val color = CATEGORY_COLORS[selected]
        val driveId = navigationArgs.driveId

        createOrEditCategoryViewModel.createCategory(driveId, name, color)
            .observe(viewLifecycleOwner) { createCategoryApiResponse ->

                if (createCategoryApiResponse.isSuccess()) {

                    val categoryId = createCategoryApiResponse.data?.id ?: -1

                    selectCategoriesViewModel.addCategory(navigationArgs.fileId, driveId, categoryId)
                        .observe(viewLifecycleOwner) { addCategoryApiResponse ->

                            if (addCategoryApiResponse.isSuccess()) {
                                setBackNavigationResult(
                                    CREATE_CATEGORY_NAV_KEY,
                                    bundleOf(CATEGORY_ID_BUNDLE_KEY to categoryId)
                                )

                            } else {
                                saveButton.hideProgress(R.string.buttonSave)
                                Utils.showSnackbar(requireView(), addCategoryApiResponse.translateError())
                            }
                        }

                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    Utils.showSnackbar(requireView(), createCategoryApiResponse.translateError())
                }
            }
    }

    private fun editCategory(previousCategoryId: Int) {

        val name = categoryName.text.toString().let { if (it.isEmpty()) null else it }
        val color = CATEGORY_COLORS[selected]
        val driveId = navigationArgs.driveId

        createOrEditCategoryViewModel.editCategory(driveId, previousCategoryId, name, color)
            .observe(viewLifecycleOwner) { apiResponse ->

                if (apiResponse.isSuccess()) {
                    setBackNavigationResult(EDIT_CATEGORY_NAV_KEY, bundleOf())

                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    Utils.showSnackbar(requireView(), apiResponse.translateError())
                }
            }
    }

    @SuppressLint("InflateParams")
    private fun configureColorsGrid() {

        val ctx = requireContext()

        CATEGORY_COLORS.forEach { color ->

            // Create view
            val colorLayout = LayoutInflater.from(ctx)
                .inflate(R.layout.view_shapeable_image, null, false) as ConstraintLayout

            // Set layout size
            val size = 36.dpToPx(ctx)
            colorLayout.layoutParams = ViewGroup.MarginLayoutParams(size, size)

            // Set layout margins
            val margin = 8.dpToPx(ctx)
            colorLayout.setMargin(margin, margin, margin, margin)

            // Set background color
            colorLayout.shapeableImageBackground.setBackgroundColor(Color.parseColor(color))

            // Set click
            val pos = colorLayouts.size
            colorLayout.setOnClickListener { onColorClicked(pos) }

            // Add view
            colorLayouts.add(colorLayout)
            categoryColorsView.addView(colorLayout)
        }
    }

    private fun onColorClicked(position: Int) {
        if (position != selected) {
            colorLayouts.getOrNull(selected)?.shapeableImageIcon?.isGone = true
            colorLayouts.getOrNull(position)?.let {
                it.shapeableImageIcon.isVisible = true
                selected = position
            }
        }
    }

    internal class CreateOrEditCategoryViewModel : ViewModel() {

        fun createCategory(driveId: Int, name: String, color: String): LiveData<ApiResponse<Category>> =
            liveData(Dispatchers.IO) {

                val apiResponse = ApiRepository.createCategory(driveId, name, color)

                if (apiResponse.isSuccess()) {
                    DriveInfosController.updateDrive { localDrive ->
                        localDrive.categories.add(apiResponse.data)
                    }
                }

                emit(apiResponse)
            }

        fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): LiveData<ApiResponse<Category>> =
            liveData(Dispatchers.IO) {

                val apiResponse = ApiRepository.editCategory(driveId, categoryId, name, color)

                if (apiResponse.isSuccess()) {
                    DriveInfosController.updateDrive { localDrive ->

                        // Delete previous Category
                        val category = localDrive.categories.find { it.id == categoryId }
                        localDrive.categories.remove(category)

                        // Add new Category
                        localDrive.categories.add(apiResponse.data)

                    }
                }

                emit(apiResponse)
            }
    }

    companion object {
        const val CREATE_CATEGORY_NAV_KEY = "create_category_nav_key"
        const val CATEGORY_ID_BUNDLE_KEY = "category_id_bundle_key"

        const val EDIT_CATEGORY_NAV_KEY = "edit_category_nav_key"

        const val NO_PREVIOUS_CATEGORY_ID = -1

        private val CATEGORY_COLORS = listOf(
            "#1ABC9C",
            "#11806A",
            "#2ECC71",
            "#128040",
            "#3498DB",
            "#206694",
            "#9B59B6",
            "#71368A",
            "#E91E63",
            "#AD1457",
            "#F1C40F",
            "#C27C0E",
            "#C45911",
            "#44546A",
            "#E74C3C",
            "#992D22",
            "#9D00FF",
            "#00B0F0",
            "#BE8F00",
            "#0B4899",
            "#009945",
            "#2E77B5",
            "#70AD47",
        )
    }
}
