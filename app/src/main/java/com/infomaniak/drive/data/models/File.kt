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
package com.infomaniak.drive.data.models

import android.content.Context
import android.net.Uri
import android.os.Parcelable
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.documentprovider.CloudStorageProvider
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.file.FileConversion
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.data.models.file.FileExternalImport.FileExternalImportStatus
import com.infomaniak.drive.data.models.file.FileVersion
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.RealmListParceler.FileRealmListParceler
import com.infomaniak.drive.utils.RealmListParceler.IntRealmListParceler
import com.infomaniak.drive.utils.Utils.INDETERMINATE_PROGRESS
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.BuildConfig
import com.infomaniak.lib.core.utils.contains
import com.infomaniak.lib.core.utils.guessMimeType
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Ignore
import io.realm.annotations.LinkingObjects
import io.realm.annotations.PrimaryKey
import io.sentry.Sentry
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import kotlinx.parcelize.WriteWith
import java.util.Date

@Parcelize
open class File(
    @PrimaryKey var id: Int = 0,
    @SerializedName("parent_id")
    var parentId: Int = 0,
    @SerializedName("drive_id")
    var driveId: Int = 0,
    var name: String = "",
    @SerializedName("sorted_name")
    var sortedName: String = name,
    var path: String = "", // remote path or Uri for uploadFile
    var type: String = "file",
    var status: String? = null,
    var visibility: String = "",
    @SerializedName("created_by")
    var createdBy: Int = 0,
    @SerializedName("created_at")
    var createdAt: Long = 0,
    @SerializedName("added_at")
    var addedAt: Long = 0,
    @SerializedName("last_modified_at")
    var lastModifiedAt: Long = 0,
    @SerializedName("deleted_by")
    var deletedBy: Int = 0,
    @SerializedName("deleted_at")
    var deletedAt: Long = 0,
    var users: @WriteWith<IntRealmListParceler> RealmList<Int> = RealmList(),
    @SerializedName("is_favorite")
    var isFavorite: Boolean = false,
    var sharelink: ShareLink? = null,
    @SerializedName("capabilities")
    var rights: Rights? = null,
    var categories: @RawValue RealmList<FileCategory> = RealmList(),

    /**
     * DIRECTORY ONLY
     */
    var color: String? = null,
    var dropbox: DropBox? = null,
    @SerializedName("external_import")
    var externalImport: FileExternalImport? = null,

    /**
     * FILE ONLY
     */
    var size: Long? = null,
    @SerializedName("has_thumbnail")
    var hasThumbnail: Boolean = false,
    @SerializedName("has_onlyoffice")
    var hasOnlyoffice: Boolean = false,
    @SerializedName("extension_type")
    var extensionType: String = "",
    var version: FileVersion? = null,
    @SerializedName("conversion_capabilities")
    var conversion: FileConversion? = null,

    /**
     * LOCAL
     */
    var children: @WriteWith<FileRealmListParceler> RealmList<File> = RealmList(),
    var isComplete: Boolean = false,
    var isFromActivities: Boolean = false,
    var isFromSearch: Boolean = false,
    var isFromUploads: Boolean = false,
    var isOffline: Boolean = false,
    var versionCode: Int = 0,
    var responseAt: Long = 0,

    ) : RealmObject(), Parcelable {

    @LinkingObjects("children")
    val localParent: RealmResults<File>? = null

    @Ignore
    var driveColor: String = "#5C89F7"

    @Ignore
    var currentProgress: Int = INDETERMINATE_PROGRESS

    fun isManagedAndValidByRealm() = isManaged && isValid

    fun isNotManagedByRealm() = !isManaged

    fun isUsable() = isManagedAndValidByRealm() || isNotManagedByRealm()

    fun isFolder(): Boolean {
        return type == "dir"
    }

    fun isDrive(): Boolean {
        return type == "drive"
    }

    fun isOnlyOfficePreview(): Boolean {
        return hasOnlyoffice || conversion?.whenOnlyoffice == true
    }

    fun isDropBox() = getVisibilityType() == VisibilityType.IS_DROPBOX

    fun isTrashed(): Boolean {
        return status?.contains("trash") == true
    }

    fun thumbnail(): String {
        return if (isTrashed()) ApiRoutes.thumbnailTrashFile(this) else ApiRoutes.thumbnailFile(this)
    }

    fun imagePreview(): String {
        return "${ApiRoutes.imagePreviewFile(this)}&width=2500&height=1500&quality=80"
    }

    fun onlyOfficeUrl() = "${BuildConfig.AUTOLOG_URL}?url=" + ApiRoutes.showOffice(this)

    fun getFileType(): ExtensionType {
        return if (isFromUploads) getFileTypeFromExtension() else when (extensionType) {
            ExtensionType.ARCHIVE.value -> ExtensionType.ARCHIVE
            ExtensionType.AUDIO.value -> ExtensionType.AUDIO
            ExtensionType.CODE.value -> if (isBookmark()) ExtensionType.URL else ExtensionType.CODE
            ExtensionType.FONT.value -> ExtensionType.FONT
            ExtensionType.IMAGE.value -> ExtensionType.IMAGE
            ExtensionType.PDF.value -> ExtensionType.PDF
            ExtensionType.PRESENTATION.value -> ExtensionType.PRESENTATION
            ExtensionType.SPREADSHEET.value -> ExtensionType.SPREADSHEET
            ExtensionType.TEXT.value -> ExtensionType.TEXT
            ExtensionType.VIDEO.value -> ExtensionType.VIDEO
            ExtensionType.FORM.value -> ExtensionType.FORM
            else -> when {
                isFolder() -> ExtensionType.FOLDER
                isBookmark() -> ExtensionType.URL
                else -> ExtensionType.UNKNOWN
            }
        }
    }

    fun getLastModifiedAt(): Date {
        return Date(getLastModifiedInMilliSecond())
    }

    fun getLastModifiedInMilliSecond() = lastModifiedAt * 1000

    fun getAddedAt(): Date {
        return Date(addedAt * 1000)
    }

    fun getFileCreatedAt(): Date {
        return Date(createdAt * 1000)
    }

    fun getDeletedAt(): Date {
        return Date(deletedAt * 1000)
    }

    fun getFileName(): String {
        val fileExtension = getFileExtension() ?: ""
        return when {
            fileExtension.isBlank() || isFolder() -> name
            else -> name.substringBeforeLast(fileExtension)
        }
    }

    fun getRemotePath(userDrive: UserDrive = UserDrive()): String {
        return if (path.isBlank() && id != ROOT_ID) FileController.generateAndSavePath(id, userDrive) else path
    }

    fun getFileExtension(): String? {
        val extension = name.substringAfterLast('.')
        return if (extension == name) null else ".$extension"
    }

    fun isBookmark() = name.endsWith(".url") || name.endsWith(".webloc")

    fun isPendingUploadFolder() = isFromUploads && (isFolder() || isDrive())

    fun isObsolete(dataFile: IOFile): Boolean {
        return (dataFile.lastModified() / 1000) < lastModifiedAt
    }

    fun isIntactFile(dataFile: IOFile): Boolean {
        return dataFile.length() == size
    }

    fun isObsoleteOrNotIntact(dataFile: IOFile): Boolean {
        return isObsolete(dataFile) || !isIntactFile(dataFile)
    }

    fun getStoredFile(context: Context, userDrive: UserDrive = UserDrive()): IOFile? {
        return if (isOffline) getOfflineFile(context, userDrive.userId) else getCacheFile(context, userDrive)
    }

    fun canUseStoredFile(context: Context, userDrive: UserDrive = UserDrive()): Boolean {
        return getStoredFile(context, userDrive)?.let(::isObsoleteOrNotIntact) == false
    }

    fun isOfflineFile(context: Context, userId: Int = AccountUtils.currentUserId, checkLocalFile: Boolean = true): Boolean {
        return isOffline || (checkLocalFile && !isFolder() && getOfflineFile(context, userId)?.exists() == true)
    }

    /**
     * File is offline and local file is the same as in the server (same modification date and size)
     */
    fun isOfflineAndIntact(offlineFile: IOFile): Boolean {
        return isOffline && ((offlineFile.lastModified() / 1000) == lastModifiedAt && isIntactFile(offlineFile))
    }

    fun getConvertedPdfCache(context: Context, userDrive: UserDrive): IOFile {
        val folder = IOFile(context.cacheDir, "converted_pdf/${userDrive.userId}/${userDrive.driveId}")
        if (!folder.exists()) folder.mkdirs()
        return IOFile(folder, id.toString())
    }

    fun getOfflineFile(context: Context, userId: Int = AccountUtils.currentUserId): IOFile? {
        val userDrive = UserDrive(userId, driveId)
        val rootFolder = IOFile(getOfflineFolder(context), "${userId}/$driveId")
        val path = getRemotePath(userDrive)

        if (path.isEmpty()) return null
        val folder = IOFile(rootFolder, path.substringBeforeLast("/"))

        if (!folder.exists()) folder.mkdirs()
        return IOFile(folder, name)
    }

    fun getCacheFile(context: Context, userDrive: UserDrive = UserDrive()): IOFile {
        val folder = IOFile(context.cacheDir, "cloud_storage/${userDrive.userId}/${userDrive.driveId}")
        if (!folder.exists()) folder.mkdirs()
        return IOFile(folder, id.toString())
    }

    fun deleteCaches(context: Context) {
        if (isOffline) getOfflineFile(context)?.apply { if (exists()) delete() }
        else getCacheFile(context).apply { if (exists()) delete() }
    }

    fun isDisabled(): Boolean {
        return rights?.canRead == false && rights?.canShow == false
    }

    fun isImporting(): Boolean {
        return externalImport?.let {
            it.status == FileExternalImportStatus.IN_PROGRESS.value
                    || it.status == FileExternalImportStatus.WAITING.value
                    || isCancelingImport()
        } ?: false
    }

    fun isCancelingImport() = externalImport?.status == FileExternalImportStatus.CANCELING.value

    fun isRoot(): Boolean {
        return id == ROOT_ID
    }

    fun getWorkerTag() = "${id}_$driveId"

    fun isPendingOffline(context: Context): Boolean {
        val get = WorkManager.getInstance(context).getWorkInfosByTag(getWorkerTag()).get()
        return get.any {
            it.state == WorkInfo.State.ENQUEUED
                    || it.state == WorkInfo.State.RUNNING
                    || it.state == WorkInfo.State.BLOCKED
        }
    }

    fun getMimeType(): String = name.guessMimeType()

    enum class Type(val value: String) {
        FILE("file"),
        DIRECTORY("dir"),
        DRIVE("drive");
    }

    fun getVisibilityType(): VisibilityType {
        return when (visibility) {
            "is_root" -> VisibilityType.ROOT
            "is_team_space" -> VisibilityType.IS_TEAM_SPACE
            "is_team_space_folder" -> VisibilityType.IS_TEAM_SPACE_FOLDER
            "is_in_team_space_folder" -> VisibilityType.IS_IN_TEAM_SPACE_FOLDER
            "is_shared_space" -> VisibilityType.IS_SHARED_SPACE
            else -> {
                when {
                    dropbox != null -> VisibilityType.IS_DROPBOX
                    users.size > 1 -> VisibilityType.IS_SHARED
                    else -> VisibilityType.IS_PRIVATE
                }
            }
        }
    }

    // TODO This function is called in the FileAdapter, for each File, and is getting the RealmInstance each time. This is not very efficient.
    fun getCategories(): List<Category> {
        val fileCategoriesIds = getSortedCategoriesIds()
        return DriveInfosController.getCategoriesFromIds(driveId, fileCategoriesIds.toTypedArray())
    }

    private fun getSortedCategoriesIds(): List<Int> {
        return if (isManaged) {
            categories.sort(FileCategory::addedAt.name).map { it.categoryId }
        } else {
            runCatching {
                categories.sortedBy { it.addedAt }.map { it.categoryId }
            }.onFailure {
                Sentry.withScope { scope ->
                    scope.setExtra("categories", categories.joinToString { "id: ${it.categoryId} addedAt: ${it.addedAt}" })
                    Sentry.captureException(it)
                }
            }.getOrDefault(emptyList())
        }
    }

    fun isAllowedToBeColored(): Boolean {
        return !isDisabled()
                && isFolder()
                && getVisibilityType() != VisibilityType.IS_SHARED_SPACE
                && getVisibilityType() != VisibilityType.IS_TEAM_SPACE
    }

    fun hasCreationRight() = isFolder() && rights?.canCreateFile == true

    // For applyFileActivity in FileController
    override fun equals(other: Any?): Boolean {
        if (other is File) {
            return isUsable() && other.isUsable() && other.id == id
        }
        return super.equals(other)
    }

    override fun hashCode(): Int {
        var result = id
        result = 31 * result + driveId
        return result
    }

    enum class VisibilityType {
        ROOT,
        IS_PRIVATE,
        IS_DROPBOX,
        IS_SHARED,
        IS_SHARED_SPACE,
        IS_TEAM_SPACE,
        IS_TEAM_SPACE_FOLDER,
        IS_IN_TEAM_SPACE_FOLDER;
    }

    enum class Office(val extensionType: ExtensionType, val extension: String) {
        DOCS(ExtensionType.TEXT, "docx"),
        POINTS(ExtensionType.PRESENTATION, "pptx"),
        GRIDS(ExtensionType.SPREADSHEET, "xlsx"),
        FORM(ExtensionType.FORM, "docxf"),
        TXT(ExtensionType.TEXT, "txt")
    }

    enum class SortType(val order: String, val orderBy: String, val translation: Int) {
        NAME_AZ("asc", "path", R.string.sortNameAZ),
        NAME_ZA("desc", "path", R.string.sortNameZA),
        OLDER("asc", "last_modified_at", R.string.sortOlder),
        RECENT("desc", "last_modified_at", R.string.sortRecent),
        OLDEST_ADDED("asc", "added_at", R.string.sortOldestAdded),
        MOST_RECENT_ADDED("desc", "added_at", R.string.sortMostRecentAdded),
        OLDER_TRASHED("asc", "deleted_at", R.string.sortOlder),
        RECENT_TRASHED("desc", "deleted_at", R.string.sortRecent),
        SMALLER("asc", "size", R.string.sortSmaller),
        BIGGER("desc", "size", R.string.sortBigger),
        // EXTENSION("asc", "extension", R.string.sortExtension); // TODO: Awaiting API
    }

    enum class SortTypeUsage { FILE_LIST, TRASH }

    @Parcelize
    enum class FolderPermission(
        override val icon: Int,
        override val translation: Int,
        override val description: Int
    ) : Permission {
        ONLY_ME(
            R.drawable.ic_account,
            R.string.createFolderMeOnly,
            R.string.createFolderMeOnly
        ),
        INHERIT(
            R.drawable.ic_users,
            R.string.createFolderKeepParentsRightTitle,
            R.string.createFolderKeepParentsRightDescription
        ),
        SPECIFIC_USERS(
            R.drawable.ic_users,
            R.string.createFolderSomeUsersTitle,
            R.string.createFolderSomeUsersDescription
        ),
        ALL_DRIVE_USERS(
            R.drawable.ic_drive,
            R.string.allAllDriveUsers,
            R.string.createCommonFolderAllUsersDescription
        )
    }

    companion object {

        /**
         * This method is here, and not directly a class method in the File class, because of a supposed Realm bug.
         * When we try to put it in the File class, the app doesn't build anymore, because of a "broken method".
         * This is not the only method in this case, search this comment in the project, and you'll see.
         * Realm's Github issue: https://github.com/realm/realm-java/issues/7637
         */
        fun File.getCloudAndFileUris(context: Context, userDrive: UserDrive = UserDrive()): Pair<Uri, Uri> {
            val cloudUri = CloudStorageProvider.createShareFileUri(context, this, userDrive)!!
            val offlineFile = getOfflineFile(context, userDrive.userId)

            return cloudUri to if (isOffline && offlineFile != null) {
                // We use the uri with scheme file because Microsoft Office don't support modifications from a content uri
                if (isOnlyOfficePreview()) {
                    offlineFile.toUri()
                } else {
                    FileProvider.getUriForFile(context, context.getString(R.string.FILE_AUTHORITY), offlineFile)
                }
            } else {
                cloudUri
            }
        }

        /**
         * This method is here, and not directly a class method in the File class, because of a supposed Realm bug.
         * When we try to put it in the File class, the app doesn't build anymore, because of a "broken method".
         * This is not the only method in this case, search this comment in the project, and you'll see.
         * Realm's Github issue: https://github.com/realm/realm-java/issues/7637
         */
        fun getOfflineFolder(context: Context): IOFile {
            val mediaFolder = context.externalMediaDirs?.firstOrNull() ?: context.filesDir
            return IOFile(mediaFolder, context.getString(R.string.EXPOSED_OFFLINE_DIR))
        }

        /**
         * This method is here, and not directly a class method in the File class, because of a supposed Realm bug.
         * When we try to put it in the File class, the app doesn't build anymore, because of a "broken method".
         * This is not the only method in this case, search this comment in the project, and you'll see.
         * Realm's Github issue: https://github.com/realm/realm-java/issues/7637
         */
        fun File.getFileTypeFromExtension(): ExtensionType {
            return when (getMimeType()) {
                in Regex("application/(zip|rar|x-tar|.*compressed|.*archive)") -> ExtensionType.ARCHIVE
                in Regex("audio/") -> ExtensionType.AUDIO
                in Regex("image/") -> ExtensionType.IMAGE
                in Regex("/pdf") -> ExtensionType.PDF
                in Regex("presentation") -> ExtensionType.PRESENTATION
                in Regex("spreadsheet|excel|comma-separated-values") -> ExtensionType.SPREADSHEET
                in Regex("document|text/plain|msword") -> ExtensionType.TEXT
                in Regex("video/") -> ExtensionType.VIDEO
                in Regex("text/|application/") -> ExtensionType.CODE
                else -> if (getFileExtension() == ".${Office.FORM.extension}") ExtensionType.FORM else ExtensionType.UNKNOWN
            }
        }
    }
}
