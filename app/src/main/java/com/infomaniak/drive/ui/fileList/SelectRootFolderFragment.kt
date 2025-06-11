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
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.FragmentSelectRootFolderBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.home.RootFileTreeCategory.*
import com.infomaniak.drive.ui.home.RootFilesFragment.FolderToOpen
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.lib.core.utils.setMargins
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SelectRootFolderFragment : Fragment() {

    private var _binding: FragmentSelectRootFolderBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val fileListViewModel: FileListViewModel by viewModels()

    private val selectRootFolderViewModel: SelectRootFolderViewModel by activityViewModels() // ou viewModel ?

    private val uiSettings by lazy { UiSettings(requireContext()) }

    private val recentFoldersBindings = mutableListOf<CardviewFileListBinding>()

    private var commonFolderToOpen: FolderToOpen? = null
    private var personalFolderToOpen: FolderToOpen? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSelectRootFolderBinding.inflate(inflater, container, false).also {
            _binding = it
            inflateCardviewFileListBinding()
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = "Sélectionner un dossier"

        val currentDrive = AccountUtils.getCurrentDrive(forceRefresh = true)
        rootFolderTitle.text = currentDrive?.name

        rootFolderLayout.apply {
            recentChanges.isGone = true
            offlineFile.isGone = true
            trashbin.isGone = true
        }

        setupRecentFoldersViews()

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

    override fun onDestroyView() {
        super.onDestroyView()
        recentFoldersBindings.clear()
        _binding = null
    }

    private fun inflateCardviewFileListBinding() {
        repeat(RECENT_FOLDER_NUMBER) {
            recentFoldersBindings.add(
                CardviewFileListBinding.inflate(layoutInflater)
                    .apply { this.root.isGone = true }
                    .also { binding.recentListLayout.addView(it.root, 0) }
            )
        }
    }

    private fun setupRecentFoldersViews() {
        viewLifecycleOwner.lifecycleScope.launch {
            selectRootFolderViewModel.getRecentFoldersViewModel(limit = RECENT_FOLDER_NUMBER).collectLatest { files ->
                _binding?.let {
                    it.recentFolderTitle.isGone = files.isEmpty()
                    it.recentListLayout.isGone = files.isEmpty()

                    files.forEachIndexed { index, file ->
                        val binding = recentFoldersBindings.getOrElse(index) { return@collectLatest }
                        launch(start = CoroutineStart.UNDISPATCHED) { binding.setupRecentFolderView(file) }
                    }
                }
            }
        }
    }

    private suspend fun CardviewFileListBinding.setupRecentFolderView(file: File) {
        root.isVisible = true
        root.setOnClickListener {
            safelyNavigate(
                SelectRootFolderFragmentDirections.selectRootFolderFragmentToSelectFolderFragment(file.id, file.name)
            )
        }
        itemViewFile.setFileItem(file = file)
    }

    private fun setupItems() = with(binding) {
        rootFolderLayout.organizationFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = CommonFolders
            commonFolderToOpen?.let { safelyNavigate(fileListDirections(it)) }
        }

        rootFolderLayout.personalFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = PersonalFolder
            personalFolderToOpen?.let { safelyNavigate(fileListDirections(it)) }
        }

        rootFolderLayout.favorites.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = Favorites
            safelyNavigate(SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToFavoritesFragment())
        }

        rootFolderLayout.sharedWithMeFiles.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = SharedWithMe
            safelyNavigate(SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToSharedWithMeFragment())
        }

        rootFolderLayout.myShares.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = MyShares
            safelyNavigate(SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToMySharesFragment())
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
    }

    private fun fileListDirections(
        folderToOpen: FolderToOpen,
    ): NavDirections = SelectRootFolderFragmentDirections.selectRootFolderFragmentToSelectFolderFragment(
        folderId = folderToOpen.id,
        folderName = folderToOpen.name,
    )

    companion object {
        const val RECENT_FOLDER_NUMBER = 3
    }
}
