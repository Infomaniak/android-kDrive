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
package com.infomaniak.drive.data.models

import android.content.Context
import android.os.Parcelable
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.NotificationUtils.copyOperationProgressNotification
import com.infomaniak.drive.utils.NotificationUtils.moveOperationProgressNotification
import com.infomaniak.drive.utils.NotificationUtils.trashOperationProgressNotification
import kotlinx.android.parcel.Parcelize

@Parcelize
enum class BulkOperationType(@StringRes val title: Int, @PluralsRes val successMessage: Int) : Parcelable {
    TRASH(R.string.fileListDeletionInProgressSnackbar, R.plurals.snackbarMoveTrashConfirmation),
    MOVE(R.string.fileListMoveInProgressSnackbar, R.plurals.fileListMoveFileConfirmationSnackbar),
    COPY(R.string.fileListCopyInProgressSnackbar, R.plurals.fileListDuplicationConfirmationSnackbar),
    COLOR_FOLDER(0, R.plurals.fileListColorFolderConfirmationSnackbar),

    ADD_OFFLINE(0, successMessage = R.plurals.fileListAddOfflineConfirmationSnackbar),
    REMOVE_OFFLINE(0, successMessage = R.plurals.fileListRemoveOfflineConfirmationSnackbar),
    ADD_FAVORITES(0, successMessage = R.plurals.fileListAddFavoritesConfirmationSnackbar),
    REMOVE_FAVORITES(0, successMessage = R.plurals.fileListRemoveFavoritesConfirmationSnackbar),

    RESTORE_TO_ORIGIN(0, successMessage = R.plurals.trashedFileRestoreFileToOriginalPlaceSuccess),
    DELETE_PERMANENTLY(0, successMessage = R.plurals.snackbarDeleteConfirmation);

    fun getNotificationBuilder(context: Context): NotificationCompat.Builder {
        return when (this) {
            TRASH -> context.trashOperationProgressNotification()
            MOVE -> context.moveOperationProgressNotification()
            else -> context.copyOperationProgressNotification()
        }
    }
}
