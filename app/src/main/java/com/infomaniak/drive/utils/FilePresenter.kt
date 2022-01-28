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
package com.infomaniak.drive.utils

import android.content.Context
import android.net.Uri
import androidx.fragment.app.Fragment
import com.dd.plist.NSDictionary
import com.dd.plist.NSString
import com.dd.plist.PropertyListParser
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.Companion.getCloudAndFileUris
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.fileList.FileListFragmentDirections
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.activity_main.*
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.InputStreamReader

//TODO Making migration of others actions as openFolder, or preview
/**
 * Responsible for file opening actions from [com.infomaniak.drive.ui.fileList.FileListFragment]
 */
object FilePresenter {

    fun Fragment.openBookmark(file: File) {
        if (file.canUseStoredFile(requireContext())) {
            openBookmarkIntent(file)
        } else {
            safeNavigate(
                FileListFragmentDirections.actionFileListFragmentToDownloadProgressDialog(
                    fileID = file.id, fileName = file.name, userDrive = UserDrive(), isOpenBookmark = true
                )
            )
        }
    }

    fun Fragment.openBookmarkIntent(file: File) {
        runCatching {
            val storedFileUri = file.getCloudAndFileUris(requireContext()).second
            val url =
                if (file.name.endsWith(".url")) getUrlFromUrlFile(requireContext(), storedFileUri)
                else getUrlFromWebloc(requireContext(), storedFileUri)

            if (url.isValidUrl()) requireContext().openUrl(url) else Exception("It's not a valid url")
        }.onFailure {
            requireActivity()
                .showSnackbar(requireContext().getString(R.string.errorGetBookmarkURL), anchorView = requireActivity().mainFab)
        }
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