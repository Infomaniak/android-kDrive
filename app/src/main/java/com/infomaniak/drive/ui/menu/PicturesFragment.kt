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
package com.infomaniak.drive.ui.menu

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
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
import kotlinx.android.synthetic.main.fragment_pictures.*
import kotlin.math.max
import kotlin.math.min

class PicturesFragment(
    private val onFinish: (() -> Unit)? = null
) : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private val picturesViewModel: PicturesViewModel by viewModels()
    private val picturesAdapter: PicturesAdapter by lazy {
        PicturesAdapter { file ->
            Utils.displayFile(mainViewModel, findNavController(), file, picturesAdapter.pictureList)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pictures, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        noPicturesLayout.setup(
            icon = R.drawable.ic_images,
            title = R.string.picturesNoFile,
            initialListView = picturesRecyclerView
        )

        picturesAdapter.numberItemLoader = numberItemLoader
        picturesAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT

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
        picturesRecyclerView.adapter = picturesAdapter

        getPictures()
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
}
