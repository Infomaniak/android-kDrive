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
import com.infomaniak.drive.ui.fileList.SelectFolderActivity.SaveExternalViewModel
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.utils.safeNavigate
import com.infomaniak.drive.utils.showSnackbar
import kotlinx.android.synthetic.main.fragment_file_list.*

class SelectFolderFragment : FileListFragment() {

    private val saveExternalViewModel: SaveExternalViewModel by activityViewModels()
    private val navigationArgs: SelectFolderFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override var showPendingFiles: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        userDrive = saveExternalViewModel.userDrive
        super.onViewCreated(view, savedInstanceState)

        folderName = if (folderId == ROOT_ID) saveExternalViewModel.currentDrive?.name ?: "/" else navigationArgs.folderName

        collapsingToolbarLayout.title = getString(R.string.selectFolderTitle)

        toolbar.menu.apply {
            findItem(R.id.addFolderItem).apply {
                setOnMenuItemClickListener {
                    val selectFolderActivity = requireActivity() as? SelectFolderActivity
                    if (FileController.getFileById(folderId, userDrive)?.rights?.newFolder == true) {
                        selectFolderActivity?.hideSaveButton()
                        safeNavigate(
                            SelectFolderFragmentDirections.actionSelectFolderFragmentToNewFolderFragment(
                                parentFolderId = folderId,
                                userDrive = userDrive
                            )
                        )
                    } else {
                        selectFolderActivity?.showSnackbar(R.string.allFileAddRightError)
                    }
                    true
                }
                isVisible = true
            }
        }

        toolbar.setNavigationOnClickListener {
            onBackPressed()
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            onBackPressed()
        }

        fileAdapter.selectFolder = true
        fileAdapter.onFileClicked = { file ->
            when {
                file.isFolder() -> {
                    if (!file.isDisabled()) {
                        fileListViewModel.cancelDownloadFiles()
                        safeNavigate(
                            SelectFolderFragmentDirections.fileListFragmentToFileListFragment(
                                folderId = file.id,
                                folderName = file.name
                            )
                        )
                    }
                }
            }
        }
        lifecycleScope.launchWhenResumed {
            val selectFolderActivity = requireActivity() as SelectFolderActivity
            selectFolderActivity.showSaveButton()

            val currentFolder = FileController.getFileById(folderId, userDrive)
            val enable = folderId != saveExternalViewModel.disableSelectedFolder &&
                    (currentFolder?.rights?.moveInto != false || currentFolder.rights?.newFile != false)
            selectFolderActivity.enableSaveButton(enable)
        }
    }

    private fun onBackPressed() {
        if (folderId == ROOT_ID) {
            requireActivity().finish()
        } else Utils.ignoreCreateFolderBackStack(findNavController(), true)

    }
}