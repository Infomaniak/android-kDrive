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
package com.infomaniak.drive.data.api

import okhttp3.MediaType
import okhttp3.RequestBody
import okhttp3.internal.http.CallServerInterceptor
import okio.BufferedSink
import okio.buffer
import okio.sink
import java.io.IOException


class ProgressRequestBody(
    private val requestBody: RequestBody,
    private val onProgress: (currentBytes: Int, bytesWritten: Long, contentLength: Long) -> Unit
) : RequestBody() {

    @Synchronized
    override fun contentType(): MediaType? {
        return requestBody.contentType()
    }

    @Synchronized
    override fun contentLength(): Long {
        try {
            return requestBody.contentLength()
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return -1
    }

    @Throws(IOException::class)
    override fun writeTo(sink: BufferedSink) {
        val isCalledByCallServerInterceptor = Thread.currentThread().stackTrace.any { stackTraceElement ->
            stackTraceElement.className == CallServerInterceptor::class.java.canonicalName
        }

        if (isCalledByCallServerInterceptor) {
            val progressOutputStream = ProgressOutputStream(sink.outputStream(), onProgress, contentLength())
            val progressSink: BufferedSink = progressOutputStream.sink().buffer()
            requestBody.writeTo(progressSink)
            progressSink.flush()
        }
    }
}