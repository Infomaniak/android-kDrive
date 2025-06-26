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
package com.infomaniak.drive.ui

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.utils.FilePresenter.displayFile
import com.infomaniak.drive.utils.FilePresenter.openFolder

interface FileListNavigatoreObservable {

    fun observeNavigateFileListTo(
        mainViewModel: MainViewModel,
        viewLifecycleOwner: LifecycleOwner,
        fragment: Fragment,
        fileListViewModel: FileListViewModel
    ) {
        mainViewModel.navigateFileListTo.observe(viewLifecycleOwner) { file ->
            if (file.isFolder()) {
                fragment.openFolder(
                    file = file,
                    shouldHideBottomNavigation = false,
                    shouldShowSmallFab = false,
                    fileListViewModel = fileListViewModel,
                )
            } else {
                fragment.displayFile(file, mainViewModel, fileAdapter = null)
            }
        }
    }
}
