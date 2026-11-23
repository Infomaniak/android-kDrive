/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.utils

import com.infomaniak.drive.data.cache.PublicShareCache
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
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

    @Test
    fun `cleanup waits for a cache writer and detects its changed file`() = runBlocking {
        val cache = Files.createDirectory(temp.resolve("cache")).toRealPath()
        val file = Files.write(cache.resolve("1"), ByteArray(3))
        val cacheAlias = Files.createSymbolicLink(temp.resolve("cache-alias"), cache)
        val snapshot = scanLocalStorage(listOf(cache), mapOf(cache to StorageCategory.CachedDownloads)).cachedFiles.single()
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val writer = launch {
            CacheFileLocks.withLock(cacheAlias.resolve("1").toFile()) {
                started.complete(Unit)
                release.await()
                Files.write(file, ByteArray(7))
            }
        }
        started.await()
        val cleanup = async {
            CacheFileLocks.withLock(file.toFile()) {
                runCatching { deleteUnchangedCacheFile(cache, snapshot) }
            }
        }
        yield()
        assertFalse(cleanup.isCompleted)
        assertFalse(CacheFileLocks.tryWithLock(file.toFile()) { true })
        release.complete(Unit)
        writer.join()
        assertTrue(cleanup.await().exceptionOrNull() is IOException)
        assertEquals(7L, Files.size(file))
    }

    @Test
    fun `failed cache promotion preserves the previous offline file`() {
        val offline = Files.write(temp.resolve("offline"), byteArrayOf(1, 2, 3))
        assertThrows(IOException::class.java) {
            promoteCachedFile(temp.resolve("missing").toFile(), offline.toFile(), 1_000)
        }

        @Test
        fun `scan includes public share files and provider thumbnails in their categories`() = runBlocking {
            val publicCache = Files.createDirectory(temp.resolve("public"))
            val thumbnails = Files.createDirectory(temp.resolve("thumbnails"))
            val publicFile = Files.write(publicCache.resolve("shared.pdf"), ByteArray(12))
            val thumbnail = Files.write(thumbnails.resolve("thumb"), ByteArray(8))
            val scan = scanLocalStorage(
                listOf(temp),
                mapOf(publicCache to StorageCategory.CachedPublicShare, thumbnails to StorageCategory.ProviderThumbnail),
            )
            assertEquals(12L, scan.sizes[StorageCategory.CachedPublicShare])
            assertEquals(8L, scan.sizes[StorageCategory.ProviderThumbnail])
            assertEquals(setOf(publicFile, thumbnail), scan.cachedFiles.map { it.path }.toSet())
        }
        assertEquals(listOf<Byte>(1, 2, 3), Files.readAllBytes(offline).toList())
        assertEquals(listOf(offline), Files.list(temp).use { it.toList() })
    }

    @Test
    fun `public share cleanup is rejected until the last activity closes`() {
        PublicShareCache.opened()
        PublicShareCache.opened()
        try {
            assertThrows(IOException::class.java) { PublicShareCache.deleteIfInactive {} }
            PublicShareCache.closed()
            assertThrows(IOException::class.java) { PublicShareCache.deleteIfInactive {} }
        } finally {
            PublicShareCache.closed()
        }
        var deleted = false
        PublicShareCache.deleteIfInactive { deleted = true }
        assertTrue(deleted)
    }
}
