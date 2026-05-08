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
import com.infomaniak.drive.BuildConfig.DEBUG
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.UserDrive
import com.infomaniak.drive.data.models.deeplink.DeeplinkExternalFilePath.FilePreview
import com.infomaniak.drive.data.models.deeplink.DeeplinkExternalFilePath.FilePreviewInFolder
import com.infomaniak.drive.data.models.deeplink.DeeplinkExternalFilePath.Folder
import com.infomaniak.drive.data.models.deeplink.DeeplinkFolderRole.Favorites
import com.infomaniak.drive.data.models.deeplink.DeeplinkFolderRole.Files
import com.infomaniak.drive.data.models.deeplink.DeeplinkFolderRole.MyShares
import com.infomaniak.drive.data.models.deeplink.DeeplinkFolderRole.Recents
import com.infomaniak.drive.data.models.deeplink.DeeplinkFolderRole.Redirect
import com.infomaniak.drive.data.models.deeplink.DeeplinkFolderRole.SharedWithMe
import com.infomaniak.drive.data.models.deeplink.DeeplinkFolderRole.Trash
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

        suspend fun ensureHasAccess(): DeeplinkType

        data class Collaborate(override var userId: Int? = null, override val driveId: Int, val uuid: String) : DeeplinkAction {
            override val isHandled: Boolean
                get() = false

            override suspend fun ensureHasAccess(): Collaborate = this
        }

        data class Drive(
            override var userId: Int? = null,
            override val driveId: Int,
            val deeplinkFolderRole: DeeplinkFolderRole
        ) : DeeplinkAction {
            override val isHandled: Boolean
                get() = deeplinkFolderRole.isHandled

            override suspend fun ensureHasAccess(): DeeplinkType = deeplinkFolderRole.ensureHasAccess()

            private suspend fun DeeplinkFolderRole.ensureHasAccess(): DeeplinkType = when (this) {
                is Favorites -> ensureHasAccess(fileId = fileId)
                is Files -> ensureHasAccess(fileId = filePath.fileId)
                is MyShares -> ensureHasAccess(fileId = fileId)
                is Recents -> ensureHasAccess(fileId = fileId)
                is Redirect -> attemptToRedirectLocally(fileId, false) ?: attemptToRedirectLocally(fileId, true) ?: this@Drive
                is SharedWithMe -> externalFilePath.hasAccessTo()
                is Trash -> ensureHasAccess(fileId = folderId)
                else -> this@Drive
            }

            private suspend fun DeeplinkExternalFilePath?.hasAccessTo(): DeeplinkType = when (this) {
                is FilePreview -> ensureHasAccess(fileId, sharedWithMe = true)
                is FilePreviewInFolder -> ensureHasAccess(fileId, sharedWithMe = true)
                is Folder -> ensureHasAccess(fileId = folderId, sharedWithMe = true)
                null -> this@Drive
            }
        }

        data class Office(override var userId: Int? = null, override val driveId: Int, val fileId: Int) : DeeplinkAction {
            override suspend fun ensureHasAccess(): DeeplinkType = ensureHasAccess(fileId)
        }

        companion object {
            @Throws(InvalidFormatting::class)
            fun from(actionType: String, action: String): DeeplinkAction = ActionType.from(actionType).build(action)

            private suspend fun DeeplinkAction.ensureHasAccess(fileId: Int?, sharedWithMe: Boolean = false): DeeplinkType {
                return takeIf { hasAccessTo(fileId, sharedWithMe) } ?: Unmanaged.NotAccessible
            }

            private suspend fun DeeplinkAction.hasAccessTo(fileId: Int?, sharedWithMe: Boolean = false): Boolean {
                return fileId?.let {
                    val userId = userId
                    if (userId != null) {
                        hasFile(fileId, userId = userId, driveId, sharedWithMe)
                    } else {
                        getUserDriveWithFile(fileId, sharedWithMe)?.updateUser(deeplinkAction = this) != null
                    }
                } ?: hasDrive(sharedWithMe)
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

            private fun Drive.attemptToRedirectLocally(fileId: Int, withSharedDrives: Boolean): Drive? {
                if (DEBUG) require(deeplinkFolderRole is Redirect)

                var newUserId: Int? = null

                val deeplinkTargetFile = getDrives(sharedWithMe = withSharedDrives).firstNotNullOfOrNull { userDrive ->
                    newUserId = userDrive.userId
                    FileController.getFileById(fileId, userDrive = userDrive)
                }

                return deeplinkTargetFile?.let { file ->
                    val folderRole = if (withSharedDrives) {
                        SharedWithMe(DeeplinkExternalFilePath.fromFile(file))
                    } else {
                        Files(DeeplinkFilePath.fromFile(file))
                    }
                    Drive(userId = newUserId, driveId = driveId, deeplinkFolderRole = folderRole)
                }
            }
        }
    }

    companion object {
        fun Intent.putIfNeeded(deeplinkType: DeeplinkType?) = deeplinkType?.toArgsBundle()?.let { putExtras(it).clearStack() }

        suspend fun DeeplinkType.ensureHasAccess(): DeeplinkType {
            return forDeeplinkAction { ensureHasAccess() }
        }

        private inline fun DeeplinkType.forDeeplinkAction(block: DeeplinkAction.() -> DeeplinkType): DeeplinkType =
            (this as? DeeplinkAction)?.block() ?: this

        private fun DeeplinkType.toArgsBundle() = MainActivityArgs(deeplinkType = this).toBundle()
    }
}

