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
package com.infomaniak.drive.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.lib.core.utils.setPagination
import kotlinx.android.synthetic.main.fragment_home_tabs.*

class HomePicturesFragment : Fragment() {
    private val homeViewModel: HomeViewModel by navGraphViewModels(R.id.homeFragment)
    private val mainViewModel: MainViewModel by activityViewModels()

    private var isDownloadingPictures = false

    private var paginationListener: RecyclerView.OnScrollListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home_tabs, container, false)
    }

    companion object {
        const val MAX_PICTURES_COLUMN = 2
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        homeTabsTitle.setText(R.string.homeMyLastPictures)
        initAdapter()
        AccountUtils.getCurrentDrive()?.let { currentDrive -> getPictures(currentDrive.id) }
    }

    private fun initAdapter() {
        homeTabsRecyclerView.apply {
            homeViewModel.lastPicturesPage = 1
            paginationListener?.let { removeOnScrollListener(it) }
            // Don't remove unnecessary parentheses from function call with lambda,
            // else "kotlin.UninitializedPropertyAccessException: lateinit property lastElementsAdapter has not been initialized"
            val homePicturesAdapter = HomePicturesAdapter() { file ->
                val pictures = (adapter as HomePicturesAdapter).itemList
                Utils.displayFile(mainViewModel, findNavController(), file, pictures)
            }
            homePicturesAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
            layoutManager = StaggeredGridLayoutManager(
                MAX_PICTURES_COLUMN,
                StaggeredGridLayoutManager.VERTICAL
            )
            adapter = homePicturesAdapter

            paginationListener = setPagination(
                whenLoadMoreIsPossible = {
                    if (!homePicturesAdapter.isComplete && !isDownloadingPictures) {
                        homeViewModel.lastPicturesPage++
                        homeViewModel.lastPicturesLastPage++
                        AccountUtils.getCurrentDrive()?.let { currentDrive -> getPictures(currentDrive.id) }
                    }
                }, findFirstVisibleItemPosition = {
                    val positions = IntArray(MAX_PICTURES_COLUMN)
                    (layoutManager as StaggeredGridLayoutManager).findFirstVisibleItemPositions(positions)
                    positions.first()
                })
        }
    }

    fun getPictures(driveId: Int, forceDownload: Boolean = false) {
        (homeTabsRecyclerView?.adapter as? HomePicturesAdapter)?.apply {
            isComplete = false
            isDownloadingPictures = true
            homeViewModel.getLastPictures(driveId, forceDownload).observe(viewLifecycleOwner) { apiResponse ->
                stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                apiResponse?.data?.let { lastPictures ->
                    addAll(lastPictures)
                    isComplete = lastPictures.size < ApiRepository.PER_PAGE
                } ?: also { isComplete = true }
                isDownloadingPictures = false
            }
        }
    }

}