/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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
package com.infomaniak.drive.ui.menu.settings

import android.content.ContentResolver
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.liveData
import androidx.lifecycle.viewModelScope
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.utils.IsComplete
import com.infomaniak.drive.utils.MediaFoldersProvider
import io.realm.Realm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import splitties.init.appCtx

class SelectMediaFoldersViewModel : ViewModel() {

    private var getMediaFilesJob: Job = Job()
    val elementsToRemove = MutableLiveData<List<Long>>()

    fun getAllMediaFolders(contentResolver: ContentResolver): LiveData<Pair<IsComplete, List<MediaFolder>>> {
        getMediaFilesJob = Job()
        return liveData {
            runInterruptible(Dispatchers.IO + getMediaFilesJob) {
                MediaFolder.getRealmInstance().use { realm ->
                    val cacheMediaFolders = MediaFolder.getAll(realm)
                    viewModelScope.launch(Dispatchers.Main) {
                        if (cacheMediaFolders.isNotEmpty()) emit(false to cacheMediaFolders)
                    }

                    val localMediaFolders = getLocalMediaFolders(realm, contentResolver, getMediaFilesJob, cacheMediaFolders)

                    cacheMediaFolders.removeObsoleteMediaFolders(realm, localMediaFolders.map { it.id })

                    viewModelScope.launch(Dispatchers.Main) {
                        emit(true to localMediaFolders.newMediaFolders(cacheMediaFolders))
                    }
                }
            }
        }
    }

    private fun getLocalMediaFolders(
        realm: Realm,
        contentResolver: ContentResolver,
        getMediaFilesJob: Job,
        cacheMediaFolders: List<MediaFolder>,
    ): List<MediaFolder> {

        fun MediaFolder.exists(): Boolean = appCtx.getExternalFilesDir(path)?.exists() == true

        val localFolders = MediaFoldersProvider.getAllMediaFolders(realm, contentResolver, getMediaFilesJob)

        val previouslySyncedCacheFolders = cacheMediaFolders.filter {
            it.isSynced && it.exists()
        }

        return localFolders + previouslySyncedCacheFolders
    }

    override fun onCleared() {
        getMediaFilesJob.cancel()
        super.onCleared()
    }

    private fun List<MediaFolder>.newMediaFolders(cachedMediaFolders: List<MediaFolder>): List<MediaFolder> {
        return filterNot { mediaFolder ->
            cachedMediaFolders.any { cachedFolder -> cachedFolder.id == mediaFolder.id }
        }
    }

    private fun List<MediaFolder>.removeObsoleteMediaFolders(realm: Realm, upToDateMediasIds: List<Long>) {
        val deletedMediaFolderList = mutableListOf<Long>()

        realm.executeTransaction { currentRealm ->
            forEach { cachedFile ->
                if (!upToDateMediasIds.contains(cachedFile.id)) {
                    MediaFolder.delete(currentRealm, cachedFile.id)
                    deletedMediaFolderList.add(cachedFile.id)
                }
            }
        }

        if (deletedMediaFolderList.isNotEmpty()) elementsToRemove.postValue(deletedMediaFolderList)
    }
}
