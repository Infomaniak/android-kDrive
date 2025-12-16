/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.view.View
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.JsonParser
import com.infomaniak.core.auth.networking.HttpClient
import com.infomaniak.core.extensions.isNightModeEnabled
import com.infomaniak.core.extensions.lightStatusBar
import com.infomaniak.core.network.utils.bodyAsStringOrNull
import com.infomaniak.drive.MatomoDrive.MatomoName
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.MatomoDrive.trackPdfActivityActionEvent
import com.infomaniak.drive.MatomoDrive.trackPublicShareActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.ui.fileList.DownloadProgressViewModel.Companion.PROGRESS_COMPLETE
import com.infomaniak.drive.ui.fileList.preview.BitmapPrintDocumentAdapter
import com.infomaniak.drive.ui.fileList.preview.PDFDocumentAdapter
import com.infomaniak.drive.utils.PreviewPDFUtils.PasswordProtectedException
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.Utils.openWithIntentExceptkDrive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.invoke
import okhttp3.Response
import java.io.BufferedInputStream
import com.infomaniak.core.network.networking.HttpClient.okHttpClient as unauthenticatedHttpClient

private const val BUFFER_SIZE = 8192

fun Activity.setupBottomSheetFileBehavior(
    bottomSheetBehavior: BottomSheetBehavior<View>,
    isDraggable: Boolean,
    isFitToContents: Boolean = false,
) = with(bottomSheetBehavior) {
    isHideable = true

    this.isDraggable = isDraggable
    this.isFitToContents = isFitToContents

    // Status bar need to be light when the bottom sheet is expanded for preview fragments
    addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            window.lightStatusBar(!isNightModeEnabled() && bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED)
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
    })
}

fun Context.saveToKDrive(externalFileUri: Uri) {

    Intent(this, SaveExternalFilesActivity::class.java).apply {
        action = Intent.ACTION_SEND
        putExtra(Intent.EXTRA_STREAM, externalFileUri)
        putExtras(
            SaveExternalFilesActivityArgs(
                userId = AccountUtils.currentUserId,
                driveId = AccountUtils.currentDriveId,
            ).toBundle()
        )
        type = "/pdf"
        startActivity(this)
    }
}

fun Context.openWith(
    ownerFragment: Fragment? = null,
    currentFile: File? = null,
    externalFileUri: Uri? = null,
    userDrive: UserDrive = UserDrive(),
    onDownloadFile: (() -> Unit)? = null,
) {
    when {
        currentFile?.isPublicShared() == true -> trackPublicShareActionEvent(MatomoName.OpenWith)
        externalFileUri != null -> trackPdfActivityActionEvent(MatomoName.OpenWith)
        else -> trackFileActionEvent(MatomoName.OpenWith)
    }

    ownerFragment?.apply {
        // This is only for fragments. For activities, the snackbar is shown in the openWith method.
        val openWithIntent = requireContext().openWithIntentExceptkDrive(currentFile!!, userDrive)
        if (openWithIntent.resolveActivity(requireContext().packageManager) == null) {
            showSnackbar(R.string.errorNoSupportingAppFound, showAboveFab = true)
            findNavController().popBackStack()
        } else {
            onDownloadFile?.invoke()
        }
    } ?: run {
        externalFileUri?.apply {
            openWith(this, contentResolver.getType(this), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }
}

fun Context.printPdf(fileName: String, bitmaps: List<Bitmap>) {
    printPdf(
        file = null,
        fileName = fileName,
        bitmaps = bitmaps,
        onDownloadFile = null,
    )
}

fun Context.printPdf(file: IOFile) {
    printPdf(
        file = file,
        fileName = null,
        bitmaps = null,
        onDownloadFile = null,
    )
}

fun Context.printPdf(onDownloadFile: (() -> Unit)) {
    printPdf(
        file = null,
        fileName = null,
        bitmaps = null,
        onDownloadFile = onDownloadFile,
    )
}

private fun Context.printPdf(
    file: IOFile?,
    fileName: String?,
    bitmaps: List<Bitmap>?,
    onDownloadFile: (() -> Unit)?,
) {
    val printManager by lazy { getSystemService(Context.PRINT_SERVICE) as PrintManager }
    val printAttributes by lazy { PrintAttributes.Builder().build() }

    fun printFile() = file?.apply {
        val printAdapter = PDFDocumentAdapter(name, file)
        printManager.print(name, printAdapter, printAttributes)
    }

    fun printBitmaps() = bitmaps?.apply {
        val printAdapter = BitmapPrintDocumentAdapter(applicationContext, fileName = fileName!!, bitmaps = this)
        printManager.print(fileName, printAdapter, printAttributes)
    }

    when {
        onDownloadFile != null -> onDownloadFile()
        bitmaps.isNullOrEmpty().not() -> printBitmaps()
        file != null -> printFile()
    }
}

suspend fun downloadFile(
    externalOutputFile: IOFile,
    file: File,
    shouldBePdf: Boolean,
    onProgress: (progress: Int) -> Unit,
    isPublicShared: Boolean,
) {
    Dispatchers.IO { if (externalOutputFile.exists()) externalOutputFile.delete() }
    val downloadUrl = ApiRoutes.getDownloadFileUrl(file) + if (file.isOnlyOfficePreview()) "?as=pdf" else ""
    val downloadProgressInterceptor = DownloadOfflineFileManager.downloadProgressInterceptor(onProgress = onProgress)
    val okHttpClient = when {
        isPublicShared -> unauthenticatedHttpClient
        else -> HttpClient.okHttpClientWithTokenInterceptor
    }

    DownloadOfflineFileManager.downloadFileResponseAsync(
        fileUrl = downloadUrl,
        downloadInterceptor = downloadProgressInterceptor,
        okHttpClient = okHttpClient,
    ).use {
        if (!it.isSuccessful) {
            val errorCode = JsonParser.parseString(it.bodyAsStringOrNull()).asJsonObject.getAsJsonPrimitive("error").asString
            if (errorCode == "password_protected_error") {
                throw PasswordProtectedException()
            } else {
                throw Exception("Download error")
            }
        }

        if (shouldBePdf && it.body?.contentType()?.toString() != "application/pdf") {
            throw UnsupportedOperationException("File not supported")
        }

        createTempFile(it, externalOutputFile)
        onProgress(PROGRESS_COMPLETE)
    }
}

private suspend fun createTempFile(response: Response, file: IOFile) = Dispatchers.IO {
    BufferedInputStream(response.body?.byteStream(), BUFFER_SIZE).use { input ->
        file.outputStream().use { output -> input.copyTo(output, BUFFER_SIZE) }
    }
}
