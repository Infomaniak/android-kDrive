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

import android.net.Uri
import com.infomaniak.drive.ui.DeeplinkParser
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeeplinkParserTest {

    private fun String.mockUri(): Uri {
        val uri = mockk<Uri>()
        every { uri.path } returns this
        return uri
    }

    @Test
    fun `parse deeplink Collaborate`() {
        val uri = "https://kdrive.infomaniak.com/app/collaborate/140946/a3f48925-8626-479c-b9fb-9453dc286745".mockUri()
        val expected = DeeplinkAction.Collaborate(driveId = 140946, uuid = "a3f48925-8626-479c-b9fb-9453dc286745")
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Office`() {
        val uri = "https://kdrive.infomaniak.com/app/office/140946/4818798".mockUri()
        val expected = DeeplinkAction.Office(driveId = 140946, fileId = 4818798)
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Favorites`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/favorites".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.Favorites(fileId = null))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Favorites preview`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/favorites/preview/image/4818859".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.Favorites(fileId = 4818859))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive File`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/files/4818791".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.Files(FileType.File(fileId = 4818791)))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive File preview in folder`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/files/4818791/preview/video/4819045".mockUri()
        val expected = DeeplinkAction.Drive(
            driveId = 140946,
            roleFolder = RoleFolder.Files(FileType.FilePreviewInFolder(folderId = 4818791, fileId = 4819045))
        )
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive MyShare`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/my-shares".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.MyShares(fileId = null))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive MyShare preview`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/my-shares/preview/text/4818798".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.MyShares(fileId = 4818798))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Recents`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/recents".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.Recents(fileId = null))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Recents preview`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/recents/preview/text/4819050".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.Recents(fileId = 4819050))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }


    @Test
    fun `parse deeplink Drive SharedWithMe`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.SharedWithMe(fileType = null))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive SharedWithMe folder`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me/140946/4623400".mockUri()
        val expected = DeeplinkAction.Drive(
            driveId = 140946,
            roleFolder = RoleFolder.SharedWithMe(fileType = ExternalFileType.Folder(sourceDriveId = 140946, folderId = 4623400))
        )
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }
    @Test
    fun `parse deeplink Drive SharedWithMe preview in folder`() {
        val uri =
            "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me/140946/4623400/preview/code/4624401".mockUri()
        val expected = DeeplinkAction.Drive(
            driveId = 140946,
            roleFolder = RoleFolder.SharedWithMe(
                fileType = ExternalFileType.FilePreviewInFolder(sourceDriveId = 140946, folderId = 4623400, fileId = 4624401)
            )
        )
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }
    @Test
    fun `parse deeplink Drive SharedWithMe preview `() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me/1840145/preview/pdf/8".mockUri()
        val expected = DeeplinkAction.Drive(
            driveId = 140946,
            roleFolder = RoleFolder.SharedWithMe(fileType = ExternalFileType.FilePreview(sourceDriveId = 1840145, fileId = 8))
        )
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }
    @Test
    fun `parse deeplink Drive Trash`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/trash".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.Trash(folderId = null))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Trash folder`() {
        val uri = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/trash/4819046".mockUri()
        val expected = DeeplinkAction.Drive(driveId = 140946, roleFolder = RoleFolder.Trash(folderId = 4819046))
        val deeplinkType = DeeplinkParser.parse(uri)
        assertEquals(expected, deeplinkType)
    }

}
