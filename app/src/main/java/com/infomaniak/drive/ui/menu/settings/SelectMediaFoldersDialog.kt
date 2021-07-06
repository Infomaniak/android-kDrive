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
package com.infomaniak.drive.ui.menu.settings

import android.content.ContentResolver
import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.lifecycle.*
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.MediaFoldersProvider
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import io.realm.Realm
import kotlinx.android.synthetic.main.fragment_bottom_sheet_select_media_folders.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible

class SelectMediaFoldersDialog : FullScreenBottomSheetDialog() {

    private val mediaViewModel: MediaViewModel by viewModels()
    private lateinit var mediaFoldersAdapter: MediaFoldersAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bottom_sheet_select_media_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            dismiss()
        }

        swipeRefreshLayout.isEnabled = false

        mediaFoldersAdapter = MediaFoldersAdapter { mediaFolder, isChecked ->
            lifecycleScope.launch(Dispatchers.IO) {
                mediaFolder.enableSync(isChecked)
            }
        }
        mediaFolderList.adapter = mediaFoldersAdapter

        noMediaFolderLayout.setup(
            title = R.string.noMediaFolderTitle,
            initialListView = mediaFolderList,
        )

        val drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { authorized -> if (authorized) loadFolders() else dismiss() }
        if (drivePermissions.checkWriteStoragePermission()) loadFolders()
    }

    fun loadFolders() {
        swipeRefreshLayout.isRefreshing = true
        mediaViewModel.elementsToRemove.observe(viewLifecycleOwner) { elementsToRemove ->
            mediaFolderList.post {
                mediaFoldersAdapter.removeItemsById(elementsToRemove)
            }
        }
        mediaViewModel.getAllMediaFolders(requireActivity().contentResolver)
            .observe(viewLifecycleOwner) { (isComplete, mediaFolders) ->
                mediaFolderList.post {
                    mediaFoldersAdapter.addAll(mediaFolders)
                    noMediaFolderLayout.toggleVisibility(
                        isVisible = mediaFoldersAdapter.itemCount == 0,
                        noNetwork = false,
                        showRefreshButton = false
                    )
                    if (isComplete) {
                        swipeRefreshLayout.isRefreshing = false
                    }
                }
            }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (requireActivity() as SyncSettingsActivity).onDialogDismissed()
    }

    class MediaViewModel : ViewModel() {

        private var getMediaFilesJob: Job = Job()
        val elementsToRemove = MutableLiveData<ArrayList<Long>>()

        fun getAllMediaFolders(contentResolver: ContentResolver): LiveData<Pair<Boolean, ArrayList<MediaFolder>>> {
            getMediaFilesJob = Job()
            return liveData {
                runInterruptible(Dispatchers.IO + getMediaFilesJob) {
                    MediaFolder.getRealmInstance().use { realm ->
                        val cacheMediaFolders = MediaFolder.getAll(realm)
                        viewModelScope.launch(Dispatchers.Main) {
                            if (cacheMediaFolders.isNotEmpty()) emit(false to cacheMediaFolders)
                        }

                        val localMediaFolders = ArrayList(
                            MediaFoldersProvider.getAllMediaFolders(
                                realm = realm,
                                contentResolver = contentResolver,
                                coroutineScope = getMediaFilesJob
                            )
                        )
                        cacheMediaFolders.removeObsoleteMediaFolders(realm, localMediaFolders)

                        viewModelScope.launch(Dispatchers.Main) {
                            emit(true to localMediaFolders.removeDuplicatedMediaFolders(cacheMediaFolders))
                        }
                    }
                }
            }
        }

        override fun onCleared() {
            getMediaFilesJob.cancel()
            super.onCleared()
        }

        private fun ArrayList<MediaFolder>.removeDuplicatedMediaFolders(cachedMediaFolders: ArrayList<MediaFolder>): ArrayList<MediaFolder> {
            return filterNot { mediaFolder ->
                cachedMediaFolders.any { cache -> cache.id == mediaFolder.id }
            } as ArrayList<MediaFolder>
        }

        private fun ArrayList<MediaFolder>.removeObsoleteMediaFolders(
            realm: Realm,
            upToDateMedias: ArrayList<MediaFolder>
        ) {
            val deletedMediaFolderList = arrayListOf<Long>()
            forEach { cachedFile ->
                val exist = upToDateMedias.any { cachedFile.id == it.id }
                if (!exist) {
                    realm.executeTransaction { realm ->
                        realm.where(MediaFolder::class.java).equalTo(MediaFolder::id.name, cachedFile.id).findFirst()
                            ?.deleteFromRealm()
                    }
                    deletedMediaFolderList.add(cachedFile.id)
                }
            }
            elementsToRemove.postValue(deletedMediaFolderList)
        }
    }
}