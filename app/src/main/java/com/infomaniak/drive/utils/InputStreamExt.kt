/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.InputStream
import java.io.OutputStream

suspend fun InputStream.copyToCancellable(out: OutputStream, bufferSize: Int = DEFAULT_BUFFER_SIZE): Long {
    currentCoroutineContext().ensureActive()
    var bytesCopied: Long = 0
    val buffer = ByteArray(bufferSize)
    var bytes = read(buffer)
    while (bytes >= 0) {
        currentCoroutineContext().ensureActive()
        out.write(buffer, 0, bytes)
        bytesCopied += bytes
        currentCoroutineContext().ensureActive()
        bytes = read(buffer)
    }
    currentCoroutineContext().ensureActive()
    return bytesCopied
}
