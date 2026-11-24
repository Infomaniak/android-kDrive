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
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.services.UploadWorker
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.utils.CacheFileLocks
import com.infomaniak.drive.utils.StorageCategory
import com.infomaniak.drive.utils.StorageFile
import com.infomaniak.drive.utils.deleteUnchangedCacheFile
import com.infomaniak.drive.utils.deleteUnchangedCacheFiles
import com.infomaniak.drive.utils.scanManageStorage
import dagger.hilt.android.qualifiers.ApplicationContext
import io.realm.Realm
import io.realm.exceptions.RealmFileException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.file.Path
import javax.inject.Inject

internal data class CachedStorageFile(val file: StorageFile, val name: String?, val id: Int?, val category: StorageCategory)

internal data class ManageStorageUsage(val sizes: Map<StorageCategory, Long>, val files: List<CachedStorageFile>) {
    val totalBytes: Long get() = sizes.values.sum()
}

internal class ManageStorageRepository @Inject constructor(@param:ApplicationContext private val context: Context) {

    suspend fun measure(): ManageStorageUsage = withContext(Dispatchers.IO) {
        val paths = StoragePaths.from(context)
        val scan = scanManageStorage(paths.roots, paths.categories)
        val cacheEntries = scan.cachedFiles.filterNot { it.path.startsWith(paths.thumbnails) }
            .map { it.toCachedStorageFile(paths.downloadCache, paths.previewCache, paths.publicCache) }
        val names = loadCachedFileNames(cacheEntries)
        ManageStorageUsage(scan.sizes, cacheEntries.map { (_, entry) ->
            entry.copy(name = names[entry.file.path] ?: entry.name)
        })
    }

    suspend fun clearFiles(files: List<CachedStorageFile>): Unit = withContext(Dispatchers.IO) {
        val cache = context.cacheDir.canonicalFile.toPath()
        var firstFailure: IOException? = null
        for (entry in files) {
            ensureActive()
            val folder = cacheFolderFor(entry.category, cache)
            try {
                CacheFileLocks.withLock(entry.file.path.toFile()) {
                    if (entry.category == StorageCategory.CachedPublicShare) {
                        PublicShareCache.deleteIfInactive { deleteUnchangedCacheFile(folder, entry.file) }
                    } else {
                        deleteUnchangedCacheFile(folder, entry.file)
                    }
                }
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
        val root = context.cacheDir.canonicalFile.toPath().resolve(CloudStorageProvider.THUMBNAIL_CACHE_DIRECTORY)
        val thumbnails = scanManageStorage(listOf(root), mapOf(root to StorageCategory.ProviderThumbnail)).cachedFiles
        deleteUnchangedCacheFiles(root, thumbnails)
    }

    private fun cacheFolderFor(category: StorageCategory, cache: Path): Path = when (category) {
        StorageCategory.CachedDownloads -> cache.resolve(File.DOWNLOAD_CACHE_DIRECTORY)
        StorageCategory.CachedPreviews -> cache.resolve(File.PREVIEW_CACHE_DIRECTORY)
        StorageCategory.CachedPublicShare -> context.filesDir.canonicalFile.toPath()
            .resolve(context.getString(R.string.EXPOSED_PUBLIC_SHARE_DIR))
        else -> error("Only reconstructible cached files can be removed")
    }

    private suspend fun loadCachedFileNames(cacheEntries: List<Pair<UserDrive?, CachedStorageFile>>): Map<Path, String> {
        val names = mutableMapOf<Path, String>()
        for ((drive, entries) in cacheEntries.groupBy({ it.first }, { it.second })) {
            currentCoroutineContext().ensureActive()
            if (drive == null) continue
            for (realmDrive in listOf(drive, drive.copy(sharedWithMe = true))) {
                loadNamesFromRealm(realmDrive, entries, names)
            }
        }
        return names
    }

    private suspend fun loadNamesFromRealm(
        drive: UserDrive,
        entries: List<CachedStorageFile>,
        names: MutableMap<Path, String>,
    ) {
        val configuration = FileController.getRealmConfiguration(drive)
        if (!java.io.File(configuration.path).isFile) return
        val coroutineContext = currentCoroutineContext()
        try {
            Realm.getInstance(configuration).use { realm ->
                entries.forEach { entry ->
                    coroutineContext.ensureActive()
                    if (entry.file.path !in names) {
                        realm.where(File::class.java)
                            .equalTo(File::uid.name, "${checkNotNull(entry.id)}_${drive.driveId}")
                            .findFirst()?.name?.let { names[entry.file.path] = it }
                    }
                }
            }
        } catch (exception: RealmFileException) {
            Log.w(TAG, "Cached file names could not be loaded", exception)
        }
    }

    private data class StoragePaths(
        val roots: List<Path>,
        val categories: Map<Path, StorageCategory>,
        val downloadCache: Path,
        val previewCache: Path,
        val publicCache: Path,
        val thumbnails: Path,
    ) {
        companion object {
            fun from(context: Context): StoragePaths {
                val cache = context.cacheDir.canonicalFile.toPath()
                val data = context.dataDir.canonicalFile.toPath()
                val files = context.filesDir.canonicalFile.toPath()
                val downloadCache = cache.resolve(File.DOWNLOAD_CACHE_DIRECTORY)
                val previewCache = cache.resolve(File.PREVIEW_CACHE_DIRECTORY)
                val publicCache = files.resolve(context.getString(R.string.EXPOSED_PUBLIC_SHARE_DIR))
                val thumbnails = cache.resolve(CloudStorageProvider.THUMBNAIL_CACHE_DIRECTORY)
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
                    put(publicCache, StorageCategory.CachedPublicShare)
                    put(thumbnails, StorageCategory.ProviderThumbnail)
                    context.imageLoader.diskCache?.let { put(cache.resolve(it.directory.name), StorageCategory.ImageCache) }
                    put(cache, StorageCategory.OtherCache)
                    put(data.resolve("code_cache"), StorageCategory.OtherCache)
                    externalCaches.forEach { put(it, StorageCategory.OtherCache) }
                }
                return StoragePaths(
                    roots = listOf(data) + externalCaches + externalFiles + media,
                    categories = categories,
                    downloadCache = downloadCache,
                    previewCache = previewCache,
                    publicCache = publicCache,
                    thumbnails = thumbnails,
                )
            }
        }
    }

    private fun StorageFile.toCachedStorageFile(
        downloadCache: Path,
        previewCache: Path,
        publicCache: Path,
    ): Pair<UserDrive?, CachedStorageFile> {
        if (path.startsWith(publicCache)) {
            return null to CachedStorageFile(this, path.fileName.toString(), id = null, StorageCategory.CachedPublicShare)
        }
        val isDownload = path.startsWith(downloadCache)
        val relative = (if (isDownload) downloadCache else previewCache).relativize(path)
        val category = if (isDownload) StorageCategory.CachedDownloads else StorageCategory.CachedPreviews
        val (userId, driveId, fileId) = relative.storageIds()
        val drive = if (userId != null && driveId != null && fileId != null) UserDrive(userId, driveId) else null
        return drive to CachedStorageFile(this, name = null, id = fileId, category)
    }

    private fun Path.storageIds(): Triple<Int?, Int?, Int?> {
        if (nameCount != 3) return Triple(null, null, null)
        return Triple(
            getName(0).toString().toIntOrNull(),
            getName(1).toString().toIntOrNull(),
            getName(2).toString().toIntOrNull(),
        )
    }

    private companion object {
        const val TAG = "ManageStorageRepository"
    }
}
