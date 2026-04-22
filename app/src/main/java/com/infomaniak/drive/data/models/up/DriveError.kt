/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.models.up

import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import com.infomaniak.drive.R
import com.infomaniak.drive.data.models.up.DriveError.Server.ServerError
import com.infomaniak.drive.data.models.up.DriveError.Server.ServerPlurals
import splitties.init.appCtx
import kotlin.reflect.KClass

sealed interface DriveError {
    val key: String

    interface Plurals {
        fun message(quantity: Int): String
    }

    interface Message {
        val message: String
    }

    sealed class Local(
        override val key: String,
        @field:StringRes private val id: Int = R.string.errorGeneric
    ) : DriveError, Message {
        override val message: String
            get() = appCtx.getString(id)

        object CachingFailed : Local(key = "cachingFailed", id = R.string.errorCache)
        object DownloadFailed : Local(key = "downloadFailed", id = R.string.errorDownload)
        object ErrorDeviceStorage : Local(key = "errorDeviceStorage", id = R.string.errorDeviceStorage)
        object FileNotFound : Local(key = "fileNotFound")
        object LocalError : Local(key = "localError")
        object MoveLocalError : Local(key = "moveLocalError", id = R.string.errorMove)
        object PhotoAssetNoLongerExists : Local(key = "photoAssetNoLongerExists")
        object PhotoLibraryWriteAccessDenied : Local(key = "writeAccessDenied", id = R.string.errorPhotoLibraryAccessLimited)
        object SearchCancelled : Local(key = "searchCancelled")
        object TaskCancelled : Local(key = "taskCancelled")
        object TaskExpirationCancelled : Local(key = "taskExpirationCancelled")
        object TaskRescheduled : Local(key = "taskRescheduled")
        object UnknownError : Local(key = "unknownError")
        object UnknownToken : Local(key = "unknownToken")
        object UploadOverDataRestricted : Local(key = "uploadOverDataRestricted", id = R.string.uploadOverDataRestrictedError)
    }

    sealed interface Network : DriveError, Message {
        override val key: String
            get() = "networkError"
        override val message: String
            get() = appCtx.getString(R.string.errorNetwork)

        object NetworkError : Network
    }

    sealed interface Server : DriveError {
        sealed class ServerPlurals(
            override val key: String,
            @field:PluralsRes private val id: Int
        ) : Server, Plurals {
            override fun message(quantity: Int): String = appCtx.resources.getQuantityString(id, quantity, quantity)
        }

        sealed class ServerError(
            override val key: String,
            @field:StringRes private val id: Int = R.string.errorGeneric
        ) : Server, Message {
            override val message: String
                get() = appCtx.getString(id)
        }

        object CategoryAlreadyExists : ServerError(key = "category_already_exist_error", id = R.string.errorCategoryAlreadyExists)
        object Conflict : ServerError(key = "conflict_error", id = R.string.errorConflict)
        object DestinationAlreadyExists : ServerError(key = "destination_already_exists", id = R.string.errorFileAlreadyExists)
        object DownloadPermission : ServerError(key = "you_must_add_at_least_one_file", id = R.string.errorDownloadPermission)
        object DriveMaintenance : ServerError(key = "drive_is_in_maintenance_error", id = R.string.driveMaintenanceDescription)
        object FileAlreadyExistsError : ServerError(key = "file_already_exists_error", id = R.string.errorFileAlreadyExists)
        object Forbidden : ServerError(key = "forbidden_error", id = R.string.accessDeniedTitle)
        object InvalidCursorError : ServerError(key = "invalid_cursor_error")
        object InvalidUploadTokenError : ServerError(key = "invalid_upload_token_error")
        object LimitExceededError : ServerError(key = "limit_exceeded_error", id = R.string.errorLimitExceeded)
        object LockError : ServerError(key = "lock_error", id = R.string.errorFileLocked)
        object NoDrive : ServerError(key = "no_drive")
        object NotAuthorized : ServerError(key = "not_authorized")
        object ObjectNotFound : ServerError(key = "object_not_found", id = R.string.uploadFolderNotFoundError)
        object ProductBlocked : ServerPlurals(key = "product_blocked", id = R.plurals.driveBlockedDescription)
        object ProductMaintenance : ServerError(key = "product_maintenance", id = R.string.driveMaintenanceDescription)
        object QuotaExceededError : ServerError(key = "quota_exceeded_error", id = R.string.notEnoughStorageDescription1)
        object RefreshToken : ServerError(key = "refreshToken")
        object Generic : ServerError(key = "serverError")
        object ShareLinkAlreadyExists : ServerError(key = "file_share_link_already_exists", id = R.string.errorShareLink)
        object StillUploadingError : ServerError(key = "still_uploading_error", id = R.string.errorStillUploading)
        object UploadDestinationNotFoundError : ServerError(key = "upload_destination_not_found_error")
        object UploadDestinationNotWritableError : ServerError(key = "upload_destination_not_writable_error")
        object UploadError : ServerError(key = "upload_error")
        object UploadFailedError : ServerError(key = "upload_failed_error")
        object UploadNotTerminated : ServerError(key = "upload_not_terminated")
        object UploadNotTerminatedError : ServerError(key = "upload_not_terminated_error")
        object UploadTokenCanceled : ServerError(key = "upload_token_canceled")
        object UploadTokenIsNotValid : ServerError(key = "upload_token_is_not_valid")
    }


    companion object {
        fun find(key: String?): DriveError? = key?.let { allValues[key] }

        private val allValues: Map<String, DriveError> by lazy { buildAllValuesList() }

        private fun buildAllValuesList(): Map<String, DriveError> {
            return Local::class.values() + Network::class.values() + ServerError::class.values() + ServerPlurals::class.values()
        }

        private inline fun <reified T : DriveError> KClass<T>.values(): Map<String, DriveError> =
            sealedSubclasses.map { it.objectInstance as DriveError }.associateBy { it.key }

    }
}
