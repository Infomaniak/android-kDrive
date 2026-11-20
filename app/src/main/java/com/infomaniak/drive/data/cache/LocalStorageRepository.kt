/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.infomaniak.drive.data.cache

import android.content.Context
import android.util.Log
import coil3.imageLoader
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.utils.StorageCategory
import com.infomaniak.drive.utils.StorageFile
import com.infomaniak.drive.utils.deleteUnchangedCacheFile
import com.infomaniak.drive.utils.scanLocalStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import io.realm.exceptions.RealmFileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import javax.inject.Inject

internal data class CachedStorageFile(val file: StorageFile, val name: String?, val id: Int?, val category: StorageCategory)

internal data class LocalStorageUsage(val sizes: Map<StorageCategory, Long>, val files: List<CachedStorageFile>) {
    val totalBytes: Long get() = sizes.values.sum()
}

internal class LocalStorageRepository @Inject constructor(@param:ApplicationContext private val context: Context) {

    suspend fun measure(): LocalStorageUsage = withContext(Dispatchers.IO) {
        val cache = context.cacheDir.canonicalFile.toPath()
        val data = context.dataDir.canonicalFile.toPath()
        val files = context.filesDir.canonicalFile.toPath()
        val downloadCache = cache.resolve(File.DOWNLOAD_CACHE_DIRECTORY)
        val previewCache = cache.resolve(File.PREVIEW_CACHE_DIRECTORY)
        val externalCaches = context.externalCacheDirs.filterNotNull().map { it.canonicalFile.toPath() }
        val externalFiles = context.getExternalFilesDirs(null).filterNotNull().map { it.canonicalFile.toPath() }
        val media = context.externalMediaDirs.filterNotNull().map { it.canonicalFile.toPath() }
        val offlineDirectory = context.getString(R.string.EXPOSED_OFFLINE_DIR)
        val categories = buildMap {
            put(files.resolve(offlineDirectory), StorageCategory.OfflineFiles)
            media.forEach { put(it.resolve(offlineDirectory), StorageCategory.OfflineFiles) }
            put(cache.resolve(UploadWorker.UPLOAD_FOLDER), StorageCategory.Imports)
            put(cache.resolve(SaveExternalFilesActivity.SHARED_FILE_FOLDER), StorageCategory.Imports)
            put(cache.resolve(context.getString(R.string.EXPOSED_UPLOAD_DIR)), StorageCategory.Imports)
            put(files.resolve("cloud_storage_temp_files"), StorageCategory.DocumentProvider)
            put(downloadCache, StorageCategory.CachedDownloads)
            put(previewCache, StorageCategory.CachedPreviews)
            context.imageLoader.diskCache?.let { put(cache.resolve(it.directory.name), StorageCategory.ImageCache) }
            put(cache, StorageCategory.OtherCache)
            put(data.resolve("code_cache"), StorageCategory.OtherCache)
            externalCaches.forEach { put(it, StorageCategory.OtherCache) }
        }
        val scan = scanLocalStorage(listOf(data) + externalCaches + externalFiles + media, categories)

        val cacheEntries = scan.cachedFiles.map { file ->
            val isDownload = file.path.startsWith(downloadCache)
            val relative = (if (isDownload) downloadCache else previewCache).relativize(file.path)
            val category = if (isDownload) StorageCategory.CachedDownloads else StorageCategory.CachedPreviews
            val userId = if (relative.nameCount == 3) relative.getName(0).toString().toIntOrNull() else null
            val driveId = if (relative.nameCount == 3) relative.getName(1).toString().toIntOrNull() else null
            val fileId = if (relative.nameCount == 3) relative.getName(2).toString().toIntOrNull() else null
            val drive = if (userId != null && driveId != null && fileId != null) UserDrive(userId, driveId) else null
            drive to CachedStorageFile(file, name = null, id = fileId, category)
        }
        val names = mutableMapOf<Path, String>()
        for ((drive, entries) in cacheEntries.groupBy({ it.first }, { it.second })) {
            ensureActive()
            if (drive == null) continue
            val configuration = FileController.getRealmConfiguration(drive)
            if (!java.io.File(configuration.path).isFile) continue
            try {
                Realm.getInstance(configuration).use { realm ->
                    for (entry in entries) {
                        ensureActive()
                        FileController.getFileById(realm, checkNotNull(entry.id))?.name?.let { names[entry.file.path] = it }
                    }
                }
            } catch (exception: RealmFileException) {
                Log.w(TAG, "Cached file names could not be loaded", exception)
            }
        }

        LocalStorageUsage(scan.sizes, cacheEntries.map { (_, entry) -> entry.copy(name = names[entry.file.path]) })
    }

    suspend fun clearFiles(files: List<CachedStorageFile>): Unit = withContext(Dispatchers.IO) {
        val cache = context.cacheDir.canonicalFile.toPath()
        var firstFailure: IOException? = null
        for (entry in files) {
            ensureActive()
            val folder = when (entry.category) {
                StorageCategory.CachedDownloads -> File.DOWNLOAD_CACHE_DIRECTORY
                StorageCategory.CachedPreviews -> File.PREVIEW_CACHE_DIRECTORY
                else -> error("Only reconstructible cached files can be removed")
            }
            try {
                deleteUnchangedCacheFile(cache.resolve(folder), entry.file)
            } catch (exception: IOException) {
                if (firstFailure == null) firstFailure = exception
            }
        }
        firstFailure?.let { throw it }
    }

    suspend fun clearImageCache(): Unit = withContext(Dispatchers.IO) {
        val imageLoader = context.imageLoader
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
    }

    private companion object {
        const val TAG = "LocalStorageRepository"
    }
}
