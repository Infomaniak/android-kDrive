/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2024 Infomaniak Network SA
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

import kotlin.math.ceil
import kotlin.math.floor

class FileChunkSizeManager(
    private val chunkMinSize: Long = CHUNK_MIN_SIZE,
    private val chunkMaxSize: Long = CHUNK_MAX_SIZE,
    private val optimalTotalChunks: Int = OPTIMAL_TOTAL_CHUNKS,
    private val maxChunkCount: Int = MAX_CHUNK_COUNT,
    private val maxParallelChunks: Int = MAX_PARALLEL_CHUNKS,
) {

    /**
     * Calculates the size of a chunk from the size of a file within a limit between [chunkMinSize] and [chunkMaxSize],
     * taking into account the memory available to the application.
     * If the limit is exceeded, an [AllowedFileSizeExceededException] is thrown, otherwise the chunk size is returned.
     *
     * @return The file chunk size otherwise raises an [AllowedFileSizeExceededException]
     *
     * @throws AllowedFileSizeExceededException
     */
    fun computeChunkSize(fileSize: Long): Long {

        var fileChunkSize = ceil(fileSize.toDouble() / optimalTotalChunks).toLong()

        if (fileChunkSize > chunkMaxSize) {
            fileChunkSize = ceil(fileSize.toDouble() / maxChunkCount).toLong()
        }

        if (fileChunkSize > chunkMaxSize) throw AllowedFileSizeExceededException()

        return adjustChunkSizeByAvailableMemory(fileChunkSize.coerceAtLeast(chunkMinSize))
    }

    fun computeFileChunks(fileSize: Long, fileChunkSize: Long): Int = ceil(fileSize.toDouble() / fileChunkSize).toInt()

    fun computeParallelChunks(fileChunkSize: Long): Int {
        val halfAvailableMemory = getHalfHeapMemory().toDouble()
        return floor(halfAvailableMemory / fileChunkSize).toInt().coerceIn(1, maxParallelChunks)
    }

    private fun adjustChunkSizeByAvailableMemory(fileChunkSize: Long): Long = fileChunkSize.coerceAtMost(getHalfHeapMemory())

    private fun getHalfHeapMemory(): Long = Runtime.getRuntime().maxMemory() / 2

    class AllowedFileSizeExceededException : Exception()

    companion object {
        const val CHUNK_MIN_SIZE = 1L * 1024 * 1024 // 1Mo
        private const val CHUNK_MAX_SIZE = 50L * 1024 * 1024 // 50 Mo and max file size to upload 500Gb
        private const val OPTIMAL_TOTAL_CHUNKS = 200
        private const val MAX_CHUNK_COUNT = 10_000
        private const val MAX_PARALLEL_CHUNKS = 4
    }

}
