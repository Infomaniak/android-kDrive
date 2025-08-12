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
package com.infomaniak.drive.data.models

import com.infomaniak.core.flowOnNewHandlerThread
import com.infomaniak.drive.utils.RealmModules
import io.realm.DynamicRealm
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmMigration
import io.realm.RealmObject
import io.realm.kotlin.toFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch

open class AppSettings(
    var _appLaunchesCount: Int = 0,
    var _appSecurityEnabled: Boolean = false,
    var _currentDriveId: Int = -1,
    var _currentUserId: Int = -1,
    var _onlyWifiSync: Boolean = false,
) : RealmObject() {

    fun update(appSettings: AppSettings) {
        this._appLaunchesCount = appSettings._appLaunchesCount
        this._appSecurityEnabled = appSettings._appSecurityEnabled
        this._currentDriveId = appSettings._currentDriveId
        this._currentUserId = appSettings._currentUserId
        this._onlyWifiSync = appSettings._onlyWifiSync
    }

    companion object {
        private const val DB_NAME = "AppSettings.realm"
        private val realmConfiguration: RealmConfiguration = RealmConfiguration.Builder().name(DB_NAME)
            .schemaVersion(AppSettingsMigration.DB_VERSION)
            .modules(RealmModules.AppSettingsModule())
            .migration(AppSettingsMigration())
            .build()

        private val scope = CoroutineScope(Dispatchers.Default)

        private fun getRealmInstance() = Realm.getInstance(realmConfiguration)

        private fun getAppSettingsQuery(realm: Realm) = realm.where(AppSettings::class.java).findFirst()
        private fun getAppSettingsAsyncQuery(realm: Realm) = realm.where(AppSettings::class.java).findFirstAsync()

        fun getAppSettings(): AppSettings {
            return getRealmInstance().use { realm ->
                getAppSettingsQuery(realm)?.let {
                    realm.copyFromRealm(it, 0)
                }
            } ?: AppSettings()
        }

        val currentUserIdFlow: Flow<Int?> = flow {
            getRealmInstance().use { realm ->
                val flow = realm.where(AppSettings::class.java)
                    .findFirst()
                    .toFlow()
                    .map { it?._currentUserId?.takeIf { id -> id > 0 } } // Return null if not valid user id
                emitAll(flow)
            }
        }.flowOnNewHandlerThread(name = "Realm-currentUserIdFlow").shareIn(
            scope = scope,
            started = SharingStarted.WhileSubscribed(),
            replay = 1,
        )

        fun updateAppSettings(onUpdate: (appSettings: AppSettings) -> Unit) {
            return getRealmInstance().use { realm ->
                var appSettings = getAppSettingsQuery(realm)

                realm.executeTransaction {
                    if (appSettings == null) {
                        appSettings = it.copyToRealm(AppSettings())
                    }
                    onUpdate(appSettings!!)
                }
            }
        }

        fun resetAppSettings() {
            updateAppSettings { appSettings ->
                val newAppSettings = AppSettings().apply { _appLaunchesCount = appSettings._appLaunchesCount }
                appSettings.update(newAppSettings)
            }
        }

        fun removeAppSettings() {
            getRealmInstance().use { realm ->
                realm.executeTransaction {
                    it.where(AppSettings::class.java).findFirst()?.deleteFromRealm()
                }
            }
        }

        var appLaunches: Int = getAppSettings()._appLaunchesCount
            set(value) {
                field = value
                scope.launch (Dispatchers.IO) {
                    updateAppSettings { appSettings -> appSettings._appLaunchesCount = value }
                }
            }

        var appSecurityLock: Boolean = getAppSettings()._appSecurityEnabled
            set(value) {
                field = value
                scope.launch(Dispatchers.IO) {
                    updateAppSettings { appSettings -> appSettings._appSecurityEnabled = value }
                }
            }

        var onlyWifiSync: Boolean = getAppSettings()._onlyWifiSync
            set(value) {
                field = value
                scope.launch(Dispatchers.IO) {
                    updateAppSettings { appSettings -> appSettings._onlyWifiSync = value }
                }
            }
    }

    class AppSettingsMigration : RealmMigration {

        override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
            var oldVersionTemp = oldVersion

            // DynamicRealm exposes an editable schema
            val schema = realm.schema

            //region Migrate to version 1: Remove migrated
            if (oldVersionTemp == 0L) {
                // Remove some fields in SyncSettings
                schema.get(AppSettings::class.java.simpleName)!!.removeField("_migrated")

                oldVersionTemp++
            }
            //endregion
        }

        companion object {
            const val DB_VERSION = 1L // Must be bumped when the schema changes
        }
    }
}
