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
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.FragmentRootFilesBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.ui.home.RootFileTreeCategory.*
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openFolder
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.utils.observeAndDisplayNetworkAvailability
import com.infomaniak.drive.utils.setupDriveToolbar
import com.infomaniak.drive.utils.setupRootPendingFilesIndicator
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setMargins
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class RootFilesFragment : Fragment() {

    private var binding: FragmentRootFilesBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val fileListViewModel: FileListViewModel by viewModels()

    private val uiSettings by lazy { UiSettings(requireContext()) }

    private var commonFolderToOpen: FolderToOpen? = null
    private var personalFolderToOpen: FolderToOpen? = null
    private val hasFolderToOpenBeenSet: CompletableJob = Job()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentRootFilesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setupDriveToolbar(collapsingToolbarLayout, switchDriveLayout, appBar)

        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.searchItem) {
                ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SEARCH.id)
                safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToSearchFragment())
                true
            } else {
                false
            }
        }

        personalFolder.setTitle(AccountUtils.getPersonalFolderTitle(requireContext()))

        setupItems()

        observeFiles()
        observeNavigateFileListTo()
        observeAndDisplayNetworkAvailability(
            mainViewModel = mainViewModel,
            noNetworkBinding = noNetworkInclude,
            noNetworkBindingDirectParent = contentLinearLayout,
        )

        setupRootPendingFilesIndicator(mainViewModel.pendingUploadsCount, rootFilesUploadFileInProgressView)

        navigateToLastVisitedFileTreeCategory()

        binding.root.enableEdgeToEdge(withBottom = false) { windowInsets ->
            binding.cardView.setMargins(
                bottom = resources.getDimension(R.dimen.recyclerViewPaddingBottom).toInt() + windowInsets.bottom
            )
        }
    }

    private fun setupItems() = with(binding) {
        organizationFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = CommonFolders
            commonFolderToOpen?.let { safeNavigate(fileListDirections(it)) }
        }

        personalFolder.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = PersonalFolder
            personalFolderToOpen?.let { safeNavigate(fileListDirections(it)) }
        }

        favorites.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = Favorites
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToFavoritesFragment())
        }

        recentChanges.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = RecentChanges
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToRecentChangesFragment())
        }

        sharedWithMeFiles.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = SharedWithMe
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToSharedWithMeFragment())
        }

        myShares.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = MyShares
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToMySharesFragment())
        }

        offlineFile.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = Offline
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToOfflineFileFragment())
        }

        trashbin.setOnClickListener {
            uiSettings.lastVisitedRootFileTreeCategory = Trash
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToTrashFragment())
        }
    }

    private fun observeFiles() {
        fileListViewModel.rootFiles.observe(viewLifecycleOwner) { fileTypes ->
            binding.organizationFolder.isVisible = fileTypes.contains(File.VisibilityType.IS_TEAM_SPACE)
            binding.personalFolder.isVisible = fileTypes.contains(File.VisibilityType.IS_PRIVATE)

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

    private fun observeNavigateFileListTo() {
        mainViewModel.navigateFileListTo.observe(viewLifecycleOwner) { file ->
            if (file.isFolder()) {
                openFolder(
                    file = file,
                    shouldHideBottomNavigation = false,
                    shouldShowSmallFab = false,
                    fileListViewModel = fileListViewModel,
                )
            } else {
                displayFile(file, mainViewModel, fileAdapter = null)
            }
        }
    }

    data class FolderToOpen(val id: Int, val name: String)

    private fun navigateToLastVisitedFileTreeCategory() {
        val hasDeepLink = requireActivity().intent.data != null
        if (hasDeepLink) return
        if (fileListViewModel.hasNavigatedToLastVisitedFileTreeCategory) {
            uiSettings.lastVisitedRootFileTreeCategory = null
        } else lifecycleScope.launch {
            fileListViewModel.hasNavigatedToLastVisitedFileTreeCategory = true
            when (uiSettings.lastVisitedRootFileTreeCategory) {
                null -> Unit // Stay here (at the root)
                CommonFolders -> {
                    hasFolderToOpenBeenSet.join()
                    commonFolderToOpen?.let { safeNavigate(fileListDirections(it)) }
                }
                PersonalFolder -> {
                    hasFolderToOpenBeenSet.join()
                    personalFolderToOpen?.let { safeNavigate(fileListDirections(it)) }
                }
                Favorites -> safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToFavoritesFragment())
                RecentChanges -> safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToRecentChangesFragment())
                SharedWithMe -> safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToSharedWithMeFragment())
                MyShares -> safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToMySharesFragment())
                Offline -> safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToOfflineFileFragment())
                Trash -> safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToTrashFragment())
            }
        }
    }

    private fun fileListDirections(
        folderToOpen: FolderToOpen,
    ): NavDirections = RootFilesFragmentDirections.actionFilesFragmentToFileListFragment(
        folderId = folderToOpen.id,
        folderName = folderToOpen.name
    )
}
