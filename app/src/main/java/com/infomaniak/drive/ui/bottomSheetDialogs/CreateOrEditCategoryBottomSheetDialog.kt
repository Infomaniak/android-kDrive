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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.core.view.isGone
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.api.ErrorCode.Companion.translateError
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.ui.bottomSheetDialogs.CreateOrEditCategoryAdapter.Companion.COLORS
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.getScreenSizeInDp
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.models.ApiResponse
import com.infomaniak.lib.core.utils.hideProgress
import com.infomaniak.lib.core.utils.initProgress
import com.infomaniak.lib.core.utils.showProgress
import com.infomaniak.lib.core.utils.toDp
import kotlinx.android.synthetic.main.fragment_create_category.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlin.math.max

class CreateOrEditCategoryBottomSheetDialog : FullScreenBottomSheetDialog() {

    private val navigationArgs: CreateOrEditCategoryBottomSheetDialogArgs by navArgs()
    private val createOrEditCategoryViewModel: CreateOrEditCategoryViewModel by viewModels()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()
    private val colorsAdapter: CreateOrEditCategoryAdapter by lazy { CreateOrEditCategoryAdapter() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_create_category, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        configureAdapter()
        configureUI()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configCategoriesLayoutManager()
    }

    private fun configureAdapter() {
        with(colorsAdapter) {
            selectedPosition = COLORS.indexOfFirst { it == navigationArgs.categoryColor }.let { if (it == -1) 0 else it }
            configCategoriesLayoutManager()
            categoriesRecyclerView.adapter = this
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
        }
    }

    private fun configCategoriesLayoutManager() {
        val numCategoriesColumns = getNumColorsColumns()
        val gridLayoutManager = GridLayoutManager(context, numCategoriesColumns)
        categoriesRecyclerView.layoutManager = gridLayoutManager
    }

    private fun getNumColorsColumns(minColumns: Int = 1, expectedItemSize: Int = 52): Int {
        val screenWidth = requireActivity().getScreenSizeInDp().x
        val margins = resources.getDimensionPixelSize(R.dimen.marginStandardSmall).toDp() * 2
        return max(minColumns, (screenWidth - margins) / expectedItemSize)
    }

    private fun configureUI() = with(navigationArgs) {
        toolbar.setNavigationOnClickListener { findNavController().popBackStack() }

        val isCreateCategory = categoryId == CREATE_CATEGORY_ID

        appBarTitle.title = getString(if (isCreateCategory) R.string.createCategoryTitle else R.string.editCategoryTitle)
        editCategoryWarning.isGone = isCreateCategory
        with(categoryNameValueInput) {
            addTextChangedListener { saveButton.isEnabled = it.toString().isNotEmpty() }
            setText(categoryName)
        }
        categoryNameValueLayout.isGone = categoryIsPredefined

        with(saveButton) {
            isEnabled = categoryIsPredefined || !isCreateCategory || categoryName.isNotEmpty()
            initProgress(this@CreateOrEditCategoryBottomSheetDialog)
            setOnClickListener {
                showProgress()
                if (categoryId == CREATE_CATEGORY_ID) createCategory() else editCategory(categoryId)
            }
        }
    }

    private fun createCategory() {
        val name = categoryNameValueInput.text.toString()
        val color = COLORS[colorsAdapter.selectedPosition]
        createOrEditCategoryViewModel.createCategory(navigationArgs.driveId, name, color)
            .observe(viewLifecycleOwner) { apiResponse ->
                with(apiResponse) {
                    if (isSuccess()) {
                        data?.id?.let { addCategory(it, name, color) }
                    } else {
                        saveButton.hideProgress(R.string.buttonSave)
                        Utils.showSnackbar(requireView(), translateError())
                    }
                }
            }
    }

    private fun addCategory(categoryId: Int, name: String, color: String) {
        selectCategoriesViewModel.addCategory(navigationArgs.fileId, navigationArgs.driveId, categoryId)
            .observe(viewLifecycleOwner) { apiResponse ->
                with(apiResponse) {
                    if (isSuccess()) {
                        setBackNavigationResult(
                            CREATE_CATEGORY_NAV_KEY, bundleOf(
                                CATEGORY_ID_BUNDLE_KEY to categoryId,
                                CATEGORY_NAME_BUNDLE_KEY to name,
                                CATEGORY_COLOR_BUNDLE_KEY to color,
                            )
                        )
                    } else {
                        saveButton.hideProgress(R.string.buttonSave)
                        Utils.showSnackbar(requireView(), translateError())
                    }
                }
            }
    }

    private fun editCategory(categoryId: Int) {
        val name = categoryNameValueInput.text.toString().let { if (it.isEmpty()) null else it }
        val color = COLORS[colorsAdapter.selectedPosition]
        val driveId = navigationArgs.driveId
        createOrEditCategoryViewModel.editCategory(driveId, categoryId, name, color).observe(viewLifecycleOwner) { apiResponse ->
            with(apiResponse) {
                if (isSuccess()) {
                    setBackNavigationResult(
                        EDIT_CATEGORY_NAV_KEY, bundleOf(
                            CATEGORY_ID_BUNDLE_KEY to categoryId,
                            CATEGORY_NAME_BUNDLE_KEY to name,
                            CATEGORY_COLOR_BUNDLE_KEY to color,
                        )
                    )
                } else {
                    saveButton.hideProgress(R.string.buttonSave)
                    Utils.showSnackbar(requireView(), translateError())
                }
            }
        }
    }

    internal class CreateOrEditCategoryViewModel : ViewModel() {

        private var createCategoryJob = Job()
        private var editCategoryJob = Job()

        fun createCategory(driveId: Int, name: String, color: String): LiveData<ApiResponse<Category>> {
            createCategoryJob.cancel()
            createCategoryJob = Job()
            return liveData(Dispatchers.IO + createCategoryJob) {
                with(ApiRepository.createCategory(driveId, name, color)) {
                    if (isSuccess()) DriveInfosController.updateDrive { it.categories.add(data) }
                    emit(this)
                }
            }
        }

        fun editCategory(driveId: Int, categoryId: Int, name: String?, color: String): LiveData<ApiResponse<Category>> {
            editCategoryJob.cancel()
            editCategoryJob = Job()
            return liveData(Dispatchers.IO + editCategoryJob) {
                with(ApiRepository.editCategory(driveId, categoryId, name, color)) {
                    if (isSuccess()) {
                        DriveInfosController.updateDrive { localDrive ->
                            // Delete previous Category
                            val category = localDrive.categories.find { it.id == categoryId }
                            localDrive.categories.remove(category)
                            // Add new Category
                            localDrive.categories.add(data)
                        }
                    }
                    emit(this)
                }
            }
        }

        override fun onCleared() {
            super.onCleared()
            createCategoryJob.cancel()
            editCategoryJob.cancel()
        }
    }

    companion object {
        const val CREATE_CATEGORY_NAV_KEY = "create_category_nav_key"
        const val EDIT_CATEGORY_NAV_KEY = "edit_category_nav_key"
        const val CATEGORY_ID_BUNDLE_KEY = "category_id_bundle_key"
        const val CATEGORY_NAME_BUNDLE_KEY = "category_name_bundle_key"
        const val CATEGORY_COLOR_BUNDLE_KEY = "category_color_bundle_key"
        const val CREATE_CATEGORY_ID = -1
    }
}
