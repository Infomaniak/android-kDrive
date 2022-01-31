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
package com.infomaniak.drive.ui.fileList.preview

import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.infomaniak.drive.data.models.ConvertedType
import com.infomaniak.drive.data.models.File

class PreviewSliderAdapter(manager: FragmentManager, lifecycle: Lifecycle) : FragmentStateAdapter(manager, lifecycle) {

    private var files = ArrayList<File>()
    private var filePagesIds: ArrayList<Long> = ArrayList()

    override fun getItemCount() = files.size

    override fun createFragment(position: Int): Fragment {
        val file = getFile(position)
        val args = bundleOf(PreviewFragment.FILE_ID_TAG to file.id)
        return when (file.getFileType()) {
            ConvertedType.IMAGE -> PreviewPictureFragment().apply { arguments = args }
            ConvertedType.VIDEO -> PreviewVideoFragment().apply { arguments = args }
            ConvertedType.AUDIO -> PreviewMusicFragment().apply { arguments = args }
            ConvertedType.PDF -> PreviewPDFFragment().apply { arguments = args }
            else -> {
                if (file.isOnlyOfficePreview()) PreviewPDFFragment().apply { arguments = args }
                else PreviewOtherFragment().apply { arguments = args }
            }
        }
    }

    fun setFiles(files: ArrayList<File>) {
        this.files = files.filter { !it.isFolder() } as ArrayList<File>
        this.filePagesIds = files.map { it.hashCode().toLong() } as ArrayList
    }

    fun getPosition(file: File): Int {
        return files.indexOfFirst { it.id == file.id }
    }

    fun getFile(position: Int): File {
        return files[position]
    }

    fun updateFile(fileId: Int, transaction: (file: File) -> Unit) {
        val filePosition = files.indexOfFirst { it.id == fileId }
        if (filePosition >= 0) {
            transaction(files[filePosition])
        }
    }

    fun addFile(file: File) {
        files.add(file)
        filePagesIds.add(file.hashCode().toLong())
        notifyItemInserted(files.lastIndex)
    }

    fun deleteFile(file: File): Boolean {
        files.find { it.id == file.id }?.let {
            files.remove(it)
            filePagesIds.remove(it.hashCode().toLong())
            notifyItemRemoved(files.indexOf(it))
        }
        return files.isEmpty()
    }

    override fun getItemId(position: Int): Long {
        return files[position].hashCode().toLong()
    }

    override fun containsItem(itemId: Long): Boolean {
        return filePagesIds.contains(itemId)
    }
}
