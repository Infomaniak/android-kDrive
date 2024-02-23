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

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FolderFilesProvider.SourceRestrictionType.ONLY_FROM_LOCAL
import com.infomaniak.drive.data.cache.FolderFilesProvider.SourceRestrictionType.ONLY_FROM_REMOTE
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UploadFile
import com.infomaniak.drive.databinding.FragmentRootFilesBinding
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.utils.*
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openFolder
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate

class RootFilesFragment : Fragment() {

    private var binding: FragmentRootFilesBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    private val fileListViewModel: FileListViewModel by viewModels()

    private var commonFolderToOpen: FolderToOpen? = null
    private var personalFolderToOpen: FolderToOpen? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentRootFilesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        setupDriveToolbar()

        toolbar.setOnMenuItemClickListener { menuItem ->
            if (menuItem.itemId == R.id.searchItem) {
                ShortcutManagerCompat.reportShortcutUsed(requireContext(), Shortcuts.SEARCH.id)
                safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToSearchFragment())
                true
            } else {
                false
            }
        }

        setupItems()

        updateAndObserveFiles()
        observeNavigateFileListTo()
        observeAndDisplayNetworkAvailability(
            mainViewModel = mainViewModel,
            noNetworkBinding = noNetworkInclude,
            noNetworkBindingDirectParent = contentLinearLayout,
        )
        rootFilesUploadFileInProgressView.setUploadFileInProgress(this@RootFilesFragment, Utils.OTHER_ROOT_ID)
    }

    override fun onResume() {
        super.onResume()
        showPendingFiles()
    }

    private fun setupDriveToolbar() = with(binding) {
        collapsingToolbarLayout.title = AccountUtils.getCurrentDrive()!!.name
        switchDriveLayout.setupSwitchDriveButton(this@RootFilesFragment)

        appBar.addOnOffsetChangedListener { _, verticalOffset ->
            val fullyExpanded = verticalOffset == 0
            switchDriveLayout.root.isVisible = fullyExpanded

            if (fullyExpanded) {
                collapsingToolbarLayout.setExpandedTitleTextColor(ColorStateList.valueOf(Color.TRANSPARENT))
            } else {
                collapsingToolbarLayout.setExpandedTitleTextAppearance(R.style.CollapsingToolbarExpandedTitleTextAppearance)
            }
        }
    }

    private fun setupItems() = with(binding) {
        organizationFolder.setOnClickListener {
            commonFolderToOpen?.let { (id, name) ->
                safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToFileListFragment(folderId = id, folderName = name))
            }
        }

        personalFolder.setOnClickListener {
            personalFolderToOpen?.let { (id, name) ->
                safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToFileListFragment(folderId = id, folderName = name))
            }
        }

        favorites.setOnClickListener {
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToFavoritesFragment())
        }

        recentChanges.setOnClickListener {
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToRecentChangesFragment())
        }

        sharedWithMeFiles.apply {
            if (DriveInfosController.getDrivesCount(userId = AccountUtils.currentUserId, sharedWithMe = true).isPositive()) {
                setOnClickListener { safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToSharedWithMeFragment()) }
            } else {
                isGone = true
            }
        }

        myShares.setOnClickListener {
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToMySharesFragment())
        }

        offlineFile.setOnClickListener {
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToOfflineFileFragment())
        }

        trashbin.setOnClickListener {
            safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToTrashFragment())
        }
    }

    private fun updateAndObserveFiles() = with(binding) {
        val isNetworkUnavailable = mainViewModel.isInternetAvailable.value == false

        fileListViewModel.getFiles(
            parentId = Utils.ROOT_ID,
            order = File.SortType.NAME_AZ,
            sourceRestrictionType = if (isNetworkUnavailable) ONLY_FROM_LOCAL else ONLY_FROM_REMOTE,
            isNewSort = false,
        ).observe(viewLifecycleOwner) {
            // TODO: Do not process data on the main thread. Wait for getFiles refactor
            val fileTypes = it?.files?.associateBy(File::getVisibilityType) ?: emptyMap()

            organizationFolder.isVisible = fileTypes.contains(File.VisibilityType.IS_TEAM_SPACE)
            personalFolder.isVisible = fileTypes.contains(File.VisibilityType.IS_PRIVATE)

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

    private fun showPendingFiles() {
        binding.rootFilesUploadFileInProgressView.updateUploadFileInProgress(UploadFile.getCurrentUserPendingUploadsCount())
    }

    data class FolderToOpen(val id: Int, val name: String)
}
