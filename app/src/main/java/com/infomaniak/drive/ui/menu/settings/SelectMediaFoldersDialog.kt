/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.menu.settings

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.FragmentBottomSheetSelectMediaFoldersBinding
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import com.infomaniak.drive.views.NoItemsLayoutView
import com.infomaniak.lib.core.utils.safeBinding
import com.infomaniak.lib.core.views.DividerItemDecorator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SelectMediaFoldersDialog : FullScreenBottomSheetDialog(), NoItemsLayoutView.INoItemsLayoutView {

    private var binding: FragmentBottomSheetSelectMediaFoldersBinding by safeBinding()

    private val mediaViewModel: SelectMediaFoldersViewModel by viewModels()
    private lateinit var mediaFoldersAdapter: MediaFoldersAdapter

    override val noItemsIcon = R.drawable.ic_folder_filled
    override val noItemsTitle = R.string.noMediaFolderTitle
    override val noItemsInitialListView: View by lazy { binding.mediaFolderList }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentBottomSheetSelectMediaFoldersBinding.inflate(inflater, container, false).also { binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            dismiss()
        }

        swipeRefreshLayout.isEnabled = false

        noMediaFolderLayout.iNoItemsLayoutView = this@SelectMediaFoldersDialog

        mediaFoldersAdapter = MediaFoldersAdapter { mediaFolder, isChecked ->
            lifecycleScope.launch(Dispatchers.IO) {
                mediaFolder.enableSync(isChecked)
            }
        }
        mediaFolderList.adapter = mediaFoldersAdapter
        ContextCompat.getDrawable(requireContext(), R.drawable.divider)?.let {
            mediaFolderList.addItemDecoration(DividerItemDecorator(it))
        }

        val drivePermissions = DrivePermissions().apply {
            registerPermissions(this@SelectMediaFoldersDialog) { authorized -> if (authorized) loadFolders() else dismiss() }
        }
        if (drivePermissions.checkWriteStoragePermission()) loadFolders()
    }

    private fun loadFolders() = with(binding) {
        swipeRefreshLayout.isRefreshing = true
        mediaViewModel.elementsToRemove.observe(viewLifecycleOwner) { elementsToRemove ->
            mediaFolderList.post { mediaFoldersAdapter.removeItemsById(elementsToRemove) }
        }
        mediaViewModel.getAllMediaFolders(requireActivity().contentResolver)
            .observe(viewLifecycleOwner) { (isComplete, mediaFolders) ->
                mediaFolderList.post {
                    if (!isComplete || mediaFolders.isNotEmpty()) {
                        mediaFoldersAdapter.addAll(mediaFolders)
                        noMediaFolderLayout.toggleVisibility(
                            isVisible = mediaFoldersAdapter.itemCount == 0,
                            noNetwork = false,
                            showRefreshButton = false
                        )
                    }
                    if (isComplete) {
                        swipeRefreshLayout.isRefreshing = false
                    }
                }
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (requireActivity() as SyncSettingsActivity).onDialogDismissed()
    }
}
