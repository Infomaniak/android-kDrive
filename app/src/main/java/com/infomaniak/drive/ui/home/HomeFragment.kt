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

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.setPagination
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.item_search_view.*

class HomeFragment : Fragment() {
    private lateinit var lastElementsAdapter: RecyclerView.Adapter<ViewHolder>
    private lateinit var lastFilesAdapter: LastFilesAdapter
    private val arguments: HomeFragmentArgs by navArgs()
    private val homeViewModel: HomeViewModel by navGraphViewModels(R.id.homeFragment)
    private val mainViewModel: MainViewModel by activityViewModels()
    private var isProOrTeam: Boolean = false

    private var isDownloadingActivities = false
    private var isDownloadingPictures = false

    private var paginationListener: RecyclerView.OnScrollListener? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    companion object {
        const val MERGE_FILE_ACTIVITY_DELAY = 3600 * 12000 // 12h (ms)
        const val MAX_PICTURES_COLUMN = 2
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        lastFilesRecyclerView.apply {
            layoutManager = object : LinearLayoutManager(requireContext(), HORIZONTAL, false) {
                override fun checkLayoutParams(layoutParams: RecyclerView.LayoutParams): Boolean {
                    layoutParams.width = (width * 0.4f).toInt()
                    return true
                }
            }

            lastFilesAdapter = LastFilesAdapter()
            adapter = lastFilesAdapter
        }

        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            setDriveHeader(currentDrive)
        }

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            noNetworkCard.visibility = if (isInternetAvailable) GONE else VISIBLE
        }

        homeViewModel.driveSelectionDialogDismissed.observe(viewLifecycleOwner) { newDriveIsSelected ->
            if (newDriveIsSelected == true) {
                homeViewModel.clearDownloadTimes()
                initLastElementsAdapter() // Re-init the adapters if Drive type has changed between two Drives
                updateDriveInfos(newDriveIsSelected)
            }
        }

        driveInfos.setOnClickListener {
            safeNavigate(R.id.switchDriveDialog)
        }

        searchView.visibility = GONE
        searchViewText.visibility = VISIBLE
        ViewCompat.requestApplyInsets(homeCoordinator)

        searchViewCard.setOnClickListener {
            safeNavigate(HomeFragmentDirections.actionHomeFragmentToSearchFragment())
        }

        if (arguments.forceDriveSelection && savedInstanceState == null) {
            driveInfos.performClick()
        }
    }

    override fun onResume() {
        super.onResume()
        if (lastElementsRecyclerView.adapter == null) initLastElementsAdapter()
        updateDriveInfos()
    }

    // TODO - Use same fragment with PicturesAdapter and LastPictures
    private fun initLastElementsAdapter() {
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            lastElementsRecyclerView.apply {
                homeViewModel.lastActivityPage = 1
                homeViewModel.lastPicturesPage = 1
                paginationListener?.let { removeOnScrollListener(it) }
                isProOrTeam = currentDrive.pack == Drive.DrivePack.PRO.value || currentDrive.pack == Drive.DrivePack.TEAM.value
                lastElementsAdapter = if (isProOrTeam) LastActivitiesAdapter() else HomePicturesAdapter { file ->
                    val pictures = (lastElementsAdapter as HomePicturesAdapter).getItems()
                    Utils.displayFile(mainViewModel, findNavController(), file, pictures)
                }
                lastElementsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT
                layoutManager = if (isProOrTeam) LinearLayoutManager(requireContext()) else StaggeredGridLayoutManager(
                    MAX_PICTURES_COLUMN,
                    StaggeredGridLayoutManager.VERTICAL
                )
                adapter = lastElementsAdapter

                if (isProOrTeam) {
                    paginationListener = setPagination({
                        val lastActivitiesAdapter = lastElementsAdapter as LastActivitiesAdapter
                        if (!lastActivitiesAdapter.isComplete && !isDownloadingActivities) {
                            lastActivitiesAdapter.showLoading()
                            getLastPicturesOrActivities(currentDrive.id, isProOrTeam, false)
                        }
                    })

                    (lastElementsAdapter as LastActivitiesAdapter).apply {
                        onMoreFilesClicked = { fileActivity, validPreviewFiles ->
                            safeNavigate(
                                HomeFragmentDirections.actionHomeFragmentToActivityFilesFragment(
                                    fileIdList = validPreviewFiles.map { file -> file.id }.toIntArray(),
                                    activityUser = fileActivity.user,
                                    activityTranslation = resources.getQuantityString(
                                        fileActivity.homeTranslation,
                                        validPreviewFiles.size,
                                        validPreviewFiles.size
                                    )
                                )
                            )
                        }
                        onFileClicked = { currentFile, validPreviewFiles ->
                            if (currentFile.isTrashed()) {
                                requireActivity().showSnackbar(
                                    getString(R.string.errorPreviewTrash),
                                    anchorView = requireActivity().mainFab
                                )
                            } else Utils.displayFile(mainViewModel, findNavController(), currentFile, validPreviewFiles)
                        }
                    }
                } else {
                    paginationListener = setPagination(
                        whenLoadMoreIsPossible = {
                            val picturesAdapter = lastElementsAdapter as HomePicturesAdapter
                            if (!picturesAdapter.isComplete && !isDownloadingPictures) {
                                getLastPicturesOrActivities(currentDrive.id, isProOrTeam, false)
                            }
                        }, findFirstVisibleItemPosition = {
                            val positions = IntArray(MAX_PICTURES_COLUMN)
                            (layoutManager as StaggeredGridLayoutManager).findFirstVisibleItemPositions(positions)
                            positions.first()
                        })
                }
            }
        }
    }

    private fun updateDriveInfos(forceClear: Boolean = false) {
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            setDriveHeader(currentDrive)
            getLastPicturesOrActivities(currentDrive.id, isProOrTeam, forceClear)
            getLastEditedFiles()
            notEnoughStorage.setup(currentDrive)
        }
    }

    private fun setDriveHeader(currentDrive: Drive) {
        driveName.text = currentDrive.name
        driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(currentDrive.preferences.color))
    }

    private fun setupLastElementsTitle(isProOrTeam: Boolean) {
        lastElementsTitle.apply {
            text = if (isProOrTeam) getString(R.string.homeLastActivities) else getString(R.string.homeMyLastPictures)
            visibility = VISIBLE
        }
    }

    private fun getLastPicturesOrActivities(driveId: Int, isProOrTeam: Boolean, forceClear: Boolean) {
        if (isProOrTeam) {
            getLastActivities(driveId, forceClear)
        } else {
            getPictures(driveId, forceClear)
        }
        setupLastElementsTitle(isProOrTeam)
    }

    private fun getLastActivities(driveId: Int, forceClear: Boolean) {
        (lastElementsAdapter as LastActivitiesAdapter).apply {
            isComplete = false
            isDownloadingActivities = true
            homeViewModel.getLastActivities(driveId).observe(viewLifecycleOwner) {
                lastElementsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                it?.let { (apiResponse, mergedActivities) ->
                    if (forceClear && apiResponse.page == 1 || apiResponse.page == 1) clean()
                    addAll(mergedActivities)
                    isComplete = (apiResponse.data?.size ?: 0) < ApiRepository.PER_PAGE
                } ?: also {
                    isComplete = true
                    addAll(arrayListOf())
                }
                isDownloadingActivities = false
            }
        }
    }

    private fun getPictures(driveId: Int, forceClear: Boolean) {
        val picturesAdapter = (lastElementsAdapter as HomePicturesAdapter)
        picturesAdapter.isComplete = false
        isDownloadingPictures = true
        homeViewModel.getLastPictures(driveId).observe(viewLifecycleOwner) {
            lastElementsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
            it?.data?.let { lastPictures ->
                if (forceClear && it.page == 1 || it.page == 1) picturesAdapter.setList(lastPictures)
                else picturesAdapter.addAll(lastPictures)
                picturesAdapter.isComplete = lastPictures.size < ApiRepository.PER_PAGE
            } ?: also { picturesAdapter.isComplete = true }
            isDownloadingPictures = false
        }
    }

    private fun getLastEditedFiles() {
        lastFilesAdapter.clean()
        lastFilesAdapter.showLoading()

        lastFilesAdapter.onFileClicked = { file ->
            Utils.displayFile(mainViewModel, findNavController(), file, lastFilesAdapter.itemList)
        }
        homeViewModel.getLastModifiedFiles(AccountUtils.currentDriveId).observe(viewLifecycleOwner) {
            lastFilesRecyclerView.visibility = if (it.data.isNullOrEmpty()) GONE else VISIBLE
            lastFilesTitle.visibility = if (it.data.isNullOrEmpty()) GONE else VISIBLE
            lastFilesAdapter.addAll(it.data ?: arrayListOf())
        }
    }
}