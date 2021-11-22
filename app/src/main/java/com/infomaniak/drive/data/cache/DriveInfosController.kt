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
        drive.categories.forEach { it.objectId = "${drive.id}_${it.id}" }
        add(drive)
    }

    fun storeDriveInfos(userId: Int, driveInfo: DriveInfo): List<Drive> {
        val driveList = arrayListOf<Drive>()
        driveInfo.drives.main.forEach { driveList.initDriveForRealm(it, userId, false) }
        driveInfo.drives.sharedWithMe.forEach { driveList.initDriveForRealm(it, userId, true) }

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
            val driveList = realm.copyFromRealm(
                realm.where(Drive::class.java)
                    .sort(Drive::id.name, Sort.ASCENDING)
                    .equalTo(Drive::userId.name, userId)
                    .apply {
                        driveId?.let {
                            equalTo(Drive::id.name, it)
                        }
                        sharedWithMe?.let {
                            equalTo(Drive::sharedWithMe.name, it)
                        }
                        maintenance?.let {
                            equalTo(Drive::maintenance.name, it)
                        }
                    }
                    .findAll(), 1
            )
            driveList?.let { ArrayList(it) } ?: ArrayList()
        }
    }

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

    fun getCategories(fileCategoriesIds: Array<Int>? = null): List<Category> {

        if (fileCategoriesIds != null && fileCategoriesIds.isEmpty()) return emptyList()

        val categories = getRealmInstance().use { realm ->
            val drive = realm.where(Drive::class.java)
                .equalTo(Drive::id.name, AccountUtils.currentDriveId)
                .findFirst()

            drive?.categories?.let {
                val categories = it.where()
                    .let { query ->
                        if (fileCategoriesIds != null) query.`in`(Category::id.name, fileCategoriesIds)
                        query
                    }
                    .findAll()

                realm.copyFromRealm(categories, 0)
            }

        } as? List<Category> ?: emptyList()

        return fileCategoriesIds
            ?.mapNotNull { id -> categories.find { it.id == id } } // Sort the categories
            ?: categories
    }

    fun getCategoryRights(): CategoryRights? {
        return getRealmInstance().use { realm ->
            realm.where(CategoryRights::class.java).findFirst()
                ?.let { realm.copyFromRealm(it, 0) }
        }
    }
}