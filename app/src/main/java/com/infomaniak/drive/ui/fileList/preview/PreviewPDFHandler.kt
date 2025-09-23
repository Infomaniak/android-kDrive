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
package com.infomaniak.drive.ui.fileList.preview

import android.content.Context
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.SyncUtils.uploadFolder
import com.infomaniak.lib.core.utils.getFileNameAndSize

class PreviewPDFHandler(
    context: Context,
    val externalFileUri: Uri? = null,
    private val setPrintVisibility: (isGone: Boolean) -> Unit,
) {

    val fileSize: Long by lazy { fileNameAndSize?.second ?: 0L }

    var pdfViewPrintListener: PDFPrintListener? = null
    var isPasswordProtected = false

    private val fileNameAndSize: Pair<String, Long>? by lazy {
        externalFileUri?.let(context::getFileNameAndSize)
    }

    var fileName: String = fileNameAndSize?.first ?: ""

    fun shouldHidePrintOption(isGone: Boolean) {
        setPrintVisibility(isGone)
    }

    fun printClicked(
        context: Context,
        onDefaultCase: (() -> Unit)? = null,
        onError: () -> Unit,
    ) {
        when {
            isPasswordProtected -> pdfViewPrintListener?.generatePagesAsBitmaps(fileName)
            externalFileUri != null -> {
                val fileToPrint = getFileForPrint(context, externalFileUri, onError) ?: return
                val printManager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                val printAdapter = PDFDocumentAdapter(fileName, fileToPrint)
                printManager.print(fileName, printAdapter, PrintAttributes.Builder().build())
            }
            else -> onDefaultCase?.invoke()
        }
    }

    fun isExternalFile() = externalFileUri != null

    private fun getFileForPrint(context: Context, uri: Uri, onError: () -> Unit): IOFile? {
        return runCatching {
            IOFile(context.uploadFolder, uri.hashCode().toString()).apply {
                if (exists()) delete()
                createNewFile()
                context.contentResolver?.openInputStream(uri)?.use { inputStream ->
                    outputStream().use(inputStream::copyTo)
                }
            }
        }.onFailure {
            onError()
            SentryLog.e(tag = TAG, msg = "Exception while printing a PDF", throwable = it)
        }.getOrNull()
    }

    companion object {
        private const val TAG = "PreviewPDFHandler"
    }
}
