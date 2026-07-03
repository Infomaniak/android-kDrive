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
import com.infomaniak.core.common.cancellable
import com.infomaniak.core.legacy.utils.clearStack
import com.infomaniak.core.network.models.exceptions.NetworkException
import com.infomaniak.drive.BuildConfig.DEBUG
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
                is Redirect -> {
                    attemptToRedirectLocally(fileId, withSharedDrives = false)
                        ?: attemptToRedirectLocally(fileId, withSharedDrives = true)
                        ?: attemptToRedirectRemotely(fileId, withSharedDrives = false)
                        ?: attemptToRedirectRemotely(fileId, withSharedDrives = true)
                        ?: this@Drive
                }
                is SharedWithMe -> externalFilePath.hasAccessTo()
                is Trash -> ensureHasAccess(fileId = folderId)
                else -> this@Drive
            }

            private suspend fun DeeplinkExternalFilePath?.hasAccessTo(): DeeplinkType = when (this) {
                is FilePreview -> ensureSharedWithMeAccess(fileId = fileId, sourceDriveId = sourceDriveId)
                is FilePreviewInFolder -> ensureSharedWithMeAccess(fileId = fileId, sourceDriveId = sourceDriveId)
                is Folder -> ensureSharedWithMeAccess(fileId = folderId, sourceDriveId = sourceDriveId)
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
                                || fetchAndSaveRemoteFile(fileId, userId = userId, driveId, sharedWithMe) != null
                    } else {
                        getUserDriveWithFile(fileId, sharedWithMe)?.updateUser(deeplinkAction = this) != null
                                || fetchAndSaveRemoteFileForAnyDrive(fileId, sharedWithMe)
                    }
                } ?: hasDrive(sharedWithMe)
            }

            private suspend fun DeeplinkAction.ensureSharedWithMeAccess(fileId: Int, sourceDriveId: Int): DeeplinkType {
                return takeIf { hasSharedWithMeAccess(fileId, sourceDriveId) } ?: Unmanaged.NotAccessible
            }

            private suspend fun DeeplinkAction.hasSharedWithMeAccess(fileId: Int, sourceDriveId: Int): Boolean {
                return sharedWithMeCandidateUserIds(sourceDriveId).any { candidateUserId ->
                    val userDrive = UserDrive(userId = candidateUserId, driveId = sourceDriveId, sharedWithMe = true)
                    val hasLocalAccess = FileController.hasFile(fileId = fileId, userDrive = userDrive)
                    var hasRemoteAccess = false
                    if (!hasLocalAccess) {
                        hasRemoteAccess = fetchAndSaveRemoteFile(fileId, candidateUserId, sourceDriveId, true) != null
                    }
                    (hasLocalAccess || hasRemoteAccess).also { hasAccess -> if (hasAccess) userId = candidateUserId }
                }
            }

            private fun sharedWithMeCandidateUserIds(sourceDriveId: Int): List<Int> {
                val knownUserIds = DriveInfosController.getDrives(driveId = sourceDriveId, sharedWithMe = null).map { it.userId }
                val allUserIds = AccountUtils.getAllUsersSync().map { it.id }
                return (knownUserIds + allUserIds).distinct().sortedByDescending { it == AccountUtils.currentUserId }
            }

            private suspend fun hasFile(fileId: Int, userId: Int, driveId: Int, sharedWithMe: Boolean): Boolean {
                val userDrive = UserDrive(userId = userId, driveId = driveId, sharedWithMe = sharedWithMe)
                return FileController.hasFile(fileId = fileId, userDrive = userDrive)
            }

            private suspend fun DeeplinkAction.fetchAndSaveRemoteFileForAnyDrive(fileId: Int, sharedWithMe: Boolean): Boolean {
                return getDrives(sharedWithMe).any { userDrive ->
                    val file =
                        fetchAndSaveRemoteFile(fileId, userId = userDrive.userId, driveId = userDrive.driveId, sharedWithMe)
                    if (file != null) userDrive.updateUser(deeplinkAction = this)
                    file != null
                }
            }

            private suspend fun fetchAndSaveRemoteFile(
                fileId: Int,
                userId: Int,
                driveId: Int,
                sharedWithMe: Boolean,
            ): File? = withContext(Dispatchers.IO) {
                runCatching {
                    val userDrive = UserDrive(userId = userId, driveId = driveId, sharedWithMe = sharedWithMe)
                    val okHttpClient = AccountUtils.getHttpClient(userId)
                    val remoteFile = FileController.getRemoteFile(fileId, driveId, okHttpClient) ?: return@runCatching null

                    FileController.saveRemoteFileToDb(remoteFile, userDrive, okHttpClient)
                    remoteFile
                }.cancellable().getOrElse { throwable ->
                    if (throwable is NetworkException) throw throwable
                    null
                }
            }

            private fun DeeplinkAction.hasDrive(sharedWithMe: Boolean): Boolean {
                return getDrives(sharedWithMe).firstOrNull()?.updateUser(deeplinkAction = this) != null
            }

            private suspend fun DeeplinkAction.getUserDriveWithFile(fileId: Int, sharedWithMe: Boolean = false): UserDrive? {
                return getDrives(sharedWithMe).firstOrNull { FileController.hasFile(fileId = fileId, userDrive = it) }
            }

            private fun DeeplinkAction.getDrives(sharedWithMe: Boolean): List<UserDrive> {
                return DriveInfosController.getDrives(driveId = driveId, sharedWithMe = sharedWithMe)
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

            private suspend fun Drive.attemptToRedirectRemotely(fileId: Int, withSharedDrives: Boolean): Drive? {
                if (DEBUG) require(deeplinkFolderRole is Redirect)

                return getDrives(sharedWithMe = withSharedDrives).firstNotNullOfOrNull { userDrive ->
                    val file = fetchAndSaveRemoteFile(
                        fileId = fileId,
                        userId = userDrive.userId,
                        driveId = userDrive.driveId,
                        sharedWithMe = withSharedDrives,
                    ) ?: return@firstNotNullOfOrNull null

                    val folderRole = if (withSharedDrives) {
                        SharedWithMe(DeeplinkExternalFilePath.fromFile(file))
                    } else {
                        Files(DeeplinkFilePath.fromFile(file))
                    }

                    Drive(userId = userDrive.userId, driveId = driveId, deeplinkFolderRole = folderRole)
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
