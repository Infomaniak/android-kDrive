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
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.liveData
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.utils.DrivePermissions
import com.infomaniak.drive.utils.MediaFoldersProvider
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_bottom_sheet_select_media_folders.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SelectMediaFoldersDialog : FullScreenBottomSheetDialog() {

    private lateinit var mediaViewModel: MediaViewModel
    private lateinit var mediaFoldersAdapter: MediaFoldersAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_bottom_sheet_select_media_folders, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        mediaViewModel = ViewModelProvider(this)[MediaViewModel::class.java]
        super.onViewCreated(view, savedInstanceState)

        toolbar.setNavigationOnClickListener {
            dismiss()
        }

        mediaFoldersAdapter = MediaFoldersAdapter { mediaFolder, isChecked ->
            lifecycleScope.launch(Dispatchers.IO) {
                mediaFolder.enableSync(isChecked)
            }
        }
        mediaFolderList.adapter = mediaFoldersAdapter

        val drivePermissions = DrivePermissions()
        drivePermissions.registerPermissions(this) { authorized -> if (authorized) loadFolders() else dismiss() }
        if (drivePermissions.checkWriteStoragePermission()) loadFolders()
    }

    fun loadFolders() {
        mediaFoldersAdapter.apply {
            showLoading()
            mediaViewModel.elementsToRemove.observe(viewLifecycleOwner) { elementsToRemove ->
                mediaFolderList.post {
                    removeItemsById(elementsToRemove)
                }
            }
            mediaViewModel.getAllMediaFolders(requireActivity().contentResolver).observe(viewLifecycleOwner) { mediaFolders ->
                mediaFolderList.post {
                    addAll(mediaFolders)
                }
            }
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        (requireActivity() as SyncSettingsActivity).onDialogDismissed()
    }

    class MediaViewModel : ViewModel() {

        val elementsToRemove = MutableLiveData<ArrayList<Long>>()

        fun getAllMediaFolders(contentResolver: ContentResolver) = liveData(Dispatchers.IO) {
            val cacheMediaFolders = MediaFolder.getAll()
            if (cacheMediaFolders.isNotEmpty()) emit(cacheMediaFolders)

            val localMediaFolders = ArrayList(MediaFoldersProvider.getAllMediaFolders(contentResolver))
            cacheMediaFolders.removeObsoleteMediaFolders(localMediaFolders)

            emit(localMediaFolders.removeDuplicatedMediaFolders(cacheMediaFolders))
        }

        private fun ArrayList<MediaFolder>.removeDuplicatedMediaFolders(cachedMediaFolders: ArrayList<MediaFolder>): ArrayList<MediaFolder> {
            return filterNot { mediaFolder ->
                cachedMediaFolders.any { cache -> cache.id == mediaFolder.id }
            } as ArrayList<MediaFolder>
        }

        private suspend fun ArrayList<MediaFolder>.removeObsoleteMediaFolders(upToDateMedias: ArrayList<MediaFolder>) =
            withContext(Dispatchers.IO) {
                val deletedMediaFolderList = arrayListOf<Long>()
                forEach { cachedFile ->
                    val exist = upToDateMedias.any { cachedFile.id == it.id }
                    if (!exist) {
                        cachedFile.delete()
                        deletedMediaFolderList.add(cachedFile.id)
                    }
                }
                elementsToRemove.postValue(deletedMediaFolderList)
            }
    }
}