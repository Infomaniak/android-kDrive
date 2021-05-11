/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import androidx.activity.addCallback
import androidx.lifecycle.ViewModelProvider
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

    private lateinit var saveExternalViewModel: SaveExternalViewModel
    private val navigationArgs: SelectFolderFragmentArgs by navArgs()

    override var enabledMultiSelectMode: Boolean = false
    override var hideBackButtonWhenRoot: Boolean = false
    override var showPendingFiles: Boolean = false

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        saveExternalViewModel = ViewModelProvider(requireActivity())[SaveExternalViewModel::class.java]
        userDrive = saveExternalViewModel.userDrive
        super.onActivityCreated(savedInstanceState)

        folderName = if (folderID == ROOT_ID) saveExternalViewModel.currentDrive?.name ?: "/" else navigationArgs.folderName

        collapsingToolbarLayout.title = getString(R.string.selectFolderTitle)

        toolbar.menu.apply {
            findItem(R.id.addFolderItem).apply {
                setOnMenuItemClickListener {
                    val selectFolderActivity = requireActivity() as? SelectFolderActivity
                    if (FileController.getFileById(folderID, userDrive)?.rights?.newFolder == true) {
                        selectFolderActivity?.hideSaveButton()
                        safeNavigate(
                            SelectFolderFragmentDirections.actionSelectFolderFragmentToNewFolderFragment(
                                parentFolderId = folderID,
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
                                folderID = file.id,
                                folderName = file.name,
                                ignoreCreateFolderStack = false
                            )
                        )
                    }
                }
            }
        }
        val selectFolderActivity = requireActivity() as SelectFolderActivity
        selectFolderActivity.showSaveButton()

        val currentFolder = FileController.getFileById(folderID, userDrive)
        val enable = folderID != saveExternalViewModel.disableSelectedFolder &&
                (currentFolder?.rights?.moveInto != false || currentFolder.rights?.newFile != false)
        selectFolderActivity.enableSaveButton(enable)
    }

    private fun onBackPressed() {
        if (folderID == ROOT_ID) {
            requireActivity().finish()
        } else Utils.ignoreCreateFolderBackStack(findNavController(), true)

    }
}