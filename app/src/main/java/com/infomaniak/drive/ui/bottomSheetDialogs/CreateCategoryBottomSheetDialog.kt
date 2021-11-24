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

class CreateCategoryBottomSheetDialog : FullScreenBottomSheetDialog() {

    private val navigationArgs: CreateCategoryBottomSheetDialogArgs by navArgs()

    private val createCategoryViewModel: CreateCategoryViewModel by viewModels()
    private val selectCategoriesViewModel: SelectCategoriesBottomSheetDialog.SelectCategoriesViewModel by viewModels()

    private val colorLayouts = mutableListOf<ConstraintLayout>()
    private var selected: Int = -1

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_create_category, container, false)

    @SuppressLint("InflateParams")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        saveButton.setOnClickListener {
            saveButton.showProgress()

            val name = categoryName.text.toString()
            val color = CATEGORY_COLORS[selected]

            createCategoryViewModel.createCategory(navigationArgs.driveId, name, color)
                .observe(viewLifecycleOwner) { createCategoryApiResponse ->

                    if (createCategoryApiResponse.isSuccess()) {

                        val categoryId = createCategoryApiResponse.data?.id ?: -1

                        selectCategoriesViewModel.addCategory(navigationArgs.fileId, navigationArgs.driveId, categoryId)
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

        categoryName.addTextChangedListener { saveButton.isEnabled = it.toString().isNotEmpty() }

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

        // Select first element
        onColorClicked(0)
    }

    private fun onColorClicked(pos: Int) {
        if (pos != selected) {
            colorLayouts.getOrNull(selected)?.shapeableImageIcon?.isGone = true
            colorLayouts.getOrNull(pos)?.let {
                it.shapeableImageIcon.isVisible = true
                selected = pos
            }
        }
    }

    internal class CreateCategoryViewModel : ViewModel() {

        fun createCategory(driveId: Int, name: String, color: String): LiveData<ApiResponse<Category>> =
            liveData(Dispatchers.IO) {

                val apiResponse = ApiRepository.createCategory(driveId, name, color)

                if (apiResponse.isSuccess()) {
                    DriveInfosController.updateDrive { localDrive ->
                        localDrive.categories.add(
                            apiResponse.data?.apply {
                                objectId = "${driveId}_$id"
                            }
                        )
                    }
                }

                emit(apiResponse)
            }
    }

    companion object {
        const val CREATE_CATEGORY_NAV_KEY = "create_category_nav_key"
        const val CATEGORY_ID_BUNDLE_KEY = "category_id_bundle_key"
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
