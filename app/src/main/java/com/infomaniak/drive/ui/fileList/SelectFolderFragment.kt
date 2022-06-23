/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.view.View
import androidx.activity.addCallback
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.Rights
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.SelectFolderViewModel
import com.infomaniak.drive.utils.MatomoUtils.trackNewElementEvent
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.showSnackbar
import com.infomaniak.lib.core.utils.safeNavigate
import kotlinx.android.synthetic.main.fragment_file_list.*

class SelectFolderFragment : FileListFragment() {

    private val selectFolderViewModel: SelectFolderViewModel by activityViewModels()
    private val navigationArgs: SelectFolderFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override var showPendingFiles: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        userDrive = selectFolderViewModel.userDrive
        setNoFilesLayout = SetNoFilesLayout()
        super.onViewCreated(view, savedInstanceState)

        folderName = if (folderId == ROOT_ID) selectFolderViewModel.currentDrive?.name ?: "/" else navigationArgs.folderName

        collapsingToolbarLayout.title = getString(R.string.selectFolderTitle)

        toolbar.menu.findItem(R.id.addFolderItem).apply {
            setOnMenuItemClickListener {
                val selectFolderActivity = requireActivity() as? SelectFolderActivity
                if (FileController.getFileById(folderId, userDrive)?.rights?.canCreateDirectory == true) {
                    selectFolderActivity?.hideSaveButton()
                    trackNewElementEvent("createFolderOnTheFly")
                    safeNavigate(
                        SelectFolderFragmentDirections.actionSelectFolderFragmentToNewFolderFragment(
                            parentFolderId = folderId,
                            userDrive = userDrive,
                        )
                    )
                } else {
                    selectFolderActivity?.showSnackbar(R.string.allFileAddRightError)
                }
                true
            }
            isVisible = true
        }

        toolbar.setNavigationOnClickListener { onBackPressed() }
        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) { onBackPressed() }

        fileAdapter.apply {
            selectFolder = true
            onFileClicked = { file ->
                if (file.isFolder() && !file.isDisabled()) {
                    fileListViewModel.cancelDownloadFiles()
                    safeNavigate(SelectFolderFragmentDirections.fileListFragmentToFileListFragment(file.id, file.name))
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            with(requireActivity() as SelectFolderActivity) {
                showSaveButton()
                val currentFolderRights = FileController.getFileById(folderId, userDrive)?.rights ?: Rights()
                val enable = folderId != selectFolderViewModel.disableSelectedFolderId
                        && (currentFolderRights.canMoveInto || currentFolderRights.canCreateFile)
                enableSaveButton(enable)
            }
        }
    }

    private fun onBackPressed() {
        if (folderId == ROOT_ID) requireActivity().finish() else Utils.ignoreCreateFolderBackStack(findNavController(), true)
    }

    private inner class SetNoFilesLayout : () -> Unit {
        override fun invoke() {
            noFilesLayout.setup(
                title = R.string.noFilesDescriptionSelectFolder,
                initialListView = fileRecyclerView
            )
        }
    }
}
