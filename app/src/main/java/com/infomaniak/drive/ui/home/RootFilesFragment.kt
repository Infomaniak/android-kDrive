/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.core.legacy.utils.safeBinding
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.deeplink.DeeplinkAction
import com.infomaniak.drive.data.models.deeplink.RoleFolder
import com.infomaniak.drive.databinding.FragmentRootFilesBinding
import com.infomaniak.drive.databinding.RootFolderLayoutBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.BaseRootFolderFragment
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.ui.home.RootFileTreeCategory.CommonFolders
import com.infomaniak.drive.ui.home.RootFileTreeCategory.Favorites
import com.infomaniak.drive.ui.home.RootFileTreeCategory.MyShares
import com.infomaniak.drive.ui.home.RootFileTreeCategory.Offline
import com.infomaniak.drive.ui.home.RootFileTreeCategory.PersonalFolder
import com.infomaniak.drive.ui.home.RootFileTreeCategory.RecentChanges
import com.infomaniak.drive.ui.home.RootFileTreeCategory.SharedWithMe
import com.infomaniak.drive.ui.home.RootFileTreeCategory.Trash
import com.infomaniak.drive.ui.home.RootFilesFragmentDirections.Companion.actionFilesFragmentToFavoritesFragment
import com.infomaniak.drive.ui.home.RootFilesFragmentDirections.Companion.actionFilesFragmentToFileListFragment
import com.infomaniak.drive.ui.home.RootFilesFragmentDirections.Companion.actionFilesFragmentToMySharesFragment
import com.infomaniak.drive.ui.home.RootFilesFragmentDirections.Companion.actionFilesFragmentToRecentChangesFragment
import com.infomaniak.drive.ui.home.RootFilesFragmentDirections.Companion.actionFilesFragmentToSharedWithMeFragment
import com.infomaniak.drive.ui.home.RootFilesFragmentDirections.Companion.actionFilesFragmentToTrashFragment
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.drive.utils.observeAndDisplayNetworkAvailability
import com.infomaniak.drive.utils.observeNavigateFileListTo
import com.infomaniak.drive.utils.setupDriveToolbar
import com.infomaniak.drive.utils.setupRootPendingFilesIndicator
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

class RootFilesFragment : BaseRootFolderFragment() {

    private var binding: FragmentRootFilesBinding by safeBinding()
    private val mainViewModel: MainViewModel by activityViewModels()
    override val fileListViewModel: FileListViewModel by viewModels()

    override val rootFolderLayout: RootFolderLayoutBinding
        get() = binding.rootFolderLayout

    override val uiSettings by lazy { UiSettings(requireContext()) }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentRootFilesBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        fileListViewModel.updateRootFiles(UserDrive())

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
            sharedWithMeNav = actionFilesFragmentToSharedWithMeFragment(UserDrive()),
            favoritesNav = actionFilesFragmentToFavoritesFragment(UserDrive()),
            mySharesNav = actionFilesFragmentToMySharesFragment(UserDrive()),
            recentChangesNav = actionFilesFragmentToRecentChangesFragment(),
            offlineNav = RootFilesFragmentDirections.actionFilesFragmentToOfflineFileFragment(),
            trashNav = actionFilesFragmentToTrashFragment()
        )

        observeFiles(haveBin = true)
        observeNavigateFileListTo(mainViewModel, fileListViewModel)
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
        observeDeeplink()
    }

    private fun observeDeeplink() {
        lifecycleScope.launch {
            mainViewModel.navigateDeeplink.filterNotNull().collect {
                retrieveDeeplinkAction(it)?.let(::safelyNavigate)
                mainViewModel.navigateDeeplink.emit(null)
            }
        }
    }

    private fun retrieveDeeplinkAction(deeplinkAction: DeeplinkAction.Drive): NavDirections? {
        return with(deeplinkAction.roleFolder) {
            when (this) {
                is RoleFolder.Favorites -> actionFilesFragmentToFavoritesFragment(
                    userDrive = UserDrive(),
                    previewFileId = fileId ?: 0
                )
                is RoleFolder.MyShares -> actionFilesFragmentToMySharesFragment(
                    userDrive = UserDrive(),
                    previewFileId = fileId ?: 0
                )
                is RoleFolder.Recents -> actionFilesFragmentToRecentChangesFragment(previewFileId = fileId ?: 0)
                is RoleFolder.SharedWithMe -> actionFilesFragmentToSharedWithMeFragment(
                    userDrive = UserDrive(),
                    driveId = fileType?.sourceDriveId ?: 0,
                    fileType = fileType
                )
                is RoleFolder.Trash -> actionFilesFragmentToTrashFragment(subfolderId = folderId ?: -1)
                is RoleFolder.Files -> actionFilesFragmentToFileListFragment(fileType = fileType)
                is RoleFolder.Category, is RoleFolder.Collaboratives, is RoleFolder.SharedLinks -> notHandled(deeplinkAction)
            }
        }
    }

    private fun notHandled(deeplinkAction: DeeplinkAction): Nothing? {
        return if (deeplinkAction.isHandled) TODO("Need to implement here when ${deeplinkAction::class.simpleName} deeplink will be supported") else null
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
                Favorites -> safeNavigate(actionFilesFragmentToFavoritesFragment(UserDrive()))
                RecentChanges -> safeNavigate(actionFilesFragmentToRecentChangesFragment())
                SharedWithMe -> safeNavigate(actionFilesFragmentToSharedWithMeFragment(UserDrive()))
                MyShares -> safeNavigate(actionFilesFragmentToMySharesFragment(UserDrive()))
                Offline -> safeNavigate(RootFilesFragmentDirections.actionFilesFragmentToOfflineFileFragment())
                Trash -> safeNavigate(actionFilesFragmentToTrashFragment())
            }
        }
    }

    override fun fileListDirections(
        folderToOpen: FolderToOpen,
    ): NavDirections = actionFilesFragmentToFileListFragment(
        folderId = folderToOpen.id,
        folderName = folderToOpen.name
    )
}
