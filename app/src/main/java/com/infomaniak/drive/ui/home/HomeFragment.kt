/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.core.view.ViewCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.infomaniak.drive.databinding.FragmentHomeBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.utils.setDriveHeader
import com.infomaniak.drive.utils.setupRootPendingFilesIndicator
import com.infomaniak.drive.utils.setupSwitchDriveButton
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

class HomeFragment : Fragment(), SwipeRefreshLayout.OnRefreshListener {

    private var binding: FragmentHomeBinding by safeBinding()

    private val mainViewModel: MainViewModel by activityViewModels()
    private var mustRefreshUi: Boolean = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentHomeBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel.isInternetAvailable.observe(viewLifecycleOwner) { isInternetAvailable ->
            noNetworkCard.root.isGone = isInternetAvailable
        }
        switchDriveLayout.setupSwitchDriveButton(this@HomeFragment)

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
            appBarLayout.addOnOffsetChangedListener { _, verticalOffset ->
                isEnabled = verticalOffset == 0
            }
        }

        setupRootPendingFilesIndicator(mainViewModel.pendingUploadsCount, homeUploadFileInProgressView)
    }

    override fun onResume() {
        super.onResume()
        updateUi()
    }

    private fun updateUi(forceDownload: Boolean = false) = with(binding) {
        AccountUtils.getCurrentDrive()?.let { currentDrive ->
            val downloadRequired = forceDownload || mustRefreshUi
            homeActivitiesFragment.getFragment<HomeActivitiesFragment>().getLastActivities(currentDrive.id, downloadRequired)

            notEnoughStorage.setup(currentDrive)
            mustRefreshUi = false
        }
    }

    override fun onRefresh() = with(binding) {
        updateUi(forceDownload = true)
        AccountUtils.getCurrentDrive()?.let { switchDriveLayout.setDriveHeader(it) }
        homeSwipeRefreshLayout.isRefreshing = false
    }

    companion object {
        const val MERGE_FILE_ACTIVITY_DELAY = 3600 * 12000 // 12h (ms)
    }
}
