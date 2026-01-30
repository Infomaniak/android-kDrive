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

import com.infomaniak.core.common.flowOnNewHandlerThread
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
    var _appSecurityEnabled: Boolean = false,
    var _currentDriveId: Int = -1,
    var _currentUserId: Int = -1,
    var _onlyWifiSyncOffline: Boolean = false,
) : RealmObject() {

    fun update(appSettings: AppSettings) {
        this._appSecurityEnabled = appSettings._appSecurityEnabled
        this._currentDriveId = appSettings._currentDriveId
        this._currentUserId = appSettings._currentUserId
        this._onlyWifiSyncOffline = appSettings._onlyWifiSyncOffline
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

        fun updateAppSettings(scope: CoroutineScope = Companion.scope, onUpdate: AppSettings.() -> Unit) {
            scope.launch(Dispatchers.IO) {
                getRealmInstance().use { realm ->
                    realm.executeTransaction {
                        onUpdate(getAppSettingsQuery(realm) ?: it.copyToRealm(AppSettings()))
                    }
                }
            }
        }

        fun resetAppSettings() {
            updateAppSettings { update(AppSettings()) }
        }

        var appSecurityLock: Boolean = getAppSettings()._appSecurityEnabled
            set(value) {
                field = value
                updateAppSettings { _appSecurityEnabled = value }
            }

        var onlyWifiSyncOffline: Boolean = getAppSettings()._onlyWifiSyncOffline
            set(value) {
                field = value
                updateAppSettings { _onlyWifiSyncOffline = value }
            }
    }

    class AppSettingsMigration : RealmMigration {

        override fun migrate(realm: DynamicRealm, oldVersion: Long, newVersion: Long) {
            if (oldVersion < newVersion) {
                realm.schema?.get(AppSettings::class.java.simpleName)?.run {
                    if (oldVersion < 1L) {
                        removeField("_migrated")
                    }
                    if (oldVersion < 2L) {
                        removeField("_appLaunchesCount")
                    }
                    if (oldVersion < 3L) {
                        renameField("_onlyWifiSync", "_onlyWifiSyncOffline")
                    }
                }
            }
        }

        companion object {
            const val DB_VERSION = 3L // Must be bumped when the schema changes
        }
    }
}
