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

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.viewModels
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.ui.fileList.fileDetails.CategoriesAdapter
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.getName
import com.infomaniak.drive.utils.setBackNavigationResult
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.lib.core.models.ApiResponse
import kotlinx.android.synthetic.main.fragment_select_categories.*
import kotlinx.coroutines.Dispatchers
import java.util.*


data class UICategory(
    val id: Int,
    val name: String,
    val color: String,
    var isSelected: Boolean
)

class SelectCategoriesBottomSheetDialog : FullScreenBottomSheetDialog() {

    private lateinit var adapter: CategoriesAdapter
    private val navigationArgs: SelectCategoriesBottomSheetDialogArgs by navArgs()
    private val selectCategoriesViewModel: SelectCategoriesViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? =
        inflater.inflate(R.layout.fragment_select_categories, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fun setBackNavResult() {
            setBackNavigationResult(
                SELECT_CATEGORIES_NAV_KEY,
                bundleOf(CATEGORIES_BUNDLE_KEY to adapter.categories.filter { it.isSelected })
            )
        }

        toolbar.setNavigationOnClickListener { setBackNavResult() }

        dialog?.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event?.action == KeyEvent.ACTION_UP) {
                setBackNavResult()
                true
            } else false
        }

        val file = FileController.getFileById(navigationArgs.fileId)
        val allCategories = DriveInfosController.getCategories()
        val enabledCategories = DriveInfosController.getCategories(navigationArgs.categoriesIds.toTypedArray())

        val uiCategories = allCategories.map { category ->
            UICategory(
                category.id,
                category.getName(requireContext()),
                category.color,
                enabledCategories.find { it.id == category.id } != null
            )
        }

        selectCategoriesViewModel.file = file!!

        adapter = CategoriesAdapter(onCategoryChanged = { id, isSelected, position ->
            (if (isSelected) selectCategoriesViewModel.addCategory(id)
            else selectCategoriesViewModel.removeCategory(id))
                .observe(viewLifecycleOwner) { apiResponse ->
                    if (apiResponse.isSuccess()) {
                        val customRealm = FileController.getRealmInstance()
                        FileController.updateFile(file.id, customRealm) { localFile ->
                            if (isSelected) {
                                localFile.categories.add(
                                    FileCategory(
                                        id = id,
                                        userId = AccountUtils.currentUserId,
                                        addedToFileAt = Date()
                                    )
                                )
                            } else {
                                val categories = localFile.categories
                                val category = categories.find { it.id == id }
                                categories.remove(category)
                            }
                        }
                        adapter.categories.find { it.id == id }?.isSelected = isSelected
                        adapter.notifyItemChanged(position)
                    } else {
                        Utils.showSnackbar(requireView(), R.string.errorNetwork)
                    }
                }
        })

        categoriesRecyclerView.adapter = adapter.apply {
            setAll(uiCategories)
        }
    }

    internal class SelectCategoriesViewModel : ViewModel() {

        lateinit var file: File

        fun addCategory(id: Int): LiveData<ApiResponse<Unit>> = liveData(Dispatchers.IO) {
            emit(ApiRepository.addCategory(file, mapOf("id" to id)))
        }

        fun removeCategory(id: Int): LiveData<ApiResponse<Unit>> = liveData(Dispatchers.IO) {
            emit(ApiRepository.removeCategory(file, id))
        }
    }

    companion object {
        const val SELECT_CATEGORIES_NAV_KEY = "categories_dialog_key"
        const val CATEGORIES_BUNDLE_KEY = "categories_bundle_key"
    }
}
