/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
package com.infomaniak.drive.data.api

import android.util.Log
import java.io.OutputStream

internal class ProgressOutputStream(
    private val stream: OutputStream?,
    private val onProgress: (currentBytes: Int, bytesWritten: Long, contentLength: Long) -> Unit,
    private val total: Long
) : OutputStream() {

    private var totalWritten = 0L

    override fun write(bytes: ByteArray, off: Int, len: Int) {
        stream?.write(bytes, off, len)

        if (total < 0) {
            onProgress(-1, -1, -1)
            return
        }
        val written = if (len < bytes.size) {
            len.toLong()
        } else {
            bytes.size.toLong()
        }
        totalWritten += written
        Log.d("ProgressOutputStream", "write > totalWritten:$totalWritten written:$written")
        onProgress(written.toInt(), totalWritten, total)
    }

    override fun write(byte: Int) {
        stream?.write(byte)
        if (total < 0) {
            onProgress(-1, -1, -1)
            return
        }
        totalWritten += byte
        Log.d("ProgressOutputStream", "write > totalWritten:$totalWritten byte:$byte")
        onProgress(byte, totalWritten, total)
    }

    override fun close() {
        stream?.close()
    }

    override fun flush() {
        stream?.flush()
    }

}