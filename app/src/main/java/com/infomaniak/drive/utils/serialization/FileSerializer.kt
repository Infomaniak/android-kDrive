/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.utils.serialization

import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.data.models.Rights
import com.infomaniak.drive.data.models.ShareLink
import com.infomaniak.drive.data.models.file.FileConversion
import com.infomaniak.drive.data.models.file.FileExternalImport
import com.infomaniak.drive.data.models.file.FileVersion
import io.realm.RealmList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * Serializes a [File] through a [FileSurrogate] that only holds the properties exchanged with the API (the local-only
 * properties, such as the offline flags, are never read from nor written to the payload).
 * Every decoded [File] (including nested children) gets its primary key derived from its id and drive id, exactly like
 * `File.initUid()` did.
 */
object FileSerializer : KSerializer<File> {

    override val descriptor: SerialDescriptor = FileSurrogate.serializer().descriptor

    override fun serialize(encoder: Encoder, value: File) {
        encoder.encodeSerializableValue(FileSurrogate.serializer(), FileSurrogate.from(value))
    }

    override fun deserialize(decoder: Decoder): File {
        return decoder.decodeSerializableValue(FileSurrogate.serializer()).toFile().apply { initUid() }
    }
}

@Serializable
@SerialName("File")
private class FileSurrogate(
    val id: Int = 0,
    @SerialName("parent_id")
    val parentId: Int = 0,
    @SerialName("drive_id")
    val driveId: Int = 0,
    val name: String = "",
    @SerialName("sorted_name")
    val sortedName: String? = null,
    val path: String = "",
    val type: String = "file",
    val status: String? = null,
    val visibility: String = "",
    @SerialName("created_by")
    val createdBy: Int = 0,
    @SerialName("created_at")
    val createdAt: Long = 0,
    @SerialName("added_at")
    val addedAt: Long = 0,
    @SerialName("last_modified_at")
    val lastModifiedAt: Long = 0,
    @SerialName("deleted_by")
    val deletedBy: Int = 0,
    @SerialName("deleted_at")
    val deletedAt: Long = 0,
    @Serializable(with = RealmIntListSerializer::class)
    val users: RealmList<Int> = RealmList(),
    @SerialName("is_favorite")
    val isFavorite: Boolean = false,
    @SerialName("sharelink")
    val shareLink: ShareLink? = null,
    @SerialName("capabilities")
    val rights: Rights? = null,
    @Serializable(with = RealmFileCategoryListSerializer::class)
    val categories: RealmList<FileCategory> = RealmList(),
    val color: String? = null,
    val dropbox: DropBox? = null,
    @SerialName("external_import")
    val externalImport: FileExternalImport? = null,
    val size: Long? = null,
    @SerialName("supported_by")
    @Serializable(with = RealmStringListSerializer::class)
    val supportedBy: RealmList<String>? = null,
    @SerialName("extension_type")
    val extensionType: String = "",
    val version: FileVersion? = null,
    @SerialName("conversion_capabilities")
    val conversion: FileConversion? = null,
    @SerialName("revised_at")
    val revisedAt: Long = 0,
    @SerialName("updated_at")
    val updatedAt: Long = 0,
    @Serializable(with = RealmFileListSerializer::class)
    val children: RealmList<File> = RealmList(),
) {

    fun toFile() = File(
        id = id,
        parentId = parentId,
        driveId = driveId,
        name = name,
        sortedName = sortedName ?: name,
        path = path,
        type = type,
        status = status,
        visibility = visibility,
        createdBy = createdBy,
        createdAt = createdAt,
        addedAt = addedAt,
        lastModifiedAt = lastModifiedAt,
        deletedBy = deletedBy,
        deletedAt = deletedAt,
        users = users,
        isFavorite = isFavorite,
        shareLink = shareLink,
        rights = rights,
        categories = categories,
        color = color,
        dropbox = dropbox,
        externalImport = externalImport,
        size = size,
        supportedBy = supportedBy,
        extensionType = extensionType,
        version = version,
        conversion = conversion,
        revisedAt = revisedAt,
        updatedAt = updatedAt,
        children = children,
    )

    companion object {
        fun from(file: File) = FileSurrogate(
            id = file.id,
            parentId = file.parentId,
            driveId = file.driveId,
            name = file.name,
            sortedName = file.sortedName,
            path = file.path,
            type = file.type,
            status = file.status,
            visibility = file.visibility,
            createdBy = file.createdBy,
            createdAt = file.createdAt,
            addedAt = file.addedAt,
            lastModifiedAt = file.lastModifiedAt,
            deletedBy = file.deletedBy,
            deletedAt = file.deletedAt,
            users = file.users,
            isFavorite = file.isFavorite,
            shareLink = file.shareLink,
            rights = file.rights,
            categories = file.categories,
            color = file.color,
            dropbox = file.dropbox,
            externalImport = file.externalImport,
            size = file.size,
            supportedBy = file.supportedBy,
            extensionType = file.extensionType,
            version = file.version,
            conversion = file.conversion,
            revisedAt = file.revisedAt,
            updatedAt = file.updatedAt,
            children = file.children,
        )
    }
}

/** Non-generic variant, because kapt stubs cannot reference a generic serializer from `@Serializable(with = ...)`. */
object RealmFileListSerializer : KSerializer<RealmList<File>> by RealmListSerializer(FileSerializer)
