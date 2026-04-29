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
package com.infomaniak.drive.data.models.deeplink

import android.content.Intent
import android.net.Uri
import android.os.Parcelable
import com.infomaniak.core.legacy.utils.clearStack
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.deeplink.ExternalFileType.FilePreview
import com.infomaniak.drive.data.models.deeplink.ExternalFileType.FilePreviewInFolder
import com.infomaniak.drive.data.models.deeplink.ExternalFileType.Folder
import com.infomaniak.drive.ui.MainActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import kotlinx.parcelize.Parcelize

@Parcelize
sealed interface DeeplinkType : Parcelable {
    val isHandled: Boolean
        get() = true

    @Parcelize
    sealed interface Unmanaged : DeeplinkType {

        data object NotAccessible : Unmanaged

        @Parcelize
        sealed class BrowserLaunch(val url: String) : Unmanaged {
            class BadFormatting(val uri: Uri) : BrowserLaunch(url = uri.toString())
            class Unknown(val uri: Uri) : BrowserLaunch(url = uri.toString())
        }
    }

    @Parcelize
    sealed interface DeeplinkAction : DeeplinkType {
        var userId: Int?
        val driveId: Int

        suspend fun ensureHasAccess(): Boolean

        data class Collaborate(override var userId: Int? = null, override val driveId: Int, val uuid: String) : DeeplinkAction {
            override val isHandled: Boolean
                get() = false

            override suspend fun ensureHasAccess(): Boolean = true
        }

        data class Drive(override var userId: Int? = null, override val driveId: Int, val roleFolder: RoleFolder) :
            DeeplinkAction {
            override val isHandled: Boolean
                get() = roleFolder.isHandled


            override suspend fun ensureHasAccess(): Boolean = roleFolder.hasAccessTo()

            private suspend fun RoleFolder.hasAccessTo(): Boolean = when (this) {
                is RoleFolder.Favorites -> hasAccessTo(fileId = fileId)
                is RoleFolder.Files -> hasAccessTo(fileId = fileType.fileId)
                is RoleFolder.MyShares -> hasAccessTo(fileId = fileId)
                is RoleFolder.Recents -> hasAccessTo(fileId = fileId)
                is RoleFolder.SharedWithMe -> fileType.hasAccessTo()
                is RoleFolder.Trash -> hasAccessTo(fileId = folderId)
                else -> true
            }

            private suspend fun ExternalFileType?.hasAccessTo(): Boolean = when (this) {
                is FilePreview -> hasAccessTo(fileId, sharedWithMe = true)
                is FilePreviewInFolder -> hasAccessTo(fileId, sharedWithMe = true)
                is Folder -> hasAccessTo(fileId = folderId, sharedWithMe = true)
                null -> true
            }
        }

        data class Office(override var userId: Int? = null, override val driveId: Int, val fileId: Int) : DeeplinkAction {
            override suspend fun ensureHasAccess(): Boolean = hasAccessTo(fileId)
        }


        companion object {
            @Throws(InvalidFormatting::class)
            fun from(actionType: String, action: String): DeeplinkAction = ActionType.from(actionType).build(action)

            private suspend fun DeeplinkAction.hasAccessTo(fileId: Int?, sharedWithMe: Boolean = false): Boolean {
                return fileId?.let { hasAccessTo(fileId, sharedWithMe) } ?: hasDrive(sharedWithMe)
            }

            private suspend fun DeeplinkAction.hasAccessTo(fileId: Int, sharedWithMe: Boolean = false): Boolean {
                val userId = userId
                return if (userId != null) {
                    hasFile(fileId, userId = userId, driveId, sharedWithMe)
                } else {
                    getUserDriveWithFile(fileId, sharedWithMe)?.updateUser(deeplinkAction = this) != null
                }
            }

            private suspend fun hasFile(fileId: Int, userId: Int, driveId: Int, sharedWithMe: Boolean): Boolean {
                val userDrive = UserDrive(userId = userId, driveId = driveId, sharedWithMe = sharedWithMe)
                return FileController.hasFile(fileId = fileId, userDrive = userDrive)
            }

            private fun DeeplinkAction.hasDrive(sharedWithMe: Boolean): Boolean {
                return getDrives(sharedWithMe).firstOrNull()?.updateUser(deeplinkAction = this) != null
            }

            private suspend fun DeeplinkAction.getUserDriveWithFile(fileId: Int, sharedWithMe: Boolean = false): UserDrive? {
                return getDrives(sharedWithMe).firstOrNull { FileController.hasFile(fileId = fileId, userDrive = it) }
            }

            private fun DeeplinkAction.getDrives(sharedWithMe: Boolean): List<UserDrive> {
                return DriveInfosController.getDrives(driveId = driveId)
                    .sortedByDescending { it.userId == AccountUtils.currentUserId }
                    .map { UserDrive(userId = it.userId, driveId = it.id, sharedWithMe = sharedWithMe) }
            }

            private fun UserDrive.updateUser(deeplinkAction: DeeplinkAction) = also {
                deeplinkAction.userId = it.userId
            }
        }
    }

    companion object {
        fun Intent.putIfNeeded(deeplinkType: DeeplinkType?) = deeplinkType?.toArgsBundle()?.let { putExtras(it).clearStack() }

        suspend fun DeeplinkType.ensureHasAccess(): DeeplinkType {
            return forDeeplinkAction { takeIf { ensureHasAccess() } ?: Unmanaged.NotAccessible }
        }

        private inline fun DeeplinkType.forDeeplinkAction(block: DeeplinkAction.() -> DeeplinkType): DeeplinkType =
            (this as? DeeplinkAction)?.block() ?: this

        private fun DeeplinkType.toArgsBundle() = MainActivityArgs(deeplinkType = this).toBundle()
    }
}

