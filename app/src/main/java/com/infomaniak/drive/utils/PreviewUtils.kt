/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.infomaniak.drive.MatomoDrive.trackFileActionEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.ui.fileList.preview.BitmapPrintDocumentAdapter
import com.infomaniak.drive.ui.fileList.preview.PDFDocumentAdapter
import com.infomaniak.drive.utils.PreviewPDFUtils.downloadFile
import com.infomaniak.drive.utils.Utils.openWith
import com.infomaniak.drive.utils.Utils.openWithIntentExceptkDrive
import com.infomaniak.lib.core.utils.lightNavigationBar

fun Activity.setupBottomSheetFileBehavior(
    bottomSheetBehavior: BottomSheetBehavior<View>,
    isDraggable: Boolean,
    isFitToContents: Boolean = false,
) {
    setColorNavigationBar(true)
    bottomSheetBehavior.apply {
        isHideable = true
        this.isDraggable = isDraggable
        this.isFitToContents = isFitToContents
        addBottomSheetCallback(object : BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                when (bottomSheetBehavior.state) {
                    BottomSheetBehavior.STATE_HIDDEN -> {
                        window?.navigationBarColor =
                            ContextCompat.getColor(this@setupBottomSheetFileBehavior, R.color.previewBackgroundTransparent)
                        window?.lightNavigationBar(false)
                    }
                    else -> {
                        setColorNavigationBar(true)
                    }
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) = Unit
        })
    }
}

fun Context.saveToKDrive(externalFileUri: Uri) {
    trackFileActionEvent("saveToKDrive")

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
    onDownloadFile: (() -> Unit)? = null,
) {
    trackFileActionEvent("openWith")

    ownerFragment?.apply {
        // This is only for fragments. For activities, the snackbar is shown in the openWith method.
        if (requireContext().openWithIntentExceptkDrive(currentFile!!).resolveActivity(requireContext().packageManager) == null) {
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

fun convertFileToIOFile(
    fileModel: File,
    cacheFile: IOFile,
    shouldBePdf: Boolean = false,
    onProgress: (progress: Int) -> Unit,
): IOFile {
    val fileNeedDownload = if (fileModel.isOnlyOfficePreview()) {
        fileModel.isObsolete(cacheFile)
    } else {
        fileModel.isObsoleteOrNotIntact(cacheFile)
    }

    if (fileNeedDownload) {
        downloadFile(cacheFile, fileModel, shouldBePdf, onProgress)
        cacheFile.setLastModified(fileModel.getLastModifiedInMilliSecond())
    }

    return cacheFile
}