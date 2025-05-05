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
package com.infomaniak.drive.data.cache

import androidx.collection.IntIntMap
import androidx.collection.IntList
import androidx.collection.MutableIntIntMap
import androidx.collection.buildIntList
import com.infomaniak.core.DynamicLazyMap
import com.infomaniak.core.flowForKey
import com.infomaniak.core.maxElements
import com.infomaniak.core.sharedFlow
import com.infomaniak.drive.data.models.DriveUser
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.data.models.Team
import com.infomaniak.drive.data.models.drive.Category
import com.infomaniak.drive.data.models.drive.CategoryRights
import com.infomaniak.drive.data.models.drive.Drive
import com.infomaniak.drive.data.models.drive.DriveInfo
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.RealmModules
import com.infomaniak.drive.utils.runOnMainThread
import io.realm.Realm
import io.realm.RealmConfiguration
import io.realm.RealmQuery
import io.realm.Sort
import io.realm.kotlin.oneOf
import io.realm.kotlin.toFlow
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.job

object DriveInfosController {

    private const val DB_NAME = "DrivesInfos.realm"

    val baseDriveInfosRealmConfigurationBuilder: RealmConfiguration.Builder = RealmConfiguration.Builder()
        .name(DB_NAME)
        .schemaVersion(DriveMigration.DB_VERSION) // Must be bumped when the schema changes
        .modules(RealmModules.DriveFilesModule())

    private val realmConfiguration = baseDriveInfosRealmConfigurationBuilder
        .migration(DriveMigration())
        .build()

    fun getRealmInstance(): Realm = HandleSchemaVersionBelowZero.getRealmInstance(realmConfiguration)

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

    private fun Realm.getDrivesQuery(
        userId: Int?,
        driveId: Int?,
        sharedWithMe: Boolean? = null,
        maintenance: Boolean? = null
    ): RealmQuery<Drive> {
        return where(Drive::class.java)
            .sort(Drive::name.name, Sort.ASCENDING)
            .sort(Drive::sharedWithMe.name, Sort.ASCENDING)
            .apply {
                userId?.let { equalTo(Drive::userId.name, it) }
                driveId?.let { equalTo(Drive::id.name, it) }
                sharedWithMe?.let { equalTo(Drive::sharedWithMe.name, it) }
                maintenance?.let { equalTo(Drive::maintenance.name, it) }
            }
    }

    fun storeDriveInfos(userId: Int, driveInfo: DriveInfo): List<Drive> {
        val driveList = arrayListOf<Drive>()
        for (drive in driveInfo.drives.filter { drive -> drive.role != DriveUser.Role.NONE }) {
            driveList.initDriveForRealm(drive, userId, sharedWithMe = drive.role == DriveUser.Role.EXTERNAL)
        }

        val driveRemoved = getDrives(userId, sharedWithMe = null).filterNot { driveList.contains(it) }
        val driveRemovedId = driveRemoved.map(Drive::objectId)

        getRealmInstance().use {
            it.executeTransaction { realm ->
                realm.where(Drive::class.java)
                    .oneOf(Drive::objectId.name, driveRemovedId.toTypedArray())
                    .findAll()
                    .deleteAllFromRealm()

                realm.insertOrUpdate(driveList)
                realm.delete(DriveUser::class.java)
                realm.insertOrUpdate(driveInfo.users)
                realm.delete(Team::class.java)
                realm.insertOrUpdate(driveInfo.teams)
            }
        }

        return driveRemoved
    }

    fun updateDrive(transaction: (drive: Drive) -> Unit) {
        getRealmInstance().use { realm ->
            realm.getCurrentDrive()?.let { drive -> realm.executeTransaction { if (drive.isValid) transaction(drive) } }
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
        userId: Int? = null,
        driveId: Int? = null,
        sharedWithMe: Boolean? = false,
        maintenance: Boolean? = null
    ): ArrayList<Drive> {
        return getRealmInstance().use { realm ->
            val driveList = realm.copyFromRealm(realm.getDrivesQuery(userId, driveId, sharedWithMe, maintenance).findAll(), 1)
            driveList?.let { ArrayList(it) } ?: ArrayList()
        }
    }

    fun getDrive(
        userId: Int? = null,
        driveId: Int? = null,
        sharedWithMe: Boolean? = null,
        maintenance: Boolean? = null,
        customRealm: Realm? = null,
    ): Drive? {
        val block: (Realm) -> Drive? = { realm ->
            realm.getDrivesQuery(userId, driveId, sharedWithMe, maintenance).findFirst()?.let {
                if (customRealm == null) realm.copyFromRealm(it, 2) else it
            }
        }
        return customRealm?.let(block) ?: getRealmInstance().use(block)
    }

    fun getDrivesCount(
        userId: Int,
        driveId: Int? = null,
        sharedWithMe: Boolean? = false,
        maintenance: Boolean? = null
    ) = getRealmInstance().use { it.getDrivesQuery(userId, driveId, sharedWithMe, maintenance).count() }

    fun hasSingleDrive(userId: Int): Boolean = getDrivesCount(userId) == 1L

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

    fun getDriveCategories(driveId: Int): List<Category> {
        val categories = getRealmInstance().use { realm ->
            realm.getDrivesQuery(AccountUtils.currentUserId, driveId).findFirst()?.let { drive ->
                drive.categories.let {
                    val categories = it.where()
                        .sort(Category::userUsageCount.name, Sort.DESCENDING)
                        .findAll()
                    realm.copyFromRealm(categories, 0)
                }
            }
        } ?: emptyList()

        return categories
    }

    private val realmInstance = DynamicLazyMap<Unit, Realm>(
        createElement = {
            runOnMainThread { getRealmInstance() }.also { realm ->
                coroutineContext.job.invokeOnCompletion { runOnMainThread { realm.close() } }
            }
        }
    )

    private val driveCategories: DynamicLazyMap<Int, SharedFlow<List<Category>?>> = DynamicLazyMap.sharedFlow(
        cacheManager = DynamicLazyMap.CacheManager.maxElements(maxCacheSize = 20),
        coroutineScope = MainScope(),
        createFlow = { driveId: Int ->
            realmInstance.useElement(Unit) {
                it.getDrivesQuery(
                    userId = AccountUtils.currentUserId,
                    driveId = driveId
                ).findFirst().toFlow().map {
                    if (it?.categoryRights?.canReadOnFile == true) it.categories else null
                }
            }
        }
    )

    data class CategoriesRequest(
        val driveId: Int,
        val categoriesIds: IntList,
    )

    fun categoriesFor(file: File): Flow<List<Category>?> = categoriesFor(
        driveId = file.driveId,
        categoriesIds = buildIntList(initialCapacity = file.categories.size) {
            file.categories.forEach { add(it.categoryId) }
        }
    )

    fun categoriesFor(driveId: Int, categoriesIds: IntList): Flow<List<Category>?> {
        return categories.flowForKey(CategoriesRequest(driveId, categoriesIds))
    }

    private val categories: DynamicLazyMap<CategoriesRequest, SharedFlow<List<Category>?>> = DynamicLazyMap.sharedFlow(
        cacheManager = DynamicLazyMap.CacheManager.maxElements(maxCacheSize = 1000),
        coroutineScope = MainScope(),
        createFlow = { request ->
            val idsToIndexes = request.categoriesIds.asMapOfIndexes()
            driveCategories.flowForKey(request.driveId).map { categories ->
                categories?.toMutableList()?.also {
                    it.removeAll { category -> category.id !in request.categoriesIds }
                    it.sortBy { category -> idsToIndexes[category.id] }
                }
            }
        }
    )

    private fun IntList.asMapOfIndexes(): IntIntMap = MutableIntIntMap(initialCapacity = size).also { map ->
        forEachIndexed { index, number -> map[number] = index }
    }

    fun getCategoriesFromIds(driveId: Int, categoriesIds: Array<Int>): List<Category> {
        if (categoriesIds.isEmpty()) return emptyList()

        val categories = getRealmInstance().use { realm ->
            realm.getDrivesQuery(AccountUtils.currentUserId, driveId).findFirst()?.let { drive ->
                drive.categories.let {
                    val categories = it.where()
                        .oneOf(Category::id.name, categoriesIds)
                        .findAll()
                    realm.copyFromRealm(categories, 0)
                }
            }
        } ?: emptyList()

        return categoriesIds.withIndex()
            .associate { it.value to it.index }
            .let { orderList -> categories.sortedBy { orderList[it.id] } }
    }

    fun getCategoryRights(driveId: Int = AccountUtils.currentDriveId): CategoryRights {
        return getRealmInstance().use { realm ->
            realm.getDrivesQuery(AccountUtils.currentUserId, driveId).findFirst()?.let { drive ->
                drive.categoryRights.let { realm.copyFromRealm(it, 0) }
            }
        } ?: CategoryRights()
    }

    private fun Realm.getCurrentDrive(): Drive? {
        return getDrivesQuery(AccountUtils.currentUserId, AccountUtils.currentDriveId).findFirst()
    }
}
