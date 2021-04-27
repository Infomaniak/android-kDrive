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
package com.infomaniak.drive

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.infomaniak.drive.UiTestHelper.device
import com.infomaniak.drive.UiTestHelper.getViewIdentifier
import com.infomaniak.drive.UiTestHelper.startApp
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * UI Tests relative to file list (quick operations, file creation, upload, import, ...)
 */
@RunWith(AndroidJUnit4::class)
class FileListUiTest {

    @Before
    fun init() {
        startApp()
        device.findObject(UiSelector().resourceId(getViewIdentifier("fileListFragment"))).click()
    }

    @Test
    fun testCreateFolder() {
        val fileRecyclerView = UiScrollable(UiSelector().resourceId(getViewIdentifier("fileRecyclerView")))
        val initialFileNumber = fileRecyclerView.childCount
        val randomFolderName = "UI-Test-${UUID.randomUUID()}"

        UiTestHelper.createPrivateFolder(randomFolderName)
        device.waitForWindowUpdate(null, 5000)
        assert(fileRecyclerView.childCount == initialFileNumber + 1)

        UiTestHelper.deleteFile(fileRecyclerView, randomFolderName)
        assert(fileRecyclerView.childCount == initialFileNumber)
    }
}