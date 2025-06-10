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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.FragmentSelectRootFolderBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.home.RootFileTreeCategory.*
import com.infomaniak.drive.ui.home.RootFilesFragment.FolderToOpen
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setMargins
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest

class SelectRootFolderFragment : Fragment() {

    private var binding: FragmentSelectRootFolderBinding by safeBinding()
    private val fileListViewModel: FileListViewModel by viewModels()

    private val uiSettings by lazy { UiSettings(requireContext()) }

    private var recentFoldersBindings: List<CardviewFileListBinding>? = null

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

        recentFoldersBindings = List(3) {
            CardviewFileListBinding.inflate(layoutInflater)
        }.onEach { recentFolderBinding ->
            recentFolderBinding.root.isVisible = false
            binding.recentListLayout.addView(recentFolderBinding.root, 0)
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        recentFoldersBindings = null
    }

    private fun setupRecentFolderView() {
        viewLifecycleOwner.lifecycleScope.launch {
            FileController.getRecentFolders().collectLatest { files ->
                binding.recentFolderTitle.isGone = files.isEmpty()
                binding.recentListLayout.isGone = files.isEmpty()

                setupRecentFolderView(files)
            }
        }
    }

    private suspend fun setupRecentFolderView(files: List<File>) = coroutineScope {
        recentFoldersBindings!!.forEachIndexed { index, binding ->
            launch(start = CoroutineStart.UNDISPATCHED) {
                try {
                    binding.root.isVisible = true
                    binding.root.setOnClickListener {
                        safeNavigate(
                            SelectRootFolderFragmentDirections.selectRootFolderFragmentToSelectFolderFragment(
                                files[index].id,
                                files[index].name
                            )
                        )
                    }
                    binding.itemViewFile.setFileItem(file = files[index])
                } finally {
                    binding.root.isVisible = false
                }
            }
        }
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

        rootFolderLayout.myShares.setOnClickListener {
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
