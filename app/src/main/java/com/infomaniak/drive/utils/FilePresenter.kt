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
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.dd.plist.NSDictionary
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.utils.SnackbarUtils.showSnackbar
import com.infomaniak.core.legacy.utils.UtilsUi.openUrl
import com.infomaniak.core.legacy.utils.capitalizeFirstChar
import com.infomaniak.core.legacy.utils.safeNavigate
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.MatomoDrive.MatomoCategory
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Companion.getCloudAndFileUris
import com.infomaniak.drive.data.models.FileListNavigationType
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.MainViewModel
import com.infomaniak.drive.ui.bottomSheetDialogs.AccessDeniedBottomSheetDialogArgs
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.ui.fileList.DownloadProgressDialogArgs
import com.infomaniak.drive.ui.fileList.FileAdapter
import com.infomaniak.drive.ui.fileList.FileListFragmentArgs
import com.infomaniak.drive.ui.fileList.FileListViewModel
import com.infomaniak.drive.ui.menu.TrashFragmentArgs
import com.infomaniak.drive.ui.publicShare.PublicShareListFragmentArgs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Responsible for file opening actions from [com.infomaniak.drive.ui.fileList.FileListFragment]
 */
object FilePresenter {

    const val TAG = "FilePresenter"

    fun Fragment.openFolder(
        navigationType: FileListNavigationType,
        shouldHideBottomNavigation: Boolean,
        shouldShowSmallFab: Boolean,
        fileListViewModel: FileListViewModel,
    ) {
        val file = when (navigationType) {
            is FileListNavigationType.Folder -> navigationType.file
            is FileListNavigationType.Subfolder -> navigationType.file
        }

        if (file.isDisabled() && !file.isPublicShared()) {
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
            if (file.isPublicShared()) {
                val args = PublicShareListFragmentArgs(fileId = file.id, fileName = file.getDisplayName(requireContext()))
                safeNavigate(R.id.publicShareListFragment, args.toBundle())
            } else if (file.isTrashed()) {
                var args: TrashFragmentArgs? = null
                if (navigationType is FileListNavigationType.Subfolder) {
                    args = TrashFragmentArgs(subfolderId = navigationType.subfolderId)
                }
                safeNavigate(R.id.trashFragment, args?.toBundle())
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

    fun Fragment.displayFile(file: File, mainViewModel: MainViewModel, fileAdapter: FileAdapter?) {
        trackEvent(MatomoCategory.Preview.value, "preview${file.getFileType().value.capitalizeFirstChar()}")
        val fileList = fileAdapter?.getFileObjectsList(mainViewModel.realm) ?: listOf(file)
        Utils.displayFile(mainViewModel, findNavController(), file, fileList)
    }

    fun Fragment.openBookmark(file: File) {
        trackFileActionEvent(MatomoName.OpenBookmark)
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

    fun Fragment.openBookmarkIntent(file: File) = lifecycleScope.launch {
        runCatching {
            val storedFileUri = file.getCloudAndFileUris(requireContext()).second
            requireActivity().openBookmarkIntent(file.name, storedFileUri)
        }.cancellable().onFailure { exception ->
            SentryLog.e(TAG, "Error opening an url link", exception)
            activity?.showSnackbar(
                title = getString(R.string.errorGetBookmarkURL),
                anchor = (activity as MainActivity).getMainFab(),
            )
        }
    }

    suspend fun Context.openBookmarkIntent(fileName: String, uri: Uri) {
        val url = withContext(Dispatchers.IO) {
            if (fileName.isUrlFile()) {
                getUrlFromUrlFile(context = this@openBookmarkIntent, uri)
            } else {
                getUrlFromWebloc(context = this@openBookmarkIntent, uri)
            }
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
