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
package com.infomaniak.drive.ui

import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.collection.arrayMapOf
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.fileList.FileAdapter.Companion.setCorners
import com.infomaniak.drive.utils.SyncUtils
import com.infomaniak.drive.utils.Utils
import com.infomaniak.drive.utils.setFileItem
import com.infomaniak.drive.utils.setMargin
import com.infomaniak.lib.core.views.ViewHolder
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.android.synthetic.main.cardview_file_list.view.*
import kotlinx.android.synthetic.main.item_file.view.*
import java.util.*

class SaveExternalUriAdapter(val uris: ArrayList<Uri>) : RecyclerView.Adapter<ViewHolder>() {

    private var fileNames = arrayMapOf<Uri, String>()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.cardview_file_list, parent, false)
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val uri = uris[position]

        with(holder.itemView) {
            try {
                context?.contentResolver?.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val name = initAndGetFileName(uri, cursor)
                        val file = File(
                            id = uri.hashCode(),
                            isFromUploads = true,
                            name = name,
                            path = uri.toString()
                        )

                        fileName.text = name

                        setFileItem(file)
                        initView(position)
                        setOnClickListener { onItemClicked(file, position) }
                    }
                }
            } catch (exception: Exception) {
                fileName.setText(R.string.anErrorHasOccurred)
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.WARNING
                    scope.setExtra("uri", uri.toString())
                    Sentry.captureException(exception)
                }
            }
        }
    }

    override fun getItemCount() = uris.size

    fun getFileName(uri: Uri) = fileNames[uri]

    private fun updateFileName(position: Int, newName: String) {
        fileNames[uris[position]] = newName
        notifyItemChanged(position)
    }

    private fun initAndGetFileName(uri: Uri, cursor: Cursor): String {
        return (fileNames[uri] ?: SyncUtils.getFileName(cursor) ?: throw Exception("Name not found from $uri")).also { name ->
            if (fileNames[uri] == null) fileNames[uri] = name
        }
    }

    private fun View.initView(position: Int) {
        fileSize.isGone = true
        fileDate.isGone = true

        fileCardView.setMargin(left = 0, right = 0)
        fileCardView.setCorners(position, itemCount)

        menuButton.apply {
            isVisible = true
            isEnabled = false
            isClickable = false
            setIconResource(R.drawable.ic_edit)
        }
    }

    private fun View.onItemClicked(file: File, position: Int) {
        Utils.createPromptNameDialog(
            context = context,
            title = R.string.buttonRename,
            fieldName = R.string.hintInputFileName,
            positiveButton = R.string.buttonSave,
            fieldValue = file.name,
            selectedRange = file.getFileName().count()
        ) { dialog, name ->
            updateFileName(position, name)
            dialog.dismiss()
        }
    }
}