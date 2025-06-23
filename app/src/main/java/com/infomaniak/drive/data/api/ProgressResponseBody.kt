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
package com.infomaniak.drive.data.api

import okhttp3.ResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer

class ProgressResponseBody(val responseBody: ResponseBody, val progressListener: ProgressListener) : ResponseBody() {

    private val bufferedSource: BufferedSource by lazy { source(responseBody.source()).buffer() }

    override fun contentType() = responseBody.contentType()

    override fun contentLength() = responseBody.contentLength()

    override fun source() = bufferedSource

    private fun source(source: Source): Source {
        return object : ForwardingSource(source) {
            var totalBytesRead = 0L

            override fun read(sink: Buffer, byteCount: Long): Long {
                val bytesRead: Long = super.read(sink, byteCount)
                // read() returns the number of bytes read, or -1 if this source is exhausted.
                totalBytesRead += if (bytesRead != -1L) bytesRead else 0
                progressListener.update(totalBytesRead, responseBody.contentLength(), bytesRead == -1L)
                return bytesRead
            }

        }
    }

    interface ProgressListener {
        fun update(bytesRead: Long, contentLength: Long, done: Boolean)
    }
}
