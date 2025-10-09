/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.content.Context
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.core.net.toUri
import androidx.core.view.forEachIndexed
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import androidx.viewbinding.ViewBinding
import coil3.ImageLoader
import coil3.imageLoader
import coil3.load
import com.google.android.material.progressindicator.CircularProgressIndicator
import com.infomaniak.core.FormatterFileSize.formatShortFileSize
import com.infomaniak.core.legacy.utils.context
import com.infomaniak.core.legacy.utils.setMargins
import com.infomaniak.core.legacy.utils.toPx
import com.infomaniak.core.thumbnails.ThumbnailsUtils.getLocalThumbnail
import com.infomaniak.core.utils.format
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.File.VisibilityType
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.databinding.CardviewFileGridBinding
import com.infomaniak.drive.databinding.CardviewFolderGridBinding
import com.infomaniak.drive.databinding.ItemCategoriesLayoutBinding
import com.infomaniak.drive.databinding.ItemFileBinding
import com.infomaniak.drive.ui.fileList.FileListFragment.Companion.MAX_DISPLAYED_CATEGORIES
import com.infomaniak.drive.views.CategoryIconView
import com.infomaniak.drive.views.ProgressLayoutView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

suspend fun ItemFileBinding.setFileItem(
    file: File,
    isGrid: Boolean = false,
    imageLoader: ImageLoader = context.imageLoader,
    typeFolder: TypeFolder = TypeFolder.fileList
): Nothing {
    setFileItemWithoutCategories(file = file, typeFolder = typeFolder, isGrid = isGrid, imageLoader)
    categoriesLayout.displayCategoriesForFile(file)
}

fun ItemFileBinding.setFileItemWithoutCategories(
    file: File,
    typeFolder: TypeFolder = TypeFolder.fileList,
    isGrid: Boolean = false,
    imageLoader: ImageLoader = context.imageLoader,
) {
    fileName.text = file.getDisplayName(context)
    fileFavorite.isVisible = file.isFavorite
    progressLayout.isGone = true
    displayDate(file)
    displaySize(file)
    filePreview.displayIcon(file, isGrid, progressLayout, imageLoader = imageLoader)
    iconLayout.setMargins(left = typeFolder.iconHorizontalMargin, right = typeFolder.iconHorizontalMargin)
    displayExternalImport(file, filePreview, fileProgression, fileDate)
}

suspend fun CardviewFolderGridBinding.setFileItem(file: File, isGrid: Boolean = false): Nothing {
    fileName.text = file.getDisplayName(context)
    fileFavorite.isVisible = file.isFavorite
    progressLayout.isGone = true
    filePreview.displayIcon(file, isGrid, progressLayout)
    displayExternalImport(file, filePreview, fileProgression)
    categoriesLayout.displayCategoriesForFile(file)
}

suspend fun CardviewFileGridBinding.setFileItem(file: File, isGrid: Boolean = false, imageLoader: ImageLoader): Nothing {
    fileName.text = file.getDisplayName(context)
    fileFavorite.isVisible = file.isFavorite
    progressLayout.isGone = true
    filePreview.displayIcon(file, isGrid, progressLayout, filePreview2, imageLoader)
    categoriesLayout.displayCategoriesForFile(file)
}

private fun ItemFileBinding.displayDate(file: File) = fileDate.apply {
    text = if (file.deletedAt.isPositive()) {
        file.getDeletedAt().format(context.getString(R.string.allDeletedFilePattern))
    } else {
        file.getLastModifiedAt().format(context.getString(R.string.allLastModifiedFilePattern))
    }
}

private fun ItemFileBinding.displaySize(file: File) {
    file.size?.let {
        fileSize.text = context.formatShortFileSize(it)
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
    imageLoader: ImageLoader = context.imageLoader,
) {
    scaleType = if (isGrid) ImageView.ScaleType.FIT_CENTER else ImageView.ScaleType.CENTER
    when {
        file.isFolder() -> displayFolderIcon(file)
        else -> displayFileIcon(file, isGrid, progressLayout, filePreview, imageLoader)
    }
}

private fun ImageView.displayFolderIcon(file: File) {
    val (icon, tint) = file.getFolderIcon()
    if (tint == null) load(icon) else load(getTintedDrawable(context, icon, tint))
}

private fun ImageView.displayFileIcon(
    file: File,
    isGrid: Boolean,
    progressLayout: ProgressLayoutView,
    filePreview: ImageView? = null,
    imageLoader: ImageLoader,
) {
    val fileType = file.getFileType()
    val isGraphic = fileType == ExtensionType.IMAGE || fileType == ExtensionType.VIDEO

    when {
        file.hasThumbnail && (isGrid || isGraphic) -> {
            scaleType = ImageView.ScaleType.CENTER_CROP
            loadAny(ApiRoutes.getThumbnailUrl(file), fileType.icon, imageLoader)
        }
        file.isFromUploads && isGraphic -> {
            scaleType = ImageView.ScaleType.CENTER_CROP
            CoroutineScope(Dispatchers.IO).launch {
                val isVideo = file.getMimeType().contains("video")
                val bitmap = context.getLocalThumbnail(fileUri = file.path.toUri(), isVideo = isVideo, thumbnailSize = 100)
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

private suspend fun ItemCategoriesLayoutBinding.displayCategoriesForFile(file: File): Nothing {
    DriveInfosController.categoriesFor(file).collect { categories ->
        displayCategories(categories)
    }
    awaitCancellation()
}

private fun ItemCategoriesLayoutBinding.displayCategories(categories: List<Category>?) = with(root) {
    if (categories.isNullOrEmpty()) {
        isGone = true
        return@with
    } else {
        isVisible = true
    }
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

fun ProgressLayoutView.setupFileProgress(file: File, containsProgress: Boolean = false) {
    when {
        !containsProgress && file.isMarkedAsOffline -> {
            setIndeterminateProgress()
            isVisible = true
        }
        containsProgress && file.currentProgress in 0..99 -> {
            setProgress(file)
            isVisible = true
        }
        file.isOfflineFile(context, checkLocalFile = false) && !file.isFolder() -> {
            hideProgress()
            isVisible = true
        }
        else -> {
            isGone = true
        }
    }
}

enum class TypeFolder(val iconHorizontalMargin: Int) {
    fileList(10.toPx()),
    recentFolder(16.toPx()),
}
