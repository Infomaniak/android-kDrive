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
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.liveData
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.MediaFolder
import com.infomaniak.drive.utils.MediaFoldersProvider
import com.infomaniak.drive.utils.checkWriteStoragePermission
import com.infomaniak.drive.views.FullScreenBottomSheetDialog
import kotlinx.android.synthetic.main.fragment_bottom_sheet_select_media_folders.*
import kotlinx.coroutines.Dispatchers

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
            // Media folder check change
        }
        mediaFolderList.adapter = mediaFoldersAdapter
        loadFolders()
    }

    fun loadFolders() {
        if (checkWriteStoragePermission()) {
            mediaViewModel.getAllMediaFolders(requireActivity().contentResolver).observe(viewLifecycleOwner) { mediaFolders ->
                //TODO add medias to adapter
                mediaFolders.forEach {
                    Log.i("kiki", "folder_name: ${it.name}")
                }
            }
        }
    }

    class MediaViewModel : ViewModel() {
        fun getAllMediaFolders(contentResolver: ContentResolver) = liveData(Dispatchers.IO) {
            val localMediaFolders = MediaFoldersProvider.getAllMediaFolders(contentResolver)
            val cacheMediaFolders = MediaFolder.getAll()

            cacheMediaFolders.forEach { cache ->
                val exist = localMediaFolders.any { cache.id == it.id }
                if (!exist) {
                    cache.delete()
                }
            }
            emit(localMediaFolders as ArrayList<MediaFolder>)
        }
    }
}