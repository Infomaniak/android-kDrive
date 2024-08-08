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
package com.infomaniak.drive.ui.fileList.preview

import android.app.Application
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.MainApplication
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.utils.saveToKDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PreviewSliderViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = getApplication<MainApplication>()

    val pdfIsDownloading = MutableLiveData<Boolean>()
    var currentPreview: File? = null
    var userDrive = UserDrive()
    var shareLinkUuid = ""

    fun saveToDrive(onDownloadProgress: () -> Unit, onDownloadError: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                val cacheFile = currentPreview!!.convertToIOFile(appContext, userDrive) {
                    viewModelScope.launch(Dispatchers.Main) {
                        onDownloadProgress()
                    }
                }

                val uri = FileProvider.getUriForFile(appContext, appContext.getString(R.string.FILE_AUTHORITY), cacheFile)
                appContext.saveToKDrive(uri)
            }.onFailure { exception ->
                exception.printStackTrace()
                onDownloadError()
            }
        }
    }
}
