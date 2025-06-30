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
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.FragmentRootFilesBinding
import com.infomaniak.drive.databinding.RootFolderLayoutBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.BaseRootFolderFragment
import com.infomaniak.drive.ui.FileListNavigatorObservable
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.ui.home.RootFileTreeCategory.*
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.utils.observeAndDisplayNetworkAvailability
import com.infomaniak.drive.utils.setupDriveToolbar
import com.infomaniak.drive.utils.setupRootPendingFilesIndicator
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.utils.safeNavigate
import com.infomaniak.lib.core.utils.setMargins
import kotlinx.coroutines.launch

class RootFilesFragment : BaseRootFolderFragment(), FileListNavigatorObservable {

    private var binding: FragmentRootFilesBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    override val fileListViewModel: FileListViewModel by viewModels()

    override val rootFolderLayout: RootFolderLayoutBinding by lazy { binding.rootFolderLayout }

    override val uiSettings by lazy { UiSettings(requireContext()) }

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

        setupItems(
            folderLayout = binding.rootFolderLayout,
            favoritesNav = RootFilesFragmentDirections.actionFilesFragmentToFavoritesFragment(),
            sharedWithMeNav = RootFilesFragmentDirections.actionFilesFragmentToSharedWithMeFragment(),
            mySharesNav = RootFilesFragmentDirections.actionFilesFragmentToMySharesFragment(),
            recentChangesNav = RootFilesFragmentDirections.actionFilesFragmentToRecentChangesFragment(),
            offlineNav = RootFilesFragmentDirections.actionFilesFragmentToOfflineFileFragment(),
            trashNav = RootFilesFragmentDirections.actionFilesFragmentToTrashFragment()
        )

        observeFiles(haveBin = true)
        observeNavigateFileListTo(mainViewModel, viewLifecycleOwner, this@RootFilesFragment, fileListViewModel)
        observeAndDisplayNetworkAvailability(
            mainViewModel = mainViewModel,
            noNetworkBinding = noNetworkInclude,
            noNetworkBindingDirectParent = contentLinearLayout,
        )

        setupRootPendingFilesIndicator(mainViewModel.pendingUploadsCount, rootFilesUploadFileInProgressView)

        navigateToLastVisitedFileTreeCategory()

        binding.root.enableEdgeToEdge(withBottom = false) { windowInsets ->
            binding.rootFolderLayout.cardView.setMargins(
                bottom = resources.getDimension(R.dimen.recyclerViewPaddingBottom).toInt() + windowInsets.bottom
            )
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

    override fun fileListDirections(
        folderToOpen: FolderToOpen,
    ): NavDirections = RootFilesFragmentDirections.actionFilesFragmentToFileListFragment(
        folderId = folderToOpen.id,
        folderName = folderToOpen.name
    )
}
