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
import android.os.CountDownTimer
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.GridLayoutManager
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.getScreenSizeInDp
import com.infomaniak.lib.core.utils.toDp
import kotlinx.android.synthetic.main.fragment_pictures.*
import kotlin.math.max
import kotlin.math.min

class PicturesFragment : Fragment() {

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var picturesAdapter: PicturesAdapter
    private val picturesViewModel: PicturesViewModel by viewModels()

    private lateinit var timer: CountDownTimer

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_pictures, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        timer = Utils.createRefreshTimer {
            swipeRefreshLayout?.isRefreshing = true
        }

        toolbar.setNavigationOnClickListener {
            findNavController().popBackStack()
        }

        noPicturesLayout.setup(
            icon = R.drawable.ic_images,
            title = R.string.picturesNoFile,
            initialListView = picturesRecyclerView
        )

        swipeRefreshLayout.setOnRefreshListener {
            picturesAdapter.clearPictures()
            getPictures()
        }

        picturesAdapter = PicturesAdapter { file ->
            Utils.displayFile(mainViewModel, findNavController(), file, picturesAdapter.pictureList)
        }

        timer.start()
        val gridLayoutManager = GridLayoutManager(requireContext(), 12)
        gridLayoutManager.spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
            override fun getSpanSize(position: Int): Int {
                return when {
                    picturesAdapter.getItems()[position] is String -> 12
                    else -> 4
                }
            }
        }
        picturesRecyclerView.layoutManager = gridLayoutManager
        picturesRecyclerView.adapter = picturesAdapter

        picturesAdapter.isComplete = false

        getPictures()
    }

    private fun getPictures() {
        val ignoreCloud = mainViewModel.isInternetAvailable.value == false
        picturesViewModel.cancelPicturesJob()
        picturesViewModel.getAllPicturesFiles(AccountUtils.currentDriveId, ignoreCloud).observe(viewLifecycleOwner) {
            it?.let { (pictures, isComplete) ->
                noPicturesLayout.toggleVisibility(pictures.isEmpty())
                val pictureList = picturesAdapter.formatList(pictures)
                if (picturesAdapter.itemCount == 0) picturesAdapter.setList(pictureList)
                else picturesRecyclerView.post { picturesAdapter.addAll(pictureList) }
                picturesAdapter.isComplete = isComplete
            } ?: run {
                picturesAdapter.isComplete = true
                noPicturesLayout.toggleVisibility(
                    noNetwork = ignoreCloud,
                    isVisible = true,
                    showRefreshButton = true
                )
            }
            timer.cancel()
            swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun getNumPicturesColumns(minColumns: Int = 2, maxColumns: Int = 5, expectedItemSize: Int = 300): Int {
        val screenWidth = requireActivity().getScreenSizeInDp().x
        return min(max(minColumns, screenWidth / expectedItemSize.toDp()), maxColumns)
    }
}
