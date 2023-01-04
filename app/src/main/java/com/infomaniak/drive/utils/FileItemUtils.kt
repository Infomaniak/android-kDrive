/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022 Infomaniak Network SA
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
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toFile
import androidx.core.net.toUri
import androidx.core.view.forEachIndexed
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import coil.load
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.VisibilityType
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.MAX_DISPLAYED_CATEGORIES
import com.infomaniak.drive.ui.fileList.FileViewHolder
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.drive.views.CategoryIconView
import com.infomaniak.lib.core.utils.FormatterFileSize
import com.infomaniak.lib.core.utils.format
import io.sentry.Sentry
import kotlinx.android.synthetic.main.cardview_file_grid.view.filePreview2
import kotlinx.android.synthetic.main.item_file.view.*
import kotlinx.android.synthetic.main.item_file.view.categoriesLayout
import kotlinx.android.synthetic.main.item_file.view.fileFavorite
import kotlinx.android.synthetic.main.item_file.view.fileName
import kotlinx.android.synthetic.main.item_file.view.fileOffline
import kotlinx.android.synthetic.main.item_file.view.fileOfflineProgression
import kotlinx.android.synthetic.main.item_file.view.filePreview
import kotlinx.android.synthetic.main.item_file.view.progressLayout
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun View.setFileItem(file: File, isGrid: Boolean = false, viewHolder: FileViewHolder? = null) {
    fileName.text = file.name
    fileFavorite.isVisible = file.isFavorite
    progressLayout.isGone = true
    displayDate(file)
    displaySize(file)
    displayIcon(file, isGrid, viewHolder)
    displayCategories(file)
    displayExternalImport(file)
}

private fun View.displayDate(file: File) {
    fileDate?.apply {
        isVisible = file.id != ROOT_ID
        text = if (file.deletedAt.isPositive()) {
            file.getDeletedAt().format(context.getString(R.string.allDeletedFilePattern))
        } else {
            file.getLastModifiedAt().format(context.getString(R.string.allLastModifiedFilePattern))
        }
    }
}

private fun View.displaySize(file: File) {
    file.size?.let {
        fileSize?.text = FormatterFileSize.formatShortFileSize(context, it)
        fileSeparator?.isVisible = true
    } ?: run {
        fileSize?.text = ""
        fileSeparator?.isGone = true
    }
}

private fun View.displayIcon(file: File, isGrid: Boolean, viewHolder: FileViewHolder?) {
    filePreview.scaleType = if (isGrid) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER
    when {
        file.isFolder() -> displayFolderIcon(file, viewHolder)
        file.isDrive() -> displayDriveIcon(file, viewHolder)
        else -> displayFileIcon(file, isGrid)
    }
}

private fun View.displayFolderIcon(file: File, viewHolder: FileViewHolder?) {
    val (icon, tint) = file.getFolderIcon()
    if (tint == null) filePreview.load(icon) else filePreview.load(context.getTintedDrawable(viewHolder, icon, tint))
}

private fun View.displayDriveIcon(file: File, viewHolder: FileViewHolder?) {
    filePreview.load(context.getTintedDrawable(viewHolder, R.drawable.ic_drive, file.driveColor))
}

private fun View.displayFileIcon(file: File, isGrid: Boolean) {
    val fileType = file.getFileType()
    val isGraphic = fileType == ExtensionType.IMAGE || fileType == ExtensionType.VIDEO

    when {
        file.hasThumbnail && (isGrid || isGraphic) -> {
            filePreview.apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                loadAny(file.thumbnail(), fileType.icon)
            }
        }
        file.isFromUploads && isGraphic -> {
            filePreview.scaleType = ImageView.ScaleType.CENTER_CROP
            CoroutineScope(Dispatchers.IO).launch {
                val bitmap = context.getLocalThumbnail(file)
                withContext(Dispatchers.Main) {
                    if (filePreview?.isVisible == true && context != null) filePreview.loadAny(bitmap, fileType.icon)
                }
            }
        }
        else -> {
            filePreview.apply {
                scaleType = ImageView.ScaleType.CENTER
                load(fileType.icon)
            }
        }
    }

    filePreview2?.load(fileType.icon)
    setupFileProgress(file)
}

private fun Context.getTintedDrawable(viewHolder: FileViewHolder?, icon: Int, tint: String): Drawable? {

    fun getDrawable(): Drawable? = ContextCompat.getDrawable(this, icon)?.mutate()

    val drawable = if (viewHolder == null) {
        getDrawable()
    } else {
        if (viewHolder.tintedDrawable == null) viewHolder.tintedDrawable = getDrawable()
        viewHolder.tintedDrawable
    }

    return drawable?.apply { setTint(tint.toColorInt()) }
}

private fun View.displayCategories(file: File) {
    val canReadCategoryOnFile = DriveInfosController.getCategoryRights(file.driveId).canReadCategoryOnFile
    val categories = file.getCategories()

    (categoriesLayout as LinearLayout).apply {
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
}

private fun View.displayExternalImport(file: File) {
    val isImporting = file.isImporting()
    fileProgression?.isVisible = isImporting
    filePreview.isInvisible = isImporting
    if (isImporting) {
        val importStatus = if (file.isCancelingImport()) R.string.allCancellationInProgress else R.string.uploadInProgressTitle
        fileDate?.text = resources.getString(importStatus)
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
        VisibilityType.IS_TEAM_SPACE_FOLDER -> R.drawable.ic_folder_common_documents to getCommonFolderDefaultColor()
        VisibilityType.IS_SHARED_SPACE -> R.drawable.ic_folder_shared to null
        VisibilityType.IS_DROPBOX -> R.drawable.ic_folder_dropbox to color
        else -> R.drawable.ic_folder_filled to color
    }
}

@Suppress("BlockingMethodInNonBlockingContext")
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
        list.getOrNull(1)?.let { path -> java.io.File(path) }
    }
    val externalRealPath = getExternalRealPath(fileUri, isSchemeFile, localFile)

    return if (isSchemeFile || externalRealPath.isNotBlank()) {
        getBitmapFromPath(file, fileUri, thumbnailSize, externalRealPath)
    } else {
        getBitmapFromFileId(fileUri, thumbnailSize)
    }
}

private fun Context.getExternalRealPath(fileUri: Uri, isSchemeFile: Boolean, localFile: java.io.File?): String {
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

fun View.setupFileProgress(file: File, containsProgress: Boolean = false) {
    when {
        !containsProgress && file.currentProgress == Utils.INDETERMINATE_PROGRESS && file.isPendingOffline(context) -> {
            fileOffline.isGone = true
            fileOfflineProgression.apply {
                isGone = true // We need to hide the view before updating its `isIndeterminate`
                isIndeterminate = true
                isVisible = true
            }
            progressLayout.isVisible = true
        }
        containsProgress && file.currentProgress in 0..99 -> {
            fileOffline.isGone = true
            fileOfflineProgression.apply {
                if (isIndeterminate) {
                    isGone = true // We need to hide the view before updating its `isIndeterminate`
                    isIndeterminate = false
                }
                isVisible = true
                progress = file.currentProgress
            }
            progressLayout.isVisible = true
        }
        file.isOfflineFile(context, checkLocalFile = false) -> {
            fileOffline.isVisible = true
            fileOfflineProgression.isGone = true
            progressLayout.isVisible = true
        }
        else -> progressLayout.isGone = true
    }
}
