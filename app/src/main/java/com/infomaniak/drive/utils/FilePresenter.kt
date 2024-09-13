/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
package com.infomaniak.drive.utils

import android.content.Context
import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.dd.plist.NSDictionary
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Companion.getCloudAndFileUris
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.AccessDeniedBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.DownloadProgressDialogArgs
import com.infomaniak.drive.ui.fileList.FileAdapter
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.ui.publicShare.PublicShareListFragmentArgs
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.capitalizeFirstChar
import com.infomaniak.lib.core.utils.safeNavigate
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

// TODO Making migration of others actions as openFolder, or preview
/**
 * Responsible for file opening actions from [com.infomaniak.drive.ui.fileList.FileListFragment]
 */
object FilePresenter {

    fun Fragment.openFolder(
        file: File,
        shouldHideBottomNavigation: Boolean,
        shouldShowSmallFab: Boolean,
        fileListViewModel: FileListViewModel,
        isPublicSharedFile: Boolean = false
    ) {
        if (file.isDisabled() && !isPublicSharedFile) {
            safeNavigate(
                R.id.accessDeniedBottomSheetFragment,
                AccessDeniedBottomSheetDialogArgs(
                    isAdmin = AccountUtils.getCurrentDrive()?.isUserAdmin() ?: false,
                    folderId = file.id,
                    folderName = file.name,
                ).toBundle()
            )
        } else {
            fileListViewModel.cancelDownloadFiles()
            if (isPublicSharedFile) {
                val args = PublicShareListFragmentArgs(fileId = file.id, fileName = file.getDisplayName(requireContext()))
                safeNavigate(R.id.publicShareListFragment, args.toBundle())
            } else {
                val args = FileListFragmentArgs(
                    folderId = file.id,
                    folderName = file.getDisplayName(requireContext()),
                    shouldHideBottomNavigation = shouldHideBottomNavigation,
                    shouldShowSmallFab = shouldShowSmallFab,
                )
                safeNavigate(R.id.fileListFragment, args.toBundle())
            }
        }
    }

    fun Fragment.displayFile(file: File, mainViewModel: MainViewModel, fileAdapter: FileAdapter?, publicShareUuid: String = "") {
        trackEvent("preview", "preview${file.getFileType().value.capitalizeFirstChar()}")
        val fileList = fileAdapter?.getFileObjectsList(mainViewModel.realm) ?: listOf(file)
        Utils.displayFile(mainViewModel, findNavController(), file, fileList, publicShareUuid = publicShareUuid)
    }

    fun Fragment.openBookmark(file: File) {
        if (file.canUseStoredFile(requireContext())) {
            openBookmarkIntent(file)
        } else {
            safeNavigate(
                R.id.downloadProgressDialog,
                DownloadProgressDialogArgs(
                    fileId = file.id,
                    fileName = file.name,
                    userDrive = UserDrive(),
                    action = DownloadAction.OPEN_BOOKMARK,
                ).toBundle(),
            )
        }
    }

    fun Fragment.openBookmarkIntent(file: File) {
        runCatching {
            val storedFileUri = file.getCloudAndFileUris(requireContext()).second
            requireActivity().openBookmarkIntent(file.name, storedFileUri)
        }.onFailure {
            requireActivity().showSnackbar(
                title = getString(R.string.errorGetBookmarkURL),
                anchor = (requireActivity() as MainActivity).getMainFab(),
            )
        }
    }

    fun Context.openBookmarkIntent(fileName: String, uri: Uri) {
        val url = if (fileName.endsWith(".url")) {
            getUrlFromUrlFile(context = this, uri)
        } else {
            getUrlFromWebloc(context = this, uri)
        }

        if (url.isValidUrl()) openUrl(url) else throw Exception("It's not a valid url")
    }

    private fun getUrlFromUrlFile(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use {
            BufferedReader(InputStreamReader(it)).useLines { lines ->
                lines.forEach { line ->
                    if (line.contains("URL=")) {
                        return@useLines line.substringAfter("URL=")
                    }
                }
                ""
            }
        } ?: ""
    }

    private fun getUrlFromWebloc(context: Context, uri: Uri): String {
        return context.contentResolver.openInputStream(uri)?.use { inputStream ->
            with(PropertyListParser.parse(BufferedInputStream(inputStream)) as NSDictionary) {
                (this["URL"] as NSString).toString()
            }
        } ?: ""
    }
}
