/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.FileVisitResult.CONTINUE
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes

internal enum class StorageCategory {
    OfflineFiles,
    Imports,
    DocumentProvider,
    CachedDownloads,
    CachedPreviews,
    CachedPublicShare,
    ImageCache,
    ProviderThumbnail,
    OtherCache,
    OtherData,
}

internal data class StorageFile(val path: Path, val bytes: Long, val lastModifiedMillis: Long)

internal data class StorageScan(
    val sizes: Map<StorageCategory, Long>,
    val cachedFiles: List<StorageFile>,
)

private val listedCacheCategories = setOf(
    StorageCategory.CachedDownloads,
    StorageCategory.CachedPreviews,
    StorageCategory.CachedPublicShare,
    StorageCategory.ProviderThumbnail,
)

internal suspend fun scanManageStorage(
    roots: List<Path>,
    categories: Map<Path, StorageCategory>,
): StorageScan {
    val jobContext = currentCoroutineContext()
    val sizes = mutableMapOf<StorageCategory, Long>()
    val cachedFiles = mutableListOf<StorageFile>()
    val categoryRoots = categories.entries.sortedByDescending { it.key.nameCount }
    val distinctRoots = roots.distinct()
    for (root in distinctRoots.filter { path -> distinctRoots.none { it != path && path.startsWith(it) } }) {
        Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
            override fun preVisitDirectory(dir: Path, attrs: BasicFileAttributes) = CONTINUE.also { jobContext.ensureActive() }

            override fun visitFile(file: Path, attrs: BasicFileAttributes) = CONTINUE.also {
                jobContext.ensureActive()
                if (!attrs.isRegularFile) return@also

                val category = categoryRoots.firstOrNull { file.startsWith(it.key) }?.value ?: StorageCategory.OtherData
                sizes[category] = sizes.getOrDefault(category, 0) + attrs.size()
                if (category in listedCacheCategories) {
                    cachedFiles += StorageFile(file, attrs.size(), attrs.lastModifiedTime().toMillis())
                }
            }

            override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                // Cache entries and optional storage directories can disappear during the scan.
                if (exc is NoSuchFileException) return CONTINUE
                throw exc
            }
        })
    }

    return StorageScan(sizes, cachedFiles.sortedByDescending(StorageFile::bytes))
}

internal fun deleteUnchangedCacheFile(root: Path, file: StorageFile) {
    val path = file.path
    require(path != root && path.normalize() == path && path.startsWith(root))

    val attrs = try {
        if (path.toRealPath() != path) throw IOException("The cache entry is no longer at its original path")
        Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS)
    } catch (_: NoSuchFileException) {
        return
    }
    if (!attrs.isRegularFile || attrs.size() != file.bytes || attrs.lastModifiedTime().toMillis() != file.lastModifiedMillis) {
        throw IOException("The cache entry changed during cleanup")
    }
    if (!Files.deleteIfExists(path) && Files.exists(path, NOFOLLOW_LINKS)) {
        throw IOException("The cache entry could not be deleted")
    }
}

internal suspend fun deleteUnchangedCacheFiles(root: Path, files: List<StorageFile>) {
    val coroutineContext = currentCoroutineContext()
    var firstFailure: IOException? = null
    for (file in files) {
        coroutineContext.ensureActive()
        try {
            val deleted = CacheFileLocks.tryWithLock(file.path.toFile()) {
                deleteUnchangedCacheFile(root, file)
                true
            }
            if (!deleted) throw IOException("A cache entry is in use")
        } catch (exception: IOException) {
            if (firstFailure == null) firstFailure = exception else firstFailure.addSuppressed(exception)
        }
    }
    firstFailure?.let { throw it }
}

internal fun promoteCachedFile(
    cacheFile: java.io.File,
    offlineFile: java.io.File,
    lastModifiedMillis: Long,
    deleteCache: (java.io.File) -> Boolean = java.io.File::delete,
): Boolean {
    val tempFile = Files.createTempFile(offlineFile.parentFile!!.toPath(), ".offline-", ".tmp").toFile()
    var operationFailure: Throwable? = null
    try {
        cacheFile.copyTo(tempFile, overwrite = true)
        if (!tempFile.setLastModified(lastModifiedMillis)) throw IOException("Could not set offline file timestamp")
        Files.move(tempFile.toPath(), offlineFile.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    } catch (exception: Throwable) {
        operationFailure = exception
    }

    val cleanupFailure = if (!tempFile.delete() && tempFile.exists()) {
        IOException("The temporary offline file could not be deleted")
    } else {
        null
    }
    operationFailure?.let { failure ->
        cleanupFailure?.let(failure::addSuppressed)
        throw failure
    }
    cleanupFailure?.let { throw it }
    return try {
        deleteCache(cacheFile) || !cacheFile.exists()
    } catch (_: SecurityException) {
        false
    }
}
