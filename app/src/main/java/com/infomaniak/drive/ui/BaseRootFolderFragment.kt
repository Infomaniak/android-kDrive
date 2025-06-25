/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui

import androidx.fragment.app.Fragment
import androidx.navigation.NavDirections
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.RootFolderLayoutBinding
import com.infomaniak.drive.ui.home.RootFileTreeCategory
import com.infomaniak.drive.ui.home.RootFilesFragment.FolderToOpen
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job

abstract class BaseRootFolderFragment : Fragment() {

    protected var commonFolderToOpen: FolderToOpen? = null
    protected var personalFolderToOpen: FolderToOpen? = null

    protected val hasFolderToOpenBeenSet: CompletableJob = Job()

    abstract val uiSettings: UiSettings

    fun updateFolderToOpenWhenClicked(fileTypes: Map<File.VisibilityType, File>, haveBin: Boolean = false) {
        fileTypes[File.VisibilityType.IS_TEAM_SPACE]?.let { file ->
            commonFolderToOpen = FolderToOpen(file.id, file.getDisplayName(requireContext()))
        }
        fileTypes[File.VisibilityType.IS_PRIVATE]?.let { file ->
            personalFolderToOpen = FolderToOpen(file.id, file.getDisplayName(requireContext()))
        }
        if (haveBin) {
            hasFolderToOpenBeenSet.complete()
        }
    }

    fun setupItems(
        folderLayout: RootFolderLayoutBinding,
        favoritesNav: NavDirections,
        sharedWithMeNav: NavDirections,
        mySharesNav: NavDirections,
        recentChangesNav: NavDirections? = null,
        offlineNav: NavDirections? = null,
        trashNav: NavDirections? = null
    ) = with(folderLayout) {
        organizationFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.CommonFolders
            commonFolderToOpen?.let { safelyNavigate(fileListDirections(it)) }
        }

        personalFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.PersonalFolder
            personalFolderToOpen?.let { safelyNavigate(fileListDirections(it)) }
        }

        favorites.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.Favorites
            safelyNavigate(favoritesNav)
        }

        sharedWithMeFiles.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.SharedWithMe
            safelyNavigate(sharedWithMeNav)
        }

        myShares.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.MyShares
            safelyNavigate(mySharesNav)
        }

        if (recentChangesNav != null && offlineNav != null && trashNav != null) {
            recentChanges.setOnClickListener {
                uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.RecentChanges
                safelyNavigate(recentChangesNav)
            }

            offlineFile.setOnClickListener {
                uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.Offline
                safelyNavigate(offlineNav)
            }

            trashbin.setOnClickListener {
                uiSettings.lastVisitedRootFileTreeCategory = RootFileTreeCategory.Trash
                safelyNavigate(trashNav)
            }
        }
    }

    abstract fun fileListDirections(folderToOpen: FolderToOpen): NavDirections
    abstract fun observeFiles()
}
