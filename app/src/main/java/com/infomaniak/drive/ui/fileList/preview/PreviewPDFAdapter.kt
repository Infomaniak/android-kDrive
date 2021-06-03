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
package com.infomaniak.drive.ui.fileList.preview

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.PdfCore
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_pdfview.view.*

class PreviewPDFAdapter(private val pdfCore: PdfCore) : RecyclerView.Adapter<ViewHolder>() {

    private var whiteBitmap: Bitmap

    init {
        whiteBitmap = createWhiteBitmap()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_pdfview, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.itemView.apply {
            imageView.setImageBitmap(whiteBitmap)
            pdfCore.renderPage(position) { bitmap ->
                imageView.setImageBitmap(bitmap)
            }
        }
    }

    private fun createWhiteBitmap(): Bitmap {
        val bitmap = Bitmap.createBitmap(
            pdfCore.bitmapWidth, pdfCore.bitmapHeight, Bitmap.Config.ARGB_8888
        )

        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        return bitmap
    }

    override fun getItemCount() = pdfCore.getPdfPages()
}