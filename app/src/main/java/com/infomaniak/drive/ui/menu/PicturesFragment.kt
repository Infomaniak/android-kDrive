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

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.getScreenSizeInDp
import com.infomaniak.lib.core.utils.toDp
import io.realm.RealmList
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.fragment_pictures.*
import kotlin.math.max
import kotlin.math.min

class PicturesFragment(
    private val onFinish: (() -> Unit)? = null,
) : Fragment(), MultiSelectListener {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val picturesViewModel: PicturesViewModel by viewModels()
    private val picturesAdapter: PicturesAdapter by lazy {
        PicturesAdapter { file ->
            Utils.displayFile(mainViewModel, findNavController(), file, picturesAdapter.pictureList)
        }
    }

    private var folderID = Utils.ROOT_ID
    private var enabledMultiSelectMode = true
    private var multiSelectParent: MultiSelectParent? = null

    fun init(multiSelectParent: MultiSelectParent) {
        this.multiSelectParent = multiSelectParent
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pictures, container, false)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        configPicturesLayoutManager()
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        noPicturesLayout.setup(
            icon = R.drawable.ic_images,
            title = R.string.picturesNoFile,
            initialListView = picturesRecyclerView
        )

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            if (picturesAdapter.multiSelectMode) {
                closeMultiSelect()
            } else {
                findNavController().popBackStack()
            }
        }

        picturesAdapter.apply {
            numberItemLoader = PicturesFragment.numberItemLoader
            stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

            openMultiSelectMode = { openMultiSelect() }
            updateMultiSelectMode = { onUpdateMultiSelect() }
        }

        if (enabledMultiSelectMode) setupMultiSelect()

        picturesRecyclerView.adapter = picturesAdapter
        configPicturesLayoutManager()

        getPictures()
    }

    private fun closeMultiSelect() {
        picturesAdapter.apply {
            itemsSelected = RealmList()
            multiSelectMode = false
            allSelected = false
            notifyItemRangeChanged(0, itemCount)
        }

        multiSelectParent?.closeMultiSelectBar()
    }

    private fun setupMultiSelect() {
        picturesAdapter.enabledMultiSelectMode = true
    }

    private fun onUpdateMultiSelect(selectedNumber: Int? = null) {
        val fileSelectedNumber = selectedNumber ?: picturesAdapter.getValidItemsSelected().size
        when (fileSelectedNumber) {
            0, 1 -> {
                val isEnabled = fileSelectedNumber == 1
                enableButtonMultiSelect(isEnabled)
            }
        }
        multiSelectParent?.setTitleMultiSelect(
            resources.getQuantityString(
                R.plurals.fileListMultiSelectedTitle,
                fileSelectedNumber,
                fileSelectedNumber
            )
        )
//        multiSelectParent?.hideSelectAllProgress(if (picturesAdapter.allSelected) R.string.buttonDeselectAll else R.string.buttonSelectAll)
    }

    private fun enableButtonMultiSelect(isEnabled: Boolean) {
        if (isEnabled) multiSelectParent?.enableMultiSelectActionButtons()
        else multiSelectParent?.disableMultiSelectActionButtons()
    }

    private fun openMultiSelect() {
        picturesAdapter.multiSelectMode = true
        picturesAdapter.notifyItemRangeChanged(0, picturesAdapter.itemCount)
        multiSelectParent?.openMultiSelectBar()
    }

    private fun configPicturesLayoutManager() {
        val numPicturesColumns = getNumPicturesColumns()
        val gridLayoutManager = GridLayoutManager(requireContext(), numPicturesColumns)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when {
                    picturesAdapter.itemList.getOrNull(position) is String -> numPicturesColumns
                    else -> 1
                }
            }
        }
        picturesRecyclerView.layoutManager = gridLayoutManager
    }

    fun reloadPictures() {
        if (isResumed) {
            picturesAdapter.clearPictures()
            getPictures()
        }
    }

    private fun getPictures() {
        picturesAdapter.apply {
            val ignoreCloud = mainViewModel.isInternetAvailable.value == false
            showLoading()
            isComplete = false
            picturesViewModel.getAllPicturesFiles(AccountUtils.currentDriveId, ignoreCloud).observe(viewLifecycleOwner) {
                it?.let { (pictures, isComplete) ->
                    stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                    val pictureList = formatList(requireContext(), pictures)
                    picturesRecyclerView.post { addAll(pictureList) }
                    this.isComplete = isComplete
                    noPicturesLayout.toggleVisibility(pictureList.isEmpty())
                } ?: run {
                    isComplete = true
                    noPicturesLayout.toggleVisibility(
                        noNetwork = ignoreCloud,
                        isVisible = pictureList.isEmpty(),
                        showRefreshButton = true
                    )
                }
                onFinish?.invoke()
            }
        }
    }

    private fun getNumPicturesColumns(minColumns: Int = 2, maxColumns: Int = 5, expectedItemSize: Int = 300): Int {
        val screenWidth = requireActivity().getScreenSizeInDp().x
        return min(max(minColumns, screenWidth / expectedItemSize.toDp()), maxColumns)
    }


    companion object {
        private const val numberItemLoader = 12
    }

    override fun onCloseMultiSelection() {
        closeMultiSelect()
    }

    override fun onMove() {
        Utils.moveFileClicked(this, folderID)
    }

    override fun onDelete() {
        TODO("Not yet implemented")
    }

    override fun onMenu() {
        TODO("Not yet implemented")
    }
}

interface MultiSelectListener {
    fun onCloseMultiSelection()
    fun onMove()
    fun onDelete()
    fun onMenu()
}

interface MultiSelectParent {
    fun openMultiSelectBar()
    fun closeMultiSelectBar()
    fun enableMultiSelectActionButtons()
    fun disableMultiSelectActionButtons()
    fun setTitleMultiSelect(title: String)
}