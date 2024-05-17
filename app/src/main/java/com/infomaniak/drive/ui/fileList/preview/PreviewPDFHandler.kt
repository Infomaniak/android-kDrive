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
package com.infomaniak.drive.ui.fileList.preview

import android.content.Context
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.lib.core.utils.getFileNameAndSize
import io.sentry.Sentry
import io.sentry.SentryLevel

class PreviewPDFHandler(
    context: Context,
    val externalFileUri: Uri? = null,
    private val setPrintVisibility: (isGone: Boolean) -> Unit,
) {

    val fileName: String by lazy { fileNameAndSize?.first ?: "" }
    val fileSize: Long by lazy { fileNameAndSize?.second ?: 0 }

    var pdfViewPrintListener: PDFPrintListener? = null
    var isPasswordProtected = false

    private val fileNameAndSize: Pair<String, Long>? by lazy {
        externalFileUri?.let { context.getFileNameAndSize(it) }
    }

    fun shouldHidePrintOption(isGone: Boolean) {
        setPrintVisibility(isGone)
    }

    fun printClicked(context: Context, onError: () -> Unit) {
        if (isPasswordProtected) {
            pdfViewPrintListener?.generatePagesAsBitmaps(fileName)
        } else {
            externalFileUri?.let {
                getFileForPrint(context, it, onError)?.let { fileToPrint ->
                    val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                    val printAdapter = PDFDocumentAdapter(fileName, fileToPrint)
                    printManager.print(fileName, printAdapter, PrintAttributes.Builder().build())
                }
            }
        }
    }

    private fun getFileForPrint(context: Context, uri: Uri, onError: () -> Unit): IOFile? {
        return runCatching {
            IOFile(context.uploadFolder, uri.hashCode().toString()).apply {
                if (exists()) delete()
                createNewFile()
                context.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    outputStream().use { inputStream.copyTo(it) }
                }
            }
        }.onFailure {
            onError()
            Sentry.withScope { scope ->
                scope.setExtra("exception", it.stackTraceToString())
                Sentry.captureMessage("Exception while printing a PDF", SentryLevel.ERROR)
            }
        }.getOrNull()
    }
}
