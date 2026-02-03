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
package com.infomaniak.drive.com.infomaniak.drive.data.models.deeplink

import com.infomaniak.drive.data.models.deeplink.DeeplinkAction
import com.infomaniak.drive.data.models.deeplink.ExternalFileType
import com.infomaniak.drive.data.models.deeplink.FileType
import com.infomaniak.drive.data.models.deeplink.RoleFolder
import com.infomaniak.drive.ui.DeeplinkParser
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DeeplinkParserTest {

    @Test
    fun `parse deeplink Collaborate`() {
        val url = "https://kdrive.infomaniak.com/app/collaborate/140946/a3f48925-8626-479c-b9fb-9453dc286745"
        val expected = DeeplinkAction.Collaborate(140946, "a3f48925-8626-479c-b9fb-9453dc286745")
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Office`() {
        val url = "https://kdrive.infomaniak.com/app/office/140946/4818798"
        val expected = DeeplinkAction.Office(140946, 4818798)
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Favorites`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/favorites"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Favorites(null))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Favorites preview`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/favorites/preview/image/4818859"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Favorites(4818859))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive File`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/files/4818791"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Files(FileType.File(4818791)))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive File preview in folder`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/files/4818791/preview/video/4819045"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Files(FileType.FilePreviewInFolder(4818791, 4819045)))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive MyShare`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/my-shares"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.MyShares(null))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive MyShare preview`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/my-shares/preview/text/4818798"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.MyShares(4818798))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Recents`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/recents"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Recents(null))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Recents preview`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/recents/preview/text/4819050"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Recents(4819050))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }


    @Test
    fun `parse deeplink Drive SharedWithMe`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.SharedWithMe(null))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive SharedWithMe folder`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me/140946/4623400"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.SharedWithMe(ExternalFileType.Folder(140946, 4623400)))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }
    @Test
    fun `parse deeplink Drive SharedWithMe preview in folder`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me/140946/4623400/preview/code/4624401"
        val expected = DeeplinkAction.Drive(
            driveId = 140946,
            roleFolder = RoleFolder.SharedWithMe(ExternalFileType.FilePreviewInFolder(140946, 4623400, 4624401))
        )
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }
    @Test
    fun `parse deeplink Drive SharedWithMe preview `() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/shared-with-me/1840145/preview/pdf/8"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.SharedWithMe(ExternalFileType.FilePreview(1840145, 8)))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }
    @Test
    fun `parse deeplink Drive Trash`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/trash"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Trash(null))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }

    @Test
    fun `parse deeplink Drive Trash folder`() {
        val url = "https://ksuite.infomaniak.com/all/kdrive/app/drive/140946/trash/4819046"
        val expected = DeeplinkAction.Drive(140946, RoleFolder.Trash(4819046))
        val deeplinkType = DeeplinkParser.parse(url)
        assertEquals(expected, deeplinkType)
    }
}
