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
package com.infomaniak.drive.ui.fileList.fileDetails

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isInvisible
import androidx.core.view.isVisible
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.FileActivity
import com.infomaniak.drive.utils.loadAvatar
import com.infomaniak.drive.utils.loadGlide
import com.infomaniak.lib.core.views.LoaderAdapter
import com.infomaniak.lib.core.views.ViewHolder
import kotlinx.android.synthetic.main.item_file_activity.view.*
import java.text.SimpleDateFormat
import java.util.*

class FileActivitiesAdapter(
    val isFolder: Boolean,
) : LoaderAdapter<FileActivity>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_file_activity, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentFileActivity = itemList[position]
        holder.itemView.apply {
            activityAction.setText(currentFileActivity.translation(isFolder))
            activityHour.text = currentFileActivity.getHour()

            currentFileActivity.user?.let { driveUser ->
                activityUserName.text = driveUser.displayName
                activityUserAvatar.loadAvatar(driveUser)
            } ?: run {
                activityUserName.setText(R.string.allUserAnonymous)
                activityUserAvatar.loadGlide(R.drawable.ic_account)
            }

            if (position == 0 || !isSameDay(currentFileActivity.createdAt, itemList[position - 1].createdAt)) {
                activityDate.text = currentFileActivity.getDay(context)
                line1.isInvisible = position == 0
                line2.isVisible = true
                activityDateCardView.isVisible = true
            } else {
                line1.isGone = true
                line2.isGone = true
                activityDateCardView.isGone = true
            }
        }
    }

    @SuppressLint("SimpleDateFormat")
    fun isSameDay(date1: Date, date2: Date): Boolean {
        val simpleDateFormat = SimpleDateFormat("yyyyMMdd")
        return simpleDateFormat.format(date1).equals(simpleDateFormat.format(date2))
    }
}
