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
package com.infomaniak.drive.data.cache

import androidx.core.os.bundleOf
import com.infomaniak.drive.data.models.DropBox
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.FileCategory
import com.infomaniak.drive.data.models.Rights
import com.infomaniak.drive.data.models.file.FileVersion
import com.infomaniak.drive.data.models.file.dropbox.DropBoxCapabilities
import com.infomaniak.drive.data.models.file.dropbox.DropBoxSize
import com.infomaniak.drive.data.models.file.dropbox.DropBoxValidity
import com.infomaniak.drive.utils.AccountUtils
import io.realm.DynamicRealm
import io.realm.FieldAttribute
import io.realm.RealmMigration
import io.realm.RealmSchema
import io.sentry.Sentry
import io.sentry.SentryLevel
import java.util.*

class FileMigration : RealmMigration {
    companion object {
        const val bddVersion = 4L // Must be bumped when the schema changes

        const val LOGOUT_CURRENT_USER_TAG = "logout_current_user_tag"
    }

    override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
        var oldVersionTemp = oldVersion

        // DynamicRealm exposes an editable schema
        val schema = realm.schema

        // Migrated to version 1
        if (oldVersionTemp == 0L) {
            schema.get(File::class.java.simpleName)?.apply {
                removeField("order")
                removeField("orderBy")
                if (hasField("canUseTag")) removeField("canUseTag")
                if (hasField("tags")) removeField("tags")
                if (hasField("isWaitingOffline")) removeField("isWaitingOffline")
                if (!hasField(File::isFromSearch.name)) {
                    addField(File::isFromSearch.name, Boolean::class.java, FieldAttribute.REQUIRED)
                }
                if (!hasField(File::isFromUploads.name)) {
                    addField(File::isFromUploads.name, Boolean::class.java, FieldAttribute.REQUIRED)
                }
            }
            oldVersionTemp++
        }

        // Migrated to version 2:
        // - Added new field (FileCategory list) in File table
        // - Modified field (Rights) in File table (remove PrimaryKey & Id, and switched to Embedded)
        if (oldVersionTemp == 1L) {
            val fileCategorySchema = schema.create(FileCategory::class.java.simpleName).apply {
                addField(FileCategory::id.name, Int::class.java, FieldAttribute.REQUIRED)
                addField(FileCategory::iaCategoryUserValidation.name, String::class.java, FieldAttribute.REQUIRED)
                addField(FileCategory::isGeneratedByIa.name, Boolean::class.java, FieldAttribute.REQUIRED)
                addField(FileCategory::userId.name, Int::class.java).setNullable(FileCategory::userId.name, true)
                addField(FileCategory::addedToFileAt.name, Date::class.java, FieldAttribute.REQUIRED)
            }
            schema.get(File::class.java.simpleName)?.apply {
                addRealmListField(File::categories.name, fileCategorySchema)
            }
            schema.get(FileCategory::class.java.simpleName)?.apply {
                isEmbedded = true
            }
            // Rights migration with sentry logs
            val sentryLogs = arrayListOf<Pair<Int, String>>()
            var countOfflineFiles = 0
            runCatching {
                schema.get(Rights::class.java.simpleName)?.transform { // apply for each right
                    val fileId = it.getInt("fileId")
                    val file = realm.where(File::class.java.simpleName).equalTo(File::id.name, fileId).findFirst()
                    if (file == null) {
                        it.deleteFromRealm() // Delete if it's orphan
                        sentryLogs.add(fileId to "right is orphan true")
                    }
                    // Count offline files for sentry log
                    if (file?.getBoolean(File::isOffline.name) == true) countOfflineFiles++
                }?.apply {
                    removePrimaryKey()
                    removeField("fileId")
                    isEmbedded = true
                }

            }.onFailure { exception ->
                exception.printStackTrace()
                // On some clients, it happens that isEmbedded is added in an orphan file without knowing why
                // So we add these sentry logs to have more info
                // We have an issue here: https://github.com/realm/realm-java/issues/7642
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setExtra("oldVersion", "$oldVersion")
                    scope.setExtra("count orphan files", "${sentryLogs.count()}")
                    scope.setExtra("count offline files", "$countOfflineFiles")
                    scope.setExtra("logs", sentryLogs.toString())
                    Sentry.captureException(exception)
                }

                temporaryMigrationFixToV2(realm, schema)
            }

            oldVersionTemp++
        }

        // Migrated to version 3:
        // - Added new field (Folder Color) in File table
        // - Added new field (Version Code) in File table
        if (oldVersionTemp == 2L) {
            schema.get(File::class.java.simpleName)?.apply {
                addField("_color", String::class.java)
                addField("versionCode", Int::class.java)
            }
            oldVersionTemp++
        }

        //region Migrated to version 4:
        // - Add new field (Dropbox) in File table

        if (oldVersionTemp == 3L) {
            // Dropbox migration
            val dropboxValiditySchema = schema.create(DropBoxValidity::class.java.simpleName).apply {
                addField(DropBoxValidity::date.name, Date::class.java)
                addField(DropBoxValidity::hasExpired.name, Boolean::class.java)
            }
            val dropboxSizeSchema = schema.create(DropBoxSize::class.java.simpleName).apply {
                addField(DropBoxSize::limit.name, Long::class.java)
                addField(DropBoxSize::remaining.name, Int::class.java)
            }
            val dropboxCapabilitiesSchema = schema.create(DropBoxCapabilities::class.java.simpleName).apply {
                addField(DropBoxCapabilities::hasPassword.name, Boolean::class.java, FieldAttribute.REQUIRED)
                addField(DropBoxCapabilities::hasNotification.name, Boolean::class.java, FieldAttribute.REQUIRED)
                addField(DropBoxCapabilities::hasValidity.name, Boolean::class.java, FieldAttribute.REQUIRED)
                addField(DropBoxCapabilities::hasSizeLimit.name, Boolean::class.java, FieldAttribute.REQUIRED)
                addRealmObjectField(DropBoxCapabilities::validity.name, dropboxValiditySchema)
                addRealmObjectField(DropBoxCapabilities::size.name, dropboxSizeSchema)
            }
            val dropboxSchema = schema.create(DropBox::class.java.simpleName).apply {
                addField(DropBox::id.name, Int::class.java, FieldAttribute.REQUIRED)
                addField(DropBox::name.name, String::class.java, FieldAttribute.REQUIRED)
                addRealmObjectField(DropBox::capabilities.name, dropboxCapabilitiesSchema)
                addField(DropBox::url.name, String::class.java, FieldAttribute.REQUIRED)
                addField(DropBox::uuid.name, String::class.java, FieldAttribute.REQUIRED)
                addField(DropBox::createdAt.name, Date::class.java)
                addField(DropBox::createdBy.name, Int::class.java, FieldAttribute.REQUIRED)
                addField(DropBox::lastUploadedAt.name, Long::class.java)
                addField(DropBox::collaborativeUsersCount.name, Int::class.java, FieldAttribute.REQUIRED)
                addField(DropBox::updatedAt.name, Date::class.java)
            }

            // FileVersion migration
            val fileVersionSchema = schema.create(FileVersion::class.java.simpleName).apply {
                addField(FileVersion::isMultiple.name, Boolean::class.java, FieldAttribute.REQUIRED)
                addField(FileVersion::number.name, Int::class.java, FieldAttribute.REQUIRED)
                addField(FileVersion::totalSize.name, Long::class.java, FieldAttribute.REQUIRED)
            }
        }
        //endregion
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        return true
    }

    override fun hashCode(): Int {
        return javaClass.hashCode()
    }

    private fun temporaryMigrationFixToV2(realm: DynamicRealm, schema: RealmSchema) {
        val offlineFile = realm.where(File::class.java.simpleName).equalTo(File::isOffline.name, true).findFirst()

        // Delete all realm DB
        realm.deleteAll()
        // Continue migration
        schema.get(Rights::class.java.simpleName)?.isEmbedded = true

        // Logout the current user if there is at least one offline file
        offlineFile?.let {
            // Logout current user
            AccountUtils.reloadApp?.invoke(bundleOf(LOGOUT_CURRENT_USER_TAG to true))
        }
    }
}
