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
package com.infomaniak.drive.ui.fileList

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.NavDirections
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.FragmentSelectRootFolderBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.home.RootFileTreeCategory.*
import com.infomaniak.drive.ui.home.RootFilesFragment.FolderToOpen
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setMargins
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job

class SelectRootFolderFragment : Fragment() {

    private var binding: FragmentSelectRootFolderBinding by safeBinding()
    private val fileListViewModel: FileListViewModel by viewModels()

    private val uiSettings by lazy { UiSettings(requireContext()) }

    private var commonFolderToOpen: FolderToOpen? = null
    private var personalFolderToOpen: FolderToOpen? = null
    private val hasFolderToOpenBeenSet: CompletableJob = Job()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSelectRootFolderBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = "Sélectionner un dossier"

        val currentDrive = AccountUtils.getCurrentDrive(forceRefresh = true)
        rootFolderTitle.text = currentDrive?.name

        rootFolderLayout.recentChanges.isGone = true
        rootFolderLayout.offlineFile.isGone = true
        rootFolderLayout.trashbin.isGone = true

        setupRecentFolderView()

        with((activity as SelectFolderActivity).getSaveButton()) {
            isGone = true
        }

        setupItems()

        observeFiles()

        root.enableEdgeToEdge(withBottom = false) { windowInsets ->
            rootFolderLayout.cardView.setMargins(
                bottom = resources.getDimension(R.dimen.recyclerViewPaddingBottom).toInt() + windowInsets.bottom
            )
        }

        toolbar.setNavigationOnClickListener { requireActivity().finish() }

    }

    private fun setupRecentFolderView() {
        // Test function
        binding.recentFolderTitle.isVisible = true
        val container: LinearLayout = binding.recentListLayout

        val folder1Binding = CardviewFileListBinding.inflate(layoutInflater)
        val folder2Binding = CardviewFileListBinding.inflate(layoutInflater)
        val folder3Binding = CardviewFileListBinding.inflate(layoutInflater)

        container.addView(folder1Binding.root, 0)
        container.addView(folder2Binding.root, 1 )
        container.addView(folder3Binding.root, 2)
    }

    private fun setupItems() = with(binding) {
        rootFolderLayout.organizationFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = CommonFolders
            commonFolderToOpen?.let { safeNavigate(fileListDirections(it)) }
        }

        rootFolderLayout.personalFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = PersonalFolder
            personalFolderToOpen?.let { safeNavigate(fileListDirections(it)) }
        }

        rootFolderLayout.favorites.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = Favorites
            safeNavigate(SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToFavoritesFragment())
        }

        rootFolderLayout.sharedWithMeFiles.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = SharedWithMe
            safeNavigate(SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToSharedWithMeFragment())
        }

        rootFolderLayout. myShares.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = MyShares
            safeNavigate(SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToMySharesFragment())
        }
    }

    private fun observeFiles() {
        fileListViewModel.rootFiles.observe(viewLifecycleOwner) { fileTypes ->
            binding.rootFolderLayout.organizationFolder.isVisible = fileTypes.contains(File.VisibilityType.IS_TEAM_SPACE)
            binding.rootFolderLayout.personalFolder.isVisible = fileTypes.contains(File.VisibilityType.IS_PRIVATE)

            updateFolderToOpenWhenClicked(fileTypes)
        }
    }

    private fun updateFolderToOpenWhenClicked(fileTypes: Map<File.VisibilityType, File>) {
        fileTypes[File.VisibilityType.IS_TEAM_SPACE]?.let { file ->
            commonFolderToOpen = FolderToOpen(file.id, file.getDisplayName(requireContext()))
        }
        fileTypes[File.VisibilityType.IS_PRIVATE]?.let { file ->
            personalFolderToOpen = FolderToOpen(file.id, file.getDisplayName(requireContext()))
        }
        hasFolderToOpenBeenSet.complete()
    }

    private fun fileListDirections(
        folderToOpen: FolderToOpen,
    ): NavDirections = SelectRootFolderFragmentDirections.selectRootFolderFragmentToSelectFolderFragment(
        folderId = folderToOpen.id,
        folderName = folderToOpen.name
    )
}
