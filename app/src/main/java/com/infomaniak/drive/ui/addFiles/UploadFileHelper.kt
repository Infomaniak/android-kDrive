/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2023 Infomaniak Network SA
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

import android.net.Uri
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.navigation.fragment.findNavController
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.lib.core.utils.FilePicker

class UploadFileHelper(private val onOpeningPicker: () -> Unit, private val onResult: (List<Uri>) -> Unit) {

    private lateinit var filePicker: FilePicker
    private lateinit var uploadFilesPermissions: DrivePermissions

    constructor(
        fragment: Fragment,
        onOpeningPicker: () -> Unit,
        onResult: (List<Uri>) -> Unit,
    ) : this(onOpeningPicker, onResult) {
        filePicker = FilePicker(fragment)
        uploadFilesPermissions = DrivePermissions().apply {
            registerPermissions(fragment) { authorized -> if (authorized) uploadFiles() }
        }
    }

    constructor(
        activity: FragmentActivity,
        onOpeningPicker: () -> Unit,
        onResult: (List<Uri>) -> Unit,
    ) : this(onOpeningPicker, onResult) {
        filePicker = FilePicker(activity)
        uploadFilesPermissions = DrivePermissions().apply {
            registerPermissions(activity) { authorized -> if (authorized) uploadFiles() }
        }
    }

    fun uploadFiles() {
        if (uploadFilesPermissions.checkSyncPermissions()) {
            onOpeningPicker()
            filePicker.open(callback = onResult)
        }
    }
}
