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

import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.Team
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.CategoryRights
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.RealmModules
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmQuery
import io.realm.Sort
import io.realm.kotlin.oneOf

object DriveInfosController {

    private const val DB_NAME = "DrivesInfos.realm"

    private val realmConfiguration = RealmConfiguration.Builder().name(DB_NAME)
        .deleteRealmIfMigrationNeeded()
        .modules(RealmModules.DriveFilesModule())
        .build()

    private fun getRealmInstance() = Realm.getInstance(realmConfiguration)

    private fun ArrayList<Drive>.initDriveForRealm(
        drive: Drive,
        userId: Int,
        sharedWithMe: Boolean
    ) {
        drive.objectId = "${drive.id}_$userId"
        drive.userId = userId
        drive.sharedWithMe = sharedWithMe
        add(drive)
    }

    private fun getDrivesQuery(
        realm: Realm,
        userId: Int,
        driveId: Int?,
        sharedWithMe: Boolean?,
        maintenance: Boolean?
    ): RealmQuery<Drive> {
        return realm.where(Drive::class.java)
            .sort(Drive::id.name, Sort.ASCENDING)
            .equalTo(Drive::userId.name, userId)
            .apply {
                driveId?.let { equalTo(Drive::id.name, it) }
                sharedWithMe?.let { equalTo(Drive::sharedWithMe.name, it) }
                maintenance?.let { equalTo(Drive::maintenance.name, it) }
            }
    }

    fun storeDriveInfos(userId: Int, driveInfo: DriveInfo): List<Drive> {
        val driveList = arrayListOf<Drive>()
        for (drive in driveInfo.drives.main) {
            driveList.initDriveForRealm(drive, userId, false)
        }
        for (drive in driveInfo.drives.sharedWithMe) {
            driveList.initDriveForRealm(drive, userId, true)
        }

        val driveRemoved = getDrives(userId, sharedWithMe = null).filterNot { driveList.contains(it) }
        val driveRemovedID = driveRemoved.map(Drive::objectId)

        getRealmInstance().use {
            it.executeTransaction { realm ->
                realm.where(Drive::class.java)
                    .oneOf(Drive::objectId.name, driveRemovedID.toTypedArray())
                    .findAll()
                    .deleteAllFromRealm()

                realm.insertOrUpdate(driveList)
                realm.delete(DriveUser::class.java)
                realm.insertOrUpdate(driveInfo.users.values.toList())
                realm.delete(Team::class.java)
                realm.insertOrUpdate(driveInfo.teams.toList())
            }
        }

        return driveRemoved
    }

    fun updateDrive(transaction: (drive: Drive) -> Unit) {
        getRealmInstance().use { realm ->
            getCurrentDrive(realm)?.let { drive -> realm.executeTransaction { if (drive.isValid) transaction(drive) } }
        }
    }

    fun getUsers(userIds: ArrayList<Int>? = arrayListOf()): List<DriveUser> {
        return getRealmInstance().use { realm ->
            val userList = realm.copyFromRealm(realm.where(DriveUser::class.java).apply {
                if (!userIds.isNullOrEmpty()) {
                    this.oneOf(Drive::id.name, userIds.toTypedArray())
                }
            }.findAll(), 1)
            userList?.let { ArrayList(it) } ?: listOf()
        }
    }

    fun getDrives(
        userId: Int,
        driveId: Int? = null,
        sharedWithMe: Boolean? = false,
        maintenance: Boolean? = null
    ): ArrayList<Drive> {
        return getRealmInstance().use { realm ->
            val driveList = realm.copyFromRealm(getDrivesQuery(realm, userId, driveId, sharedWithMe, maintenance).findAll(), 1)
            driveList?.let { ArrayList(it) } ?: ArrayList()
        }
    }

    fun getDrivesCount(
        userId: Int,
        driveId: Int? = null,
        sharedWithMe: Boolean? = false,
        maintenance: Boolean? = null
    ) = getRealmInstance().use { getDrivesQuery(it, userId, driveId, sharedWithMe, maintenance).count() }

    fun getTeams(drive: Drive): List<Team> {
        val teamList = getRealmInstance().use { realm ->
            realm.copyFromRealm(
                realm.where(Team::class.java)
                    .sort(Team::id.name, Sort.ASCENDING)
                    .findAll(), 1
            )
        } as ArrayList<Team>? ?: ArrayList()

        return teamList.filter { drive.teams.account.contains(it.id) }
    }

    fun getCurrentDriveCategories(): List<Category> {
        val categories = getRealmInstance().use { realm ->
            getCurrentDrive(realm)?.categories?.let {
                val categories = it.where()
                    .sort(Category::userUsageCount.name, Sort.DESCENDING)
                    .findAll()
                realm.copyFromRealm(categories, 0)
            }
        } ?: emptyList()

        return categories
    }

    fun getCurrentDriveCategoriesFromIds(categoriesIds: Array<Int>): List<Category> {
        if (categoriesIds.isEmpty()) return emptyList()

        val categories = getRealmInstance().use { realm ->
            getCurrentDrive(realm)?.categories?.let {
                val categories = it.where()
                    .oneOf(Category::id.name, categoriesIds)
                    .findAll()
                realm.copyFromRealm(categories, 0)
            }
        } ?: emptyList()

        return categoriesIds.withIndex()
            .associate { it.value to it.index }
            .let { orderList -> categories.sortedBy { orderList[it.id] } }
    }

    fun getCategoryRights(): CategoryRights {
        return getRealmInstance().use { realm ->
            getCurrentDrive(realm)?.categoryRights?.let { realm.copyFromRealm(it, 0) }
        } ?: CategoryRights()
    }

    private fun getCurrentDrive(customRealm: Realm? = null): Drive? {
        val block: (Realm) -> Drive? = { realm ->
            realm.where(Drive::class.java)
                .equalTo(Drive::userId.name, AccountUtils.currentUserId)
                .equalTo(Drive::id.name, AccountUtils.currentDriveId)
                .findFirst()
        }
        return customRealm?.let(block) ?: getRealmInstance().use(block)
    }
}
