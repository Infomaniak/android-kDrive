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
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.navGraphViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRepository
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerProgress
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.lib.core.utils.setPagination
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.fragment_file_list.*
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.fragment_home.searchViewCard
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.android.synthetic.main.item_search_view.*

class HomeFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {
    private lateinit var lastElementsAdapter: RecyclerView.Adapter<ViewHolder>
    private lateinit var lastFilesAdapter: LastFilesAdapter
    private val homeViewModel: HomeViewModel by navGraphViewModels(R.id.homeFragment)
    private val mainViewModel: MainViewModel by activityViewModels()
    private var isProOrTeam: Boolean = false
    private var mustRefreshUi: Boolean = false

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

        lastFilesAdapter = LastFilesAdapter()
        lastFilesRecyclerView.apply {
            adapter = lastFilesAdapter
            layoutManager = object : LinearLayoutManager(requireContext(), HORIZONTAL, false) {
                override fun checkLayoutParams(layoutParams: RecyclerView.LayoutParams): Boolean {
                    layoutParams.width = (width * 0.4f).toInt()
                    return true
                }
            }
        }

        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            setDriveHeader(currentDrive)
        }

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            noNetworkCard.isGone = isInternetAvailable
        }

        driveInfos.setOnClickListener { safeNavigate(R.id.switchDriveDialog) }

        searchView.isGone = true
        searchViewText.isVisible = true
        ViewCompat.requestApplyInsets(homeCoordinator)

        searchViewCard.setOnClickListener {
            safeNavigate(HomeFragmentDirections.actionHomeFragmentToSearchFragment())
        }

        mainViewModel.deleteFileFromHome.observe(viewLifecycleOwner) { fileDeleted ->
            mustRefreshUi = fileDeleted
        }

        homeSwipeRefreshLayout?.apply {
            setOnRefreshListener(this@HomeFragment)
            appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
                isEnabled = verticalOffset == 0
            })
        }

        homeUploadFileInProgress.setUploadFileInProgress(R.string.uploadInProgressTitle) {
            navigateToUploadView(Utils.OTHER_ROOT_ID)
        }

        requireContext().trackUploadWorkerProgress().observe(viewLifecycleOwner) {
            val workInfo = it.firstOrNull() ?: return@observe
            if (workInfo.progress.getBoolean(UploadWorker.IS_UPLOADED, false)) {
                showPendingFiles()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (lastElementsRecyclerView.adapter == null) initLastElementsAdapter()
        updateUi()
        showPendingFiles()
    }

    private fun showPendingFiles() {
        homeUploadFileInProgress.updateUploadFileInProgress(UploadFile.getCurrentUserPendingUploadsCount())
    }

    // TODO - Use same fragment with PicturesAdapter and LastPictures
    private fun initLastElementsAdapter() {
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            lastElementsRecyclerView.apply {
                homeViewModel.lastActivityPage = 1
                homeViewModel.lastPicturesPage = 1
                paginationListener?.let { removeOnScrollListener(it) }
                isProOrTeam = currentDrive.pack == Drive.DrivePack.PRO.value || currentDrive.pack == Drive.DrivePack.TEAM.value
                // Don't remove unnecessary parentheses from function call with lambda,
                // else "kotlin.UninitializedPropertyAccessException: lateinit property lastElementsAdapter has not been initialized"
                lastElementsAdapter = if (isProOrTeam) LastActivitiesAdapter() else HomePicturesAdapter() { file ->
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
                    paginationListener = setPagination(
                        whenLoadMoreIsPossible = {
                            val lastActivitiesAdapter = lastElementsAdapter as LastActivitiesAdapter
                            if (!lastActivitiesAdapter.isComplete && !isDownloadingActivities) {
                                lastActivitiesAdapter.showLoading()
                                homeViewModel.lastActivityPage++
                                homeViewModel.lastActivityLastPage++
                                getLastPicturesOrActivities(currentDrive.id, isProOrTeam)
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
                                homeViewModel.lastPicturesPage++
                                homeViewModel.lastPicturesLastPage++
                                getLastPicturesOrActivities(currentDrive.id, isProOrTeam)
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

    private fun updateUi(forceDownload: Boolean = false) {
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            val downloadRequired = forceDownload || mustRefreshUi
            if (downloadRequired) resetAndScrollToTop()

            setDriveHeader(currentDrive)
            getLastPicturesOrActivities(currentDrive.id, isProOrTeam, downloadRequired)
            getLastModifiedFiles(downloadRequired)
            notEnoughStorage.setup(currentDrive)
            mustRefreshUi = false
        }
    }

    private fun setDriveHeader(currentDrive: Drive) {
        driveName.text = currentDrive.name
        driveIcon.imageTintList = ColorStateList.valueOf(Color.parseColor(currentDrive.preferences.color))
    }

    private fun setupLastElementsTitle(isProOrTeam: Boolean) {
        lastElementsTitle.apply {
            text = if (isProOrTeam) getString(R.string.homeLastActivities) else getString(R.string.homeMyLastPictures)
            isVisible = true
        }
    }

    private fun getLastPicturesOrActivities(driveId: Int, isProOrTeam: Boolean, forceDownload: Boolean = false) {
        if (isProOrTeam) getLastActivities(driveId, forceDownload)
        else getPictures(driveId, forceDownload)
        setupLastElementsTitle(isProOrTeam)
    }

    private fun getLastActivities(driveId: Int, forceDownload: Boolean = false) {
        (lastElementsAdapter as LastActivitiesAdapter).apply {
            if (forceDownload) {
                clean()
                showLoading()
            }
            isComplete = false
            isDownloadingActivities = true
            homeViewModel.getLastActivities(driveId, forceDownload).observe(viewLifecycleOwner) {
                lastElementsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                it?.let { (apiResponse, mergedActivities) ->
                    if (apiResponse.page == 1 && itemCount > 0) clean()
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

    private fun getPictures(driveId: Int, forceDownload: Boolean = false) {
        (lastElementsAdapter as HomePicturesAdapter).apply {
            isComplete = false
            isDownloadingPictures = true
            homeViewModel.getLastPictures(driveId, forceDownload).observe(viewLifecycleOwner) { apiResponse ->
                lastElementsAdapter.stateRestorationPolicy = RecyclerView.Adapter.StateRestorationPolicy.PREVENT_WHEN_EMPTY
                apiResponse?.data?.let { lastPictures ->
                    clean()
                    addAll(lastPictures)
                    isComplete = lastPictures.size < ApiRepository.PER_PAGE
                } ?: also { isComplete = true }
                isDownloadingPictures = false
            }
        }
    }

    private fun getLastModifiedFiles(forceDownload: Boolean = false) {
        lastFilesAdapter.clean()
        lastFilesAdapter.showLoading()

        lastFilesAdapter.onFileClicked = { file ->
            Utils.displayFile(mainViewModel, findNavController(), file, lastFilesAdapter.itemList)
        }
        mainViewModel.getRecentChanges(AccountUtils.currentDriveId, true, forceDownload).observe(viewLifecycleOwner) { result ->
            result?.apply {
                lastFilesRecyclerView.isGone = files.isNullOrEmpty()
                lastFilesTitle.isGone = files.isNullOrEmpty()
                lastFilesAdapter.addAll(files)
            }
        }
    }

    private fun resetAndScrollToTop() {
        homeViewModel.apply {
            lastActivityPage = 1
            lastActivityLastPage = 1
            lastPicturesPage = 1
            lastPicturesLastPage = 1
        }
        homeCoordinator.scrollTo(0, 0)
    }

    override fun onRefresh() {
        updateUi(forceDownload = true)
        homeSwipeRefreshLayout.isRefreshing = false
    }
}
