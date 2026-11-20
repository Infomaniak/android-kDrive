/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.utils

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

class LocalStorageFilesTest {

    @TempDir
    lateinit var temp: Path

    @Test
    fun `scan counts nested roots once and lists only cached documents by size`() {
        val data = Files.createDirectory(temp.resolve("data"))
        val cache = Files.createDirectory(data.resolve("cache"))
        val downloads = Files.createDirectory(cache.resolve("downloads"))
        val media = Files.createDirectory(temp.resolve("media"))
        val offline = Files.createDirectory(media.resolve("offline"))
        val smallest = Files.write(downloads.resolve("1"), ByteArray(3))
        val largest = Files.write(downloads.resolve("2"), ByteArray(20))
        Files.write(cache.resolve("other"), ByteArray(5))
        Files.write(offline.resolve("offline.pdf"), ByteArray(13))
        Files.write(data.resolve("db"), ByteArray(17))
        Files.createSymbolicLink(downloads.resolve("offline-link"), offline)

        val scan = runBlocking {
            scanLocalStorage(
                listOf(data, cache, media, temp.resolve("missing")),
                mapOf(cache to StorageCategory.OtherCache, downloads to StorageCategory.CachedDownloads, offline to StorageCategory.OfflineFiles),
            )
        }

        assertEquals(58L, scan.sizes.values.sum())
        assertEquals(23L, scan.sizes[StorageCategory.CachedDownloads])
        assertEquals(13L, scan.sizes[StorageCategory.OfflineFiles])
        assertEquals(5L, scan.sizes[StorageCategory.OtherCache])
        assertEquals(listOf(largest, smallest), scan.cachedFiles.map(StorageFile::path))
    }

    @Test
    fun `cleanup only removes the unchanged cache file and leaves offline files alone`() {
        val cache = Files.createDirectory(temp.resolve("cache")).toRealPath()
        val unchanged = Files.write(cache.resolve("1"), ByteArray(3))
        val updated = Files.write(cache.resolve("2"), ByteArray(3))
        val offline = Files.write(temp.resolve("offline.pdf"), ByteArray(11))
        val scan = runBlocking { scanLocalStorage(listOf(cache), mapOf(cache to StorageCategory.CachedDownloads)) }
        val entries = scan.cachedFiles.associateBy(StorageFile::path)

        Files.write(updated, ByteArray(7))
        assertThrows(IOException::class.java) { deleteUnchangedCacheFile(cache, entries.getValue(updated)) }
        assertThrows(IllegalArgumentException::class.java) { deleteUnchangedCacheFile(cache, StorageFile(offline, 11, 0)) }
        deleteUnchangedCacheFile(cache, entries.getValue(unchanged))

        assertFalse(Files.exists(unchanged))
        assertEquals(7L, Files.size(updated))
        assertTrue(Files.exists(offline))
    }
}
