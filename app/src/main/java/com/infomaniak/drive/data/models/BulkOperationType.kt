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
package com.infomaniak.drive.data.models

import android.content.Context
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.NotificationUtils.copyOperationProgressNotification
import com.infomaniak.drive.utils.NotificationUtils.deletionOperationProgressNotification
import com.infomaniak.drive.utils.NotificationUtils.moveOperationProgressNotification

enum class BulkOperationType(@StringRes val title: Int) {
    MOVE(R.string.fileListMoveInProgressSnackbar),
    DELETE(R.string.fileListDeletionInProgressSnackbar),
    COPY(R.string.fileListCopyInProgressSnackbar);

    fun getNotificationBuilder(context: Context): NotificationCompat.Builder {
        return when (this) {
            MOVE -> context.moveOperationProgressNotification()
            DELETE -> context.deletionOperationProgressNotification()
            COPY -> context.copyOperationProgressNotification()
        }
    }
}