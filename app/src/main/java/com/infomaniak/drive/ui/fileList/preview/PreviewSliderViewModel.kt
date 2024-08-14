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

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.fileList.BaseDownloadProgressDialog.DownloadAction
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.printPdf
import com.infomaniak.drive.utils.saveToKDrive
import com.infomaniak.drive.utils.shareFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class PreviewSliderViewModel : ViewModel() {

    val downloadProgressLiveData = MutableLiveData(0)
    val pdfIsDownloading = MutableLiveData<Boolean>()
    var currentPreview: File? = null
    var userDrive = UserDrive()
    var publicShareUuid = ""

    fun executeDownloadAction(
        activityContext: Context,
        downloadAction: DownloadAction,
        navigateToDownloadDialog: suspend () -> Unit,
        onDownloadError: () -> Unit,
    ) = viewModelScope.launch(Dispatchers.IO) {
        runCatching {
            val cacheFile = currentPreview!!.convertToIOFile(
                context = activityContext,
                userDrive = userDrive,
                onProgress = downloadProgressLiveData::postValue,
                navigateToDownloadDialog = navigateToDownloadDialog,
            )

            val uri = FileProvider.getUriForFile(activityContext, activityContext.getString(R.string.FILE_AUTHORITY), cacheFile)

            when (downloadAction) {
                DownloadAction.OPEN_WITH -> {
                    activityContext.openWith(uri, currentPreview!!.getMimeType(), Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                DownloadAction.SEND_COPY -> activityContext.shareFile { uri }
                DownloadAction.SAVE_TO_DRIVE -> activityContext.saveToKDrive(uri)
                DownloadAction.OPEN_BOOKMARK -> TODO()
                DownloadAction.PRINT_PDF -> activityContext.printPdf(cacheFile)
            }
        }.onFailure { exception ->
            downloadProgressLiveData.postValue(null)
            exception.printStackTrace()
            onDownloadError()
        }
    }
}
