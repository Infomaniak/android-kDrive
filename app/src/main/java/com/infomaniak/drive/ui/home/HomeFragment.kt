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
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.*
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
import com.infomaniak.drive.databinding.FragmentHomeBinding
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.ui.menu.GalleryFragment
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.TabViewPagerUtils.FragmentTab
import com.infomaniak.drive.utils.TabViewPagerUtils.getFragment
import com.infomaniak.drive.utils.TabViewPagerUtils.setup
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.toPx

class HomeFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private var binding: FragmentHomeBinding by safeBinding()

    private val mainViewModel: MainViewModel by activityViewModels()
    private var mustRefreshUi: Boolean = false

    private val offlineFragment = HomeOfflineFragment().apply {
        arguments = FileListFragmentArgs(folderId = 1, folderName = "").toBundle()
    }

    private val galleryFragment = GalleryFragment()

    private val tabsHome = arrayListOf(
        FragmentTab(HomeActivitiesFragment(), R.id.homeActivitiesButton),
        FragmentTab(offlineFragment, R.id.homeOfflineButton),
        FragmentTab(galleryFragment, R.id.homeGalleryButton),
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentHomeBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        AccountUtils.getCurrentDrive()?.let { currentDrive -> setDriveHeader(currentDrive) }

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            noNetworkCard.root.isGone = isInternetAvailable
        }
        switchDriveButton.apply {
            if (DriveInfosController.hasSingleDrive(AccountUtils.currentUserId)) {
                icon = null
                isEnabled = false
            } else {
                setOnClickListener { safeNavigate(R.id.switchDriveDialog) }
            }
        }

        searchViewCard.searchView.isGone = true
        searchViewCard.searchViewText.isVisible = true
        ViewCompat.requestApplyInsets(homeCoordinator)

        searchViewCard.root.setOnClickListener {
            ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SEARCH.id)
            safeNavigate(HomeFragmentDirections.actionHomeFragmentToSearchFragment())
        }

        mainViewModel.deleteFileFromHome.observe(viewLifecycleOwner) { fileDeleted -> mustRefreshUi = fileDeleted }

        homeSwipeRefreshLayout.apply {
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

        val bottomNavigationOffset = with((activity as MainActivity).getBottomNavigation()) {
            layoutParams.height + marginBottom + marginTop + 10.toPx()
        }

        appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
            val margin = appBarLayout.totalScrollRange + verticalOffset + bottomNavigationOffset
            galleryFragment.setScrollbarTrackOffset(margin)
        }
    }

    override fun onResume() {
        super.onResume()
        updateUi()
        showPendingFiles()
    }

    private fun showPendingFiles() = with(binding) {
        homeUploadFileInProgress.updateUploadFileInProgress(
            UploadFile.getCurrentUserPendingUploadsCount(),
            homeUploadFileInProgressLayout,
        )
    }

    private fun updateUi(forceDownload: Boolean = false) = with(binding) {
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            val downloadRequired = forceDownload || mustRefreshUi
            (homeViewPager.getFragment(0) as? HomeActivitiesFragment)?.getLastActivities(currentDrive.id, downloadRequired)
            (homeViewPager.getFragment(1) as? HomeOfflineFragment)?.reloadOffline()
            (homeViewPager.getFragment(2) as? GalleryFragment)?.onRefreshGallery()

            setDriveHeader(currentDrive)
            notEnoughStorage.setup(currentDrive)
            mustRefreshUi = false
        }
    }

    private fun setDriveHeader(currentDrive: Drive) {
        binding.switchDriveButton.text = currentDrive.name
    }

    override fun onRefresh() {
        updateUi(forceDownload = true)
        binding.homeSwipeRefreshLayout.isRefreshing = false
    }

    companion object {
        const val MERGE_FILE_ACTIVITY_DELAY = 3600 * 12000 // 12h (ms)
    }
}
