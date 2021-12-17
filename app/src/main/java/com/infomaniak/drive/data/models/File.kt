/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2021 Infomaniak Network SA
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
import android.os.Parcelable
import android.webkit.MimeTypeMap
import androidx.annotation.DrawableRes
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.gson.annotations.SerializedName
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.RealmListParceler.FileRealmListParceler
import com.infomaniak.drive.utils.RealmListParceler.IntRealmListParceler
import com.infomaniak.drive.utils.Utils.INDETERMINATE_PROGRESS
import com.infomaniak.drive.utils.Utils.ROOT_ID
import com.infomaniak.lib.core.BuildConfig
import io.realm.RealmList
import io.realm.RealmObject
import io.realm.RealmResults
import io.realm.annotations.Ignore
import io.realm.annotations.LinkingObjects
import io.realm.annotations.PrimaryKey
import kotlinx.android.parcel.Parcelize
import kotlinx.android.parcel.WriteWith
import java.util.*

@Parcelize
open class File(
    @PrimaryKey var id: Int = 0,
    var children: @WriteWith<FileRealmListParceler> RealmList<File> = RealmList(),
    @SerializedName("collaborative_folder")
    var collaborativeFolder: String? = null,
    @SerializedName("converted_type")
    var convertedType: String = "",
    @SerializedName("created_at")
    var createdAt: Long = 0,
    @SerializedName("created_by")
    var createdBy: Int = 0,
    @SerializedName("deleted_at")
    var deletedAt: Long = 0,
    @SerializedName("deleted_by")
    var deletedBy: Int = 0,
    @SerializedName("drive_id")
    var driveId: Int = 0,
    @SerializedName("file_created_at")
    var fileCreatedAt: Long = 0,
    @SerializedName("has_thumbnail")
    var hasThumbnail: Boolean = false,
    @SerializedName("has_version")
    var hasVersion: Boolean = false,
    @SerializedName("is_favorite")
    var isFavorite: Boolean = false,
    @SerializedName("last_accessed_at")
    var lastAccessedAt: Long = 0,
    @SerializedName("last_modified_at")
    var lastModifiedAt: Long = 0,
    var name: String = "",
    @SerializedName("name_natural_sorting")
    var nameNaturalSorting: String = name,
    @SerializedName("nb_version")
    var nbVersion: Int = 0,
    var onlyoffice: Boolean = false,
    @SerializedName("onlyoffice_convert_extension")
    var onlyofficeConvertExtension: String? = null,
    var path: String = "", // Uri
    var rights: Rights? = null,
    @SerializedName("share_link")
    var shareLink: String? = null,
    var size: Long? = null,
    @SerializedName("size_with_version")
    var sizeWithVersions: Long? = null,
    var status: String? = null,
    var type: String = "file",
    var users: @WriteWith<IntRealmListParceler> RealmList<Int> = RealmList(),
    var visibility: String = "",

    var responseAt: Long = 0,

    /**
     * Local
     */
    var isComplete: Boolean = false,
    var isOffline: Boolean = false,
    var isFromActivities: Boolean = false,
    var isFromSearch: Boolean = false,
    var isFromUploads: Boolean = false,
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
        return onlyoffice || onlyofficeConvertExtension != null
    }

    fun isDropBox() = getVisibilityType() == VisibilityType.IS_COLLABORATIVE_FOLDER

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

    fun getFileType(): ConvertedType {
        return when (convertedType) {
            ConvertedType.ARCHIVE.value -> ConvertedType.ARCHIVE
            ConvertedType.AUDIO.value -> ConvertedType.AUDIO
            ConvertedType.CODE.value -> if (isBookmark()) ConvertedType.URL else ConvertedType.CODE
            ConvertedType.FONT.value -> ConvertedType.FONT
            ConvertedType.IMAGE.value -> ConvertedType.IMAGE
            ConvertedType.PDF.value -> ConvertedType.PDF
            ConvertedType.PRESENTATION.value -> ConvertedType.PRESENTATION
            ConvertedType.SPREADSHEET.value -> ConvertedType.SPREADSHEET
            ConvertedType.TEXT.value -> ConvertedType.TEXT
            ConvertedType.VIDEO.value -> ConvertedType.VIDEO
            else -> if (isFolder()) ConvertedType.FOLDER else ConvertedType.UNKNOWN
        }
    }

    fun getLastModifiedAt(): Date {
        return Date(getLastModifiedInMilliSecond())
    }

    fun getLastModifiedInMilliSecond() = lastModifiedAt * 1000

    fun getCreatedAt(): Date {
        return Date(createdAt * 1000)
    }

    fun getFileCreatedAt(): Date {
        return Date(fileCreatedAt * 1000)
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

    fun initRightIds() {
        rights?.let { it.fileId = id }
        children.forEach {
            it.initRightIds()
        }
    }


    fun isBookmark() = name.endsWith(".url") || name.endsWith(".webloc")

    fun isPendingUploadFolder() = isFromUploads && (isFolder() || isDrive())

    fun isObsolete(dataFile: java.io.File): Boolean {
        return (dataFile.lastModified() / 1000) < lastModifiedAt
    }

    fun isIntactFile(dataFile: java.io.File): Boolean {
        return dataFile.length() == size
    }

    fun isOfflineFile(context: Context, userId: Int = AccountUtils.currentUserId, checkLocalFile: Boolean = true): Boolean {
        return isOffline || (checkLocalFile && !isFolder() && getOfflineFile(context, userId)?.exists() == true)
    }

    /**
     * File is offline and local file is the same as in the server (same modification date and size)
     */
    fun isOfflineAndIntact(offlineFile: java.io.File): Boolean {
        return isOffline && ((offlineFile.lastModified() / 1000) == lastModifiedAt && isIntactFile(offlineFile))
    }

    fun getConvertedPdfCache(context: Context, userDrive: UserDrive): java.io.File {
        val folder = java.io.File(context.cacheDir, "converted_pdf/${userDrive.userId}/${userDrive.driveId}")
        if (!folder.exists()) folder.mkdirs()
        return java.io.File(folder, id.toString())
    }

    fun getOfflineFile(context: Context, userId: Int = AccountUtils.currentUserId): java.io.File? {
        val userDrive = UserDrive(userId, driveId)
        val mediaFolder = context.externalMediaDirs?.firstOrNull() ?: context.filesDir
        val rootFolder = java.io.File(mediaFolder, "offline_storage/${userId}/$driveId")
        val path = getRemotePath(userDrive)

        if (path.isEmpty()) return null
        val folder = java.io.File(rootFolder, path.substringBeforeLast("/"))

        if (!folder.exists()) folder.mkdirs()
        return java.io.File(folder, name)
    }

    fun getCacheFile(context: Context, userDrive: UserDrive = UserDrive()): java.io.File {
        val folder = java.io.File(context.cacheDir, "cloud_storage/${userDrive.userId}/${userDrive.driveId}")
        if (!folder.exists()) folder.mkdirs()
        return java.io.File(folder, id.toString())
    }

    fun deleteCaches(context: Context) {
        if (isOffline) getOfflineFile(context)?.apply { if (exists()) delete() }
        else getCacheFile(context).apply { if (exists()) delete() }
    }

    fun isDisabled(): Boolean {
        return rights?.read == false && rights?.show == false
    }

    fun isRoot(): Boolean {
        return id == ROOT_ID
    }

    fun getWorkerTag() = "${id}_$driveId"

    fun isPendingOffline(context: Context): Boolean {
        val get = WorkManager.getInstance(context).getWorkInfosByTag(getWorkerTag()).get()
        return get.firstOrNull {
            it.state == WorkInfo.State.ENQUEUED
                    || it.state == WorkInfo.State.RUNNING
                    || it.state == WorkInfo.State.BLOCKED
        } != null
    }

    fun getMimeType(): String {
        val fileExtension = name.substringAfterLast(".")
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(fileExtension) ?: "*/*"
    }

    enum class Type(val value: String) {
        FILE("file"),
        FOLDER("dir"),
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
                    !collaborativeFolder.isNullOrBlank() -> VisibilityType.IS_COLLABORATIVE_FOLDER
                    users.size > 1 -> VisibilityType.IS_SHARED
                    else -> VisibilityType.IS_PRIVATE
                }
            }
        }
    }

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

    enum class LocalFileActivity {
        IS_NEW, IS_UPDATE, IS_DELETE
    }

    enum class VisibilityType {
        ROOT,
        IS_PRIVATE,
        IS_COLLABORATIVE_FOLDER,
        IS_SHARED,
        IS_SHARED_SPACE,
        IS_TEAM_SPACE,
        IS_TEAM_SPACE_FOLDER,
        IS_IN_TEAM_SPACE_FOLDER;
    }

    enum class Office(val convertedType: ConvertedType, val extension: String) {
        DOCS(ConvertedType.TEXT, "docx"),
        POINTS(ConvertedType.PRESENTATION, "pptx"),
        GRIDS(ConvertedType.SPREADSHEET, "xlsx"),
        TXT(ConvertedType.TEXT, "txt")
    }

    enum class ConvertedType(val value: String, @DrawableRes val icon: Int) {
        ARCHIVE("archive", R.drawable.ic_file_zip),
        AUDIO("audio", R.drawable.ic_file_audio),
        CODE("code", R.drawable.ic_file_code),
        FOLDER("dir", R.drawable.ic_folder_filled),
        FONT("font", R.drawable.ic_file),
        IMAGE("image", R.drawable.ic_file_image),
        PDF("pdf", R.drawable.ic_file_pdf),
        PRESENTATION("presentation", R.drawable.ic_file_presentation),
        SPREADSHEET("spreadsheet", R.drawable.ic_file_sheets),
        TEXT("text", R.drawable.ic_file_text),
        UNKNOWN("unknown", R.drawable.ic_file),
        URL("url", R.drawable.url),
        VIDEO("video", R.drawable.ic_file_video),
    }

    enum class SortType(val order: String, val orderBy: String, val translation: Int) {
        NAME_AZ("asc", "files.path", R.string.sortNameAZ),
        NAME_ZA("desc", "files.path", R.string.sortNameZA),
        OLDER("asc", "last_modified_at", R.string.sortOlder),
        RECENT("desc", "last_modified_at", R.string.sortRecent),
        OLDER_TRASHED("asc", "deleted_at", R.string.sortOlder),
        RECENT_TRASHED("desc", "deleted_at", R.string.sortRecent),
        SMALLER("asc", "files.size", R.string.sortSmaller),
        BIGGER("desc", "files.size", R.string.sortBigger),
        //EXTENSION("asc", "extension", R.string.sortExtension); // TODO : Awaiting API
    }

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
}
