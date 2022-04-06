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
package com.infomaniak.drive.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.material.appbar.AppBarLayout
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.data.services.UploadWorker.Companion.trackUploadWorkerProgress
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.ui.menu.PicturesFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.TabViewPagerUtils.FragmentTab
import com.infomaniak.drive.utils.TabViewPagerUtils.getFragment
import com.infomaniak.drive.utils.TabViewPagerUtils.setup
import com.infomaniak.lib.core.utils.toPx
import kotlinx.android.synthetic.main.fragment_home.*
import kotlinx.android.synthetic.main.item_search_view.*

class HomeFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private val mainViewModel: MainViewModel by activityViewModels()
    private var mustRefreshUi: Boolean = false

    private val offlineFragment = HomeOfflineFragment().apply {
        arguments = FileListFragmentArgs(folderId = 1, folderName = "").toBundle()
    }

    private val picturesFragment = PicturesFragment()

    private val tabsHome = arrayListOf(
        FragmentTab(HomeActivitiesFragment(), R.id.homeActivitiesButton),
        FragmentTab(offlineFragment, R.id.homeOfflineButton),
        FragmentTab(picturesFragment, R.id.homePicturesButton),
    )

    companion object {
        const val MERGE_FILE_ACTIVITY_DELAY = 3600 * 12000 // 12h (ms)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        AccountUtils.getCurrentDrive()?.let { currentDrive -> setDriveHeader(currentDrive) }

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            noNetworkCard.isGone = isInternetAvailable
        }
        switchDriveButton.apply {
            if (DriveInfosController.hasSingleDrive(AccountUtils.currentUserId)) {
                icon = null
                isEnabled = false
            } else {
                setOnClickListener { safeNavigate(R.id.switchDriveDialog) }
            }
        }

        searchView.isGone = true
        searchViewText.isVisible = true
        ViewCompat.requestApplyInsets(homeCoordinator)

        searchViewCard.setOnClickListener {
            safeNavigate(HomeFragmentDirections.actionHomeFragmentToSearchFragment())
        }

        mainViewModel.deleteFileFromHome.observe(viewLifecycleOwner) { fileDeleted -> mustRefreshUi = fileDeleted }

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

        lifecycleScope.launchWhenResumed {
            setup(homeViewPager, tabsHomeGroup, tabsHome) { UiSettings(requireContext()).lastHomeSelectedTab = it }
            homeViewPager.setCurrentItem(UiSettings(requireContext()).lastHomeSelectedTab, false)
        }

        // This line is required to be able to scroll down with the fast scrollbar in the home view
        homeCoordinator.isNestedScrollingEnabled = true
        homeViewPager.isNestedScrollingEnabled = true

        appBarLayout.addOnOffsetChangedListener(AppBarLayout.OnOffsetChangedListener { _, verticalOffset ->
            picturesFragment.setScrollbarTrackOffset(appBarLayout.totalScrollRange + verticalOffset + 92.toPx())
        })
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        showPendingFiles()
    }

    private fun showPendingFiles() {
        homeUploadFileInProgress.updateUploadFileInProgress(UploadFile.getCurrentUserPendingUploadsCount())
    }

    private fun updateUi(forceDownload: Boolean = false) {
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            val downloadRequired = forceDownload || mustRefreshUi
            (homeViewPager.getFragment(0) as? HomeActivitiesFragment)?.getLastActivities(currentDrive.id, downloadRequired)
            (homeViewPager.getFragment(1) as? HomeOfflineFragment)?.reloadOffline()
            (homeViewPager.getFragment(2) as? PicturesFragment)?.onRefreshPictures()

            setDriveHeader(currentDrive)
            notEnoughStorage.setup(currentDrive)
            mustRefreshUi = false
        }
    }

    private fun setDriveHeader(currentDrive: Drive) {
        switchDriveButton.text = currentDrive.name
    }

    override fun onRefresh() {
        updateUi(forceDownload = true)
        homeSwipeRefreshLayout.isRefreshing = false
    }
}
