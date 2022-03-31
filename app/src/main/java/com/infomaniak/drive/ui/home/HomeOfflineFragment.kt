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

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.infomaniak.drive.R
import com.infomaniak.drive.ui.menu.OfflineFileFragment
import com.infomaniak.drive.utils.SentryLinearLayoutManager
import com.infomaniak.drive.utils.setMargin
import kotlinx.android.synthetic.main.fragment_file_list.*

class HomeOfflineFragment : OfflineFileFragment() {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        appBar.isGone = true
        sortButton.isGone = true
        homeTabTitle.isVisible = true
        swipeRefreshLayout.isEnabled = false

        val marginStandard = resources.getDimension(R.dimen.marginStandard).toInt()
        val marginStandardMedium = resources.getDimension(R.dimen.marginStandardMedium).toInt()
        sortLayout.setMargin(left = marginStandard, right = marginStandard, top = marginStandardMedium)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        fileRecyclerView.adapter = fileAdapter
    }

    override fun setupFileAdapter() {
        super.setupFileAdapter()
        fileAdapter.isHomeOffline = true
    }

    override fun createLayoutManager(isListMode: Boolean): LinearLayoutManager {
        return SentryLinearLayoutManager(findNavController(), requireContext())
    }

    override fun homeClassName(): String = javaClass.name

    fun reloadOffline() {
        if (isResumed) onRefresh()
    }
}