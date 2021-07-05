/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.DisplayMetrics
import kotlinx.coroutines.*
import java.io.File
import kotlin.coroutines.CoroutineContext

class PdfCore(private val context: Context, private var file: File) : CoroutineScope {
    private lateinit var pdfRenderer: PdfRenderer

    var bitmapWidth: Int = 0
    var bitmapHeight: Int = 0

    private val job = SupervisorJob()
    override val coroutineContext: CoroutineContext get() = job + Dispatchers.IO

    init {
        openPdfFile()
        bitmapWidth = getDisplayWidth()
        pdfRenderer.openPage(0).use {
            bitmapHeight = (bitmapWidth.toFloat() / it.width * it.height).toInt()
        }
    }

    fun refreshFile(newFile: File) {
        this.file = newFile
        openPdfFile()
    }

    fun clear() {
        try {
            coroutineContext.cancel()
            pdfRenderer.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getPdfPages() = pdfRenderer.pageCount

    fun renderPage(page: Int, ready: ((bitmap: Bitmap?) -> Unit)) = launch(coroutineContext) {
        if (page < getPdfPages()) {
            synchronized(this@PdfCore) {
                buildBitmap(page) {
                    launch(Dispatchers.Main) {
                        ready.invoke(it)
                    }
                }
            }
        }
    }

    private fun openPdfFile() {
        runCatching { ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY) }
            .onSuccess { pdfRenderer = PdfRenderer(it) }
            .onFailure { it.printStackTrace() }
    }

    private fun buildBitmap(page: Int, onBitmap: (Bitmap?) -> Unit) {
        val bitmap = try {
            pdfRenderer.openPage(page).renderBitmap()
        } catch (e: Exception) {
            null
        }
        onBitmap(bitmap)
    }

    private fun PdfRenderer.Page.renderBitmap() = use {
        val bitmap = createBitmap()
        render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        bitmap
    }

    private fun PdfRenderer.Page.createBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            bitmapWidth, (bitmapWidth.toFloat() / width * height).toInt(), Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        canvas.drawBitmap(bitmap, 0f, 0f, null)
        return bitmap
    }

    private fun getDisplayWidth(): Int {
        val displayMetrics: DisplayMetrics = context.resources.displayMetrics
        return displayMetrics.widthPixels
    }

}