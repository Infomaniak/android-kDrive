/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023-2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.addFiles

import android.content.Context
import android.net.Uri
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.lib.core.utils.FilePicker

class UploadFilesHelper private constructor(
    private val context: Context,
    private val navController: NavController,
    private val onOpeningPicker: (() -> Unit)?,
    private val onResult: (() -> Unit)?,
) {

    private lateinit var filePicker: FilePicker
    private lateinit var uploadFilesPermissions: DrivePermissions
    private lateinit var parentFolder: File

    constructor(
        activity: FragmentActivity,
        navController: NavController,
        onOpeningPicker: (() -> Unit)? = null,
        onResult: (() -> Unit)? = null,
    ) : this(context = activity, navController, onOpeningPicker = onOpeningPicker, onResult = onResult) {

        filePicker = FilePicker(activity).apply { initCallback(::initFilePicker) }

        uploadFilesPermissions = DrivePermissions().apply {
            registerPermissions(activity) { authorized -> if (authorized) uploadFiles() }
        }
    }

    fun initParentFolder(folder: File) {
        parentFolder = folder
    }

    fun uploadFiles() {
        ShortcutManagerCompat.reportShortcutUsed(context, Shortcuts.UPLOAD.name)
        if (uploadFilesPermissions.checkSyncPermissions()) {
            onOpeningPicker?.invoke()
            filePicker.open()
        }
    }

    private fun initFilePicker(uris: List<Uri>) {
        onResult?.invoke()
        onSelectFilesResult(uris)
    }

    private fun onSelectFilesResult(uris: List<Uri>) {
        navController.navigate(
            resId = R.id.importFileDialog,
            args = ImportFilesDialogArgs(parentFolder.id, parentFolder.name, parentFolder.driveId, uris.toTypedArray()).toBundle(),
        )
    }
}
