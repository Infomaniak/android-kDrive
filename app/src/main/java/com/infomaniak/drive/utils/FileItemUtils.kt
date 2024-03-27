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
package com.infomaniak.drive.utils

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.forEachIndexed
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import coil.load
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.VisibilityType
import com.infomaniak.drive.databinding.CardviewFileGridBinding
import com.infomaniak.drive.databinding.CardviewFolderGridBinding
import com.infomaniak.drive.databinding.ItemCategoriesLayoutBinding
import com.infomaniak.drive.databinding.ItemFileBinding
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.MAX_DISPLAYED_CATEGORIES
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.views.CategoryIconView
import com.infomaniak.drive.views.ProgressLayoutView
import com.infomaniak.lib.core.utils.context
import com.infomaniak.lib.core.utils.format
import io.sentry.Sentry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun ItemFileBinding.setFileItem(file: File, isGrid: Boolean = false) {
    fileName.text = file.name
    fileFavorite.isVisible = file.isFavorite
    progressLayout.isGone = true
    displayDate(file)
    displaySize(file)
    filePreview.displayIcon(file, isGrid, progressLayout)
    categoriesLayout.displayCategories(file)
    displayExternalImport(file, filePreview, fileProgression, fileDate)
}

fun CardviewFolderGridBinding.setFileItem(file: File, isGrid: Boolean = false) {
    fileName.text = file.name
    fileFavorite.isVisible = file.isFavorite
    progressLayout.isGone = true
    filePreview.displayIcon(file, isGrid, progressLayout)
    categoriesLayout.displayCategories(file)
    displayExternalImport(file, filePreview, fileProgression)
}

fun CardviewFileGridBinding.setFileItem(file: File, isGrid: Boolean = false) {
    fileName.text = file.name
    fileFavorite.isVisible = file.isFavorite
    progressLayout.isGone = true
    filePreview.displayIcon(file, isGrid, progressLayout, filePreview2)
    categoriesLayout.displayCategories(file)
}

private fun ItemFileBinding.displayDate(file: File) = fileDate.apply {
    isVisible = file.id != ROOT_ID
    text = if (file.deletedAt.isPositive()) {
        file.getDeletedAt().format(context.getString(R.string.allDeletedFilePattern))
    } else {
        file.getLastModifiedAt().format(context.getString(R.string.allLastModifiedFilePattern))
    }
}

private fun ItemFileBinding.displaySize(file: File) {
    file.size?.let {
        fileSize.text = context.formatShortBinarySize(it)
        fileSeparator.isVisible = true
    } ?: run {
        fileSize.text = ""
        fileSeparator.isGone = true
    }
}

private fun ImageView.displayIcon(
    file: File,
    isGrid: Boolean,
    progressLayout: ProgressLayoutView,
    filePreview: ImageView? = null,
) {
    scaleType = if (isGrid) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER
    when {
        file.isFolder() -> displayFolderIcon(file)
        file.isDrive() -> displayDriveIcon(file)
        else -> displayFileIcon(file, isGrid, progressLayout, filePreview)
    }
}

private fun ImageView.displayFolderIcon(file: File) {
    val (icon, tint) = file.getFolderIcon()
    if (tint == null) load(icon) else load(getTintedDrawable(context, icon, tint))
}

private fun ImageView.displayDriveIcon(file: File) {
    load(getTintedDrawable(context, R.drawable.ic_drive, file.driveColor))
}

private fun ImageView.displayFileIcon(
    file: File,
    isGrid: Boolean,
    progressLayout: ProgressLayoutView,
    filePreview: ImageView? = null,
) {
    val fileType = file.getFileType()
    val isGraphic = fileType == ExtensionType.IMAGE || fileType == ExtensionType.VIDEO

    when {
        file.hasThumbnail && (isGrid || isGraphic) -> {
            scaleType = ImageView.ScaleType.CENTER_CROP
            loadAny(file.thumbnail(), fileType.icon)
        }
        file.isFromUploads && isGraphic -> {
            scaleType = ImageView.ScaleType.CENTER_CROP
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = context.getLocalThumbnail(file)
                withContext(Dispatchers.Main) {
                    if (isVisible && context != null) loadAny(bitmap, fileType.icon)
                }
            }
        }
        else -> {
            scaleType = ImageView.ScaleType.CENTER
            load(fileType.icon)
        }
    }

    filePreview?.load(fileType.icon)
    progressLayout.setupFileProgress(file)
}

fun getTintedDrawable(context: Context, icon: Int, tint: String): Drawable {
    return ContextCompat.getDrawable(context, icon)!!.mutate().apply { setTint(tint.toColorInt()) }
}

private fun ItemCategoriesLayoutBinding.displayCategories(file: File) = with(root) {
    val canReadCategoryOnFile = DriveInfosController.getCategoryRights(file.driveId).canReadCategoryOnFile
    val categories = file.getCategories()

    if (!canReadCategoryOnFile || categories.isEmpty()) {
        isGone = true
    } else {
        forEachIndexed { index, view ->
            with(view as CategoryIconView) {
                val category = categories.getOrNull(index)
                if (index < MAX_DISPLAYED_CATEGORIES - 1) {
                    setCategoryIconOrHide(category)
                } else {
                    setRemainingCategoriesNumber(category, categories.size - MAX_DISPLAYED_CATEGORIES)
                }
            }
        }
        isVisible = true
    }
}

private fun ViewBinding.displayExternalImport(
    file: File,
    filePreview: ImageView,
    fileProgression: CircularProgressIndicator? = null,
    fileDate: TextView? = null,
) {
    val isImporting = file.isImporting()
    fileProgression?.isVisible = isImporting
    filePreview.isInvisible = isImporting
    if (isImporting) {
        val importStatus = if (file.isCancelingImport()) R.string.allCancellationInProgress else R.string.uploadInProgressTitle
        fileDate?.text = context.resources.getString(importStatus)
    }
}

/**
 * This method is here, and not directly a class method in the File class, because of a supposed Realm bug.
 * When we try to put it in the File class, the app doesn't build anymore, because of a "broken method".
 * This is not the only method in this case, search this comment in the project, and you'll see.
 * Realm's Github issue: https://github.com/realm/realm-java/issues/7637
 */
fun File.getFolderIcon(): Pair<Int, String?> {
    return if (isDisabled()) R.drawable.ic_folder_disable to null
    else when (getVisibilityType()) {
        VisibilityType.IS_TEAM_SPACE -> R.drawable.ic_folder_common_documents to null
        VisibilityType.IS_TEAM_SPACE_FOLDER -> R.drawable.ic_folder_common_documents to color
        VisibilityType.IS_SHARED_SPACE -> R.drawable.ic_folder_shared to null
        VisibilityType.IS_DROPBOX -> R.drawable.ic_folder_dropbox to color
        else -> R.drawable.ic_folder_filled to color
    }
}

suspend fun Context.getLocalThumbnail(file: File): Bitmap? = withContext(Dispatchers.IO) {
    val fileUri = file.path.toUri()
    val thumbnailSize = 100
    return@withContext if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        getThumbnailAfterAndroidPie(file, fileUri, thumbnailSize)
    } else {
        getThumbnailUntilAndroidPie(file, fileUri, thumbnailSize)
    }
}

private fun Context.getThumbnailAfterAndroidPie(file: File, fileUri: Uri, thumbnailSize: Int): Bitmap? {
    return if (Build.VERSION.SDK_INT > Build.VERSION_CODES.P) {
        val size = Size(thumbnailSize, thumbnailSize)
        try {
            if (fileUri.scheme.equals(ContentResolver.SCHEME_FILE)) {
                if (file.getMimeType().contains("video")) {
                    ThumbnailUtils.createVideoThumbnail(fileUri.toFile(), size, null)
                } else {
                    ThumbnailUtils.createImageThumbnail(fileUri.toFile(), size, null)
                }
            } else {
                contentResolver.loadThumbnail(fileUri, size, null)
            }
        } catch (e: Exception) {
            null
        }
    } else {
        null
    }
}

private fun Context.getThumbnailUntilAndroidPie(file: File, fileUri: Uri, thumbnailSize: Int): Bitmap? {
    val isSchemeFile = fileUri.scheme.equals(ContentResolver.SCHEME_FILE)
    val localFile = fileUri.lastPathSegment?.split(":")?.let { list ->
        list.getOrNull(1)?.let { path -> IOFile(path) }
    }
    val externalRealPath = getExternalRealPath(fileUri, isSchemeFile, localFile)

    return if (isSchemeFile || externalRealPath.isNotBlank()) {
        getBitmapFromPath(file, fileUri, thumbnailSize, externalRealPath)
    } else {
        getBitmapFromFileId(fileUri, thumbnailSize)
    }
}

private fun Context.getExternalRealPath(fileUri: Uri, isSchemeFile: Boolean, localFile: IOFile?): String {
    return when {
        !isSchemeFile && localFile?.exists() == true -> {
            Sentry.withScope { scope -> // Get more information in uri with absolute path
                scope.setExtra("uri", "$fileUri")
                Sentry.captureMessage("Uri contains absolute path")
            }
            localFile.absolutePath
        }
        fileUri.authority == "com.android.externalstorage.documents" -> {
            Utils.getRealPathFromExternalStorage(this, fileUri)
        }
        else -> ""
    }
}

private fun getBitmapFromPath(file: File, fileUri: Uri, thumbnailSize: Int, externalRealPath: String): Bitmap? {
    val path = externalRealPath.ifBlank { fileUri.path ?: return null }

    return if (file.getMimeType().contains("video")) {
        ThumbnailUtils.createVideoThumbnail(path, MediaStore.Video.Thumbnails.MICRO_KIND)
    } else {
        Utils.extractThumbnail(path, thumbnailSize, thumbnailSize)
    }
}

private fun Context.getBitmapFromFileId(fileUri: Uri, thumbnailSize: Int): Bitmap? {
    return try {
        ContentUris.parseId(fileUri)
    } catch (e: Exception) {
        fileUri.lastPathSegment?.split(":")?.let { it.getOrNull(1)?.toLongOrNull() }
    }?.let { fileId ->
        val options = BitmapFactory.Options().apply {
            outWidth = thumbnailSize
            outHeight = thumbnailSize
        }
        if (contentResolver.getType(fileUri)?.contains("video") == true) {
            MediaStore.Video.Thumbnails.getThumbnail(
                contentResolver,
                fileId,
                MediaStore.Video.Thumbnails.MICRO_KIND,
                options,
            )
        } else {
            MediaStore.Images.Thumbnails.getThumbnail(
                contentResolver,
                fileId,
                MediaStore.Images.Thumbnails.MICRO_KIND,
                options,
            )
        }
    }
}

fun ProgressLayoutView.setupFileProgress(file: File, containsProgress: Boolean = false) {
    when {
        !containsProgress && file.currentProgress == Utils.INDETERMINATE_PROGRESS && file.isPendingOffline(context) -> {
            setIndeterminateProgress()
            isVisible = true
        }
        containsProgress && file.currentProgress in 0..99 -> {
            setProgress(file)
            isVisible = true
        }
        file.isOfflineFile(context, checkLocalFile = false) -> {
            hideProgress()
            isVisible = true
        }
        else -> isGone = true
    }
}
