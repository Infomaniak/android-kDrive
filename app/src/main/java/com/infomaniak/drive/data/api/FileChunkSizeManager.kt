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
import kotlin.math.min

class FileChunkSizeManager(
    private val chunkMinSize: Long = CHUNK_MIN_SIZE,
    private val chunkMaxSize: Long = CHUNK_MAX_SIZE,
    private val optimalTotalChunks: Int = OPTIMAL_TOTAL_CHUNKS,
    private val maxChunkCount: Int = MAX_CHUNK_COUNT,
    private val maxParallelChunks: Int = MAX_PARALLEL_CHUNKS,
) {

    /**
     * Computes the optimal chunk configuration for file upload.
     *
     * @param fileSize The total size of the file in bytes.
     * @param defaultFileChunkSize An optional predefined chunk size. If provided,
     * it forces the computation based on this value instead of dynamically determining it.
     * @return A [ChunkConfig] containing the computed chunk size, total chunks, and parallel chunk count.
     * @throws IllegalArgumentException If the computed total chunks from [defaultFileChunkSize] exceed [maxChunkCount].
     * @throws AllowedFileSizeExceededException If the file is large enough to chunk with the current config.
     */
    fun computeChunkConfig(fileSize: Long, defaultFileChunkSize: Long? = null): ChunkConfig {
        val halfAvailableMemory = getHalfHeapMemory()
        val fileChunkSize = defaultFileChunkSize ?: computeChunkSize(fileSize, halfAvailableMemory)
        val totalChunks = computeFileChunks(fileSize, fileChunkSize)

        require(totalChunks <= maxChunkCount)

        return ChunkConfig(
            fileChunkSize = computeChunkSize(fileSize, halfAvailableMemory),
            totalChunks = totalChunks,
            parallelChunks = computeParallelChunks(totalChunks, fileChunkSize, halfAvailableMemory.toDouble())
        )
    }

    /**
     * Calculates the size of a chunk from the size of a file within a limit between [chunkMinSize] and [chunkMaxSize],
     * taking into account the memory available to the application.
     * If the limit is exceeded, an [AllowedFileSizeExceededException] is thrown, otherwise the chunk size is returned.
     *
     * @return The file chunk size otherwise raises an [AllowedFileSizeExceededException]
     *
     * @throws AllowedFileSizeExceededException
     */
    private fun computeChunkSize(fileSize: Long, halfAvailableMemory: Long): Long {
        var fileChunkSize = ceil(fileSize.toDouble() / optimalTotalChunks).toLong()

        if (fileChunkSize > chunkMaxSize) {
            fileChunkSize = ceil(fileSize.toDouble() / maxChunkCount).toLong()
        }

        if (fileChunkSize > chunkMaxSize) throw AllowedFileSizeExceededException()

        return adjustChunkSizeByAvailableMemory(fileChunkSize.coerceAtLeast(chunkMinSize), halfAvailableMemory)
    }

    private fun computeFileChunks(fileSize: Long, fileChunkSize: Long): Int = ceil(fileSize.toDouble() / fileChunkSize).toInt()

    private fun computeParallelChunks(totalChunks: Int, fileChunkSize: Long, halfAvailableMemory: Double): Int {
        if (totalChunks == 1) return 1
        return floor(halfAvailableMemory / fileChunkSize).toInt().coerceIn(1, min(totalChunks, maxParallelChunks))
    }

    private fun adjustChunkSizeByAvailableMemory(fileChunkSize: Long, halfAvailableMemory: Long): Long {
        return fileChunkSize.coerceAtMost(halfAvailableMemory)
    }
    
    data class ChunkConfig(
        val fileChunkSize: Long,
        val totalChunks: Int,
        val parallelChunks: Int
    )

    class AllowedFileSizeExceededException : Exception()

    companion object {
        const val CHUNK_MIN_SIZE = 1L * 1024 * 1024 // 1Mo
        private const val CHUNK_MAX_SIZE = 50L * 1024 * 1024 // 50 Mo and max file size to upload 500Gb
        private const val OPTIMAL_TOTAL_CHUNKS = 200
        private const val MAX_CHUNK_COUNT = 10_000
        private const val MAX_PARALLEL_CHUNKS = 4

        fun getHalfHeapMemory(): Long = Runtime.getRuntime().maxMemory() / 2
    }

}
