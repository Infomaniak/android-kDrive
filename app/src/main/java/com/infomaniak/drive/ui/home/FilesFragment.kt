/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.databinding.FragmentFilesBinding
import com.infomaniak.drive.ui.menu.MenuFragmentDirections
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.utils.isPositive
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

class FilesFragment : Fragment() {

    private var binding: FragmentFilesBinding by safeBinding()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentFilesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = AccountUtils.getCurrentDrive()!!.name

        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.searchItem) {
                ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SEARCH.id)
                safeNavigate(FilesFragmentDirections.actionFilesFragmentToSearchFragment())
                true
            } else {
                false
            }
        }

        setupItems()
    }

    private fun setupItems() = with(binding) {
        sharedWithMeFiles.apply {
            if (DriveInfosController.getDrivesCount(userId = AccountUtils.currentUserId, sharedWithMe = true).isPositive()) {
                setOnClickListener { safeNavigate(MenuFragmentDirections.actionMenuFragmentToSharedWithMeFragment()) }
            } else {
                isGone = true
            }
        }

        recentChanges.setOnClickListener {
            safeNavigate(MenuFragmentDirections.actionMenuFragmentToRecentChangesFragment())
        }

        offlineFile.setOnClickListener {
            safeNavigate(MenuFragmentDirections.actionMenuFragmentToOfflineFileFragment())
        }

        myShares.setOnClickListener {
            safeNavigate(MenuFragmentDirections.actionMenuFragmentToMySharesFragment())
        }

        trashbin.setOnClickListener {
            safeNavigate(MenuFragmentDirections.actionMenuFragmentToTrashFragment())
        }
    }
}
