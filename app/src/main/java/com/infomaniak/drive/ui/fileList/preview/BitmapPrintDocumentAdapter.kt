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
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.pdf.PrintedPdfDocument
import java.io.FileOutputStream

class BitmapPrintDocumentAdapter(
    private val context: Context,
    private val fileName: String,
    private val bitmaps: List<Bitmap>
) : PrintDocumentAdapter() {

    private var pageHeight: Int = 0
    private var pageWidth: Int = 0
    private var pdfDocument: PrintedPdfDocument? = null

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }

        pageWidth = newAttributes?.mediaSize?.widthMils ?: 0
        pageHeight = newAttributes?.mediaSize?.heightMils ?: 0

        pdfDocument = PrintedPdfDocument(context, newAttributes!!)

        val printInfo = PrintDocumentInfo.Builder(fileName)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(bitmaps.size)
            .build()

        callback?.onLayoutFinished(printInfo, true)
    }

    override fun onWrite(
        pages: Array<PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onWriteCancelled()
            return
        }

        bitmaps.forEachIndexed { index, bitmap ->
            pdfDocument?.startPage(index)?.also { page ->
                val contentRect = RectF(page.info.contentRect)
                val matrix = getMatrix(bitmap.width, bitmap.height, contentRect)
                matrix.postTranslate(contentRect.left, contentRect.top)
                page.canvas.clipRect(contentRect)
                page.canvas.drawBitmap(bitmap, matrix, null)
                pdfDocument?.finishPage(page)
            }
        }

        runCatching {
            pdfDocument?.writeTo(FileOutputStream(destination?.fileDescriptor))
        }.onFailure { exception ->
            callback?.onWriteFailed(exception.toString())
            return
        }.getOrElse {
            pdfDocument?.close()
            pdfDocument = null
        }

        callback?.onWriteFinished(pages)
    }

    private fun getMatrix(imageWidth: Int, imageHeight: Int, content: RectF) = Matrix().apply {
        val scale = (content.width() / imageWidth).coerceAtMost(content.height() / imageHeight)

        postScale(scale, scale)

        val translateX = ((content.width() - imageWidth * scale) / 2)
        val translateY = ((content.height() - imageHeight * scale) / 2)
        postTranslate(translateX, translateY)

        return this
    }
}
