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
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavDirections
import com.infomaniak.core.fragmentnavigation.safelyNavigate
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UiSettings
import com.infomaniak.drive.databinding.CardviewFileListBinding
import com.infomaniak.drive.databinding.FragmentSelectRootFolderBinding
import com.infomaniak.drive.databinding.RootFolderLayoutBinding
import com.infomaniak.drive.extensions.enableEdgeToEdge
import com.infomaniak.drive.ui.BaseRootFolderFragment
import com.infomaniak.drive.ui.home.RootFilesFragment.FolderToOpen
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.TypeFolder
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.lib.core.utils.setMargins
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SelectRootFolderFragment : BaseRootFolderFragment() {

    private var _binding: FragmentSelectRootFolderBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    override val fileListViewModel: FileListViewModel by viewModels()

    override val rootFolderLayout: RootFolderLayoutBinding
        get() = binding.rootFolderLayout

    private val selectRootFolderViewModel: SelectRootFolderViewModel by viewModels()

    override val uiSettings by lazy { UiSettings(requireContext()) }

    private val recentFoldersBindings = mutableListOf<CardviewFileListBinding>()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentSelectRootFolderBinding.inflate(inflater, container, false).also {
            _binding = it
        }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?): Unit = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        collapsingToolbarLayout.title = getString(R.string.selectFolderTitle)

        val currentDrive = AccountUtils.getCurrentDrive(forceRefresh = true)
        rootFolderTitle.text = currentDrive?.name

        rootFolderLayout.apply {
            recentChanges.isGone = true
            offlineFile.isGone = true
            trashbin.isGone = true
        }

        setupRecentFoldersViews()

        (activity as SelectFolderActivity).hideSaveButton()

        rootFolderLayout.cardView.setMargins(top = resources.getDimension(R.dimen.marginStandardSmall).toInt())

        setupItems(
            folderLayout = binding.rootFolderLayout,
            favoritesNav = SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToFavoritesFragment(),
            sharedWithMeNav = SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToSharedWithMeFragment(),
            mySharesNav = SelectRootFolderFragmentDirections.actionSelectRootFolderFragmentToMySharesFragment()
        )

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

    private fun setupRecentFoldersViews() {
        viewLifecycleOwner.lifecycleScope.launch {
            selectRootFolderViewModel.getRecentFolders(RECENT_FOLDER_NUMBER)
            selectRootFolderViewModel.recentFiles.collectLatest { files ->

                _binding?.let {
                    it.recentFolderTitle.isGone = files.isEmpty()
                    it.recentListLayout.isGone = files.isEmpty()

                    files.forEach { file ->
                        CardviewFileListBinding.inflate(layoutInflater).apply {
                            it.recentListLayout.addView(root, 0)
                            launch(start = CoroutineStart.UNDISPATCHED) { setupRecentFolderView(file) }
                        }
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
        itemViewFile.setFileItem(file = file, typeFolder = TypeFolder.recentFolder)
    }

    override fun fileListDirections(
        folderToOpen: FolderToOpen,
    ): NavDirections = SelectRootFolderFragmentDirections.selectRootFolderFragmentToSelectFolderFragment(
        folderId = folderToOpen.id,
        folderName = folderToOpen.name,
    )

    companion object {
        private const val RECENT_FOLDER_NUMBER = 3
    }
}
