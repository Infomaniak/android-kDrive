/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.util.UUID

/**
 * UI Tests relative to a file item (sharing, comments, details, activities)
 */
@LargeTest
@Disabled
class FileItemUiTest : KDriveUiTest() {

    @BeforeEach
    override fun startApp() {
        super.startApp()
        getDeviceViewById("fileListFragment").click()
    }

    @Test
    @DisplayName("Check UI to create a folder then create a share link for it")
    fun testCreateFileShareLink() {
        val randomFolderName = "UI-Test-${UUID.randomUUID()}"

        // Create the folder then returns to main view
        createPrivateFolder(randomFolderName)
        // Go to fileList view
        openFileShareDetails(randomFolderName)

        device.apply {
            findObject(UiSelector().resourceId(getViewIdentifier("shareLinkSwitch"))).clickAndWaitForNewWindow()
            createPublicShareLink(UiScrollable(UiSelector().resourceId(getViewIdentifier("permissionsRecyclerView"))))
            pressBack()
            findObject(UiSelector().resourceId(getViewIdentifier("closeButton"))).clickAndWaitForNewWindow()
        }
        deleteFile(randomFolderName)
    }
}
