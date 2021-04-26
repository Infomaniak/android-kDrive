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

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.*
import com.infomaniak.drive.UiTestHelper.APP_PACKAGE
import com.infomaniak.drive.UiTestHelper.LAUNCH_TIMEOUT
import com.infomaniak.drive.UiTestHelper.getViewIdentifier
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.*

/**
 * UI Tests

TODO(s)
 * testCreateFileByTakePicture()
 * testCreateFileByUpload
 * testCreateOfficeDocument
 * testCreateOfficeTable
 * testCreateOfficePresentation
 * testCreateOfficeText
 * testRemoveFile

INSTRUCTIONS
 * Always remove a file at the end of the test, and pressBack (via automator) if needed
 */


@RunWith(AndroidJUnit4::class)
class FileListUiTest {

    private lateinit var device: UiDevice
    private lateinit var context: Context

    @Before
    fun startApp() {
        device = UiTestHelper.getDevice()
        device.pressHome()

        val launcherPackage: String = device.launcherPackageName
        assertThat(launcherPackage, notNullValue())
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), 3000)
        context = ApplicationProvider.getApplicationContext()

        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)

        val fileListFragmentNav = device.findObject(UiSelector().resourceId(getViewIdentifier("fileListFragment")))
        fileListFragmentNav.click()
    }

    @Test
    fun testOpenMenu() {
        device.findObject(UiSelector().resourceId(getViewIdentifier("fileListFragment"))).click()
        val fileRecyclerView = UiCollection(UiSelector().resourceId("$APP_PACKAGE:id/fileRecyclerView"))
        (fileRecyclerView.getChildByInstance(UiSelector().resourceId(getViewIdentifier("fileCardView")), 0)).apply {
            swipeLeft(3)
            getChild(UiSelector().resourceId(getViewIdentifier("menuButton"))).click()
        }
    }

    @Test
    fun testCreateFolder() {
        val fileRecyclerView = UiScrollable(UiSelector().resourceId(getViewIdentifier("fileRecyclerView")))

        val initialFileNumber = fileRecyclerView.childCount

        // Click on main fab
        device.findObject(UiSelector().resourceId(getViewIdentifier("mainFab"))).clickAndWaitForNewWindow()

        // Click on create folder
        UiCollection(UiSelector().resourceId(getViewIdentifier("addFileBottomSheetLayout"))).getChildByText(
            UiSelector().resourceId(getViewIdentifier("folderCreateText")),
            context.getString(R.string.allFolder)
        ).click()

        // Click on private folder
        UiCollection(UiSelector().resourceId(getViewIdentifier("newFolderLayout"))).getChildByText(
            UiSelector().resourceId(getViewIdentifier("privateFolder")), context.getString(R.string.allFolder)
        ).click()

        // Create folder with UUID name
        val randomFolderName = "UI-Test-${UUID.randomUUID()}"
        UiCollection(UiSelector().resourceId(getViewIdentifier("createFolderLayout"))).apply {
            getChildByInstance(UiSelector().resourceId(getViewIdentifier("folderNameValueInput")), 0).text = randomFolderName
            getChildByText(
                UiSelector().resourceId(getViewIdentifier("createFolderButton")),
                context.getString(R.string.buttonCreateFolder)
            ).clickAndWaitForNewWindow()
        }

        device.pressBack()
        assert(fileRecyclerView.childCount == initialFileNumber + 1)

        // Delete file
        (fileRecyclerView.getChildByText(
            UiSelector().resourceId(getViewIdentifier("fileCardView")), randomFolderName
        )).apply {
            fileRecyclerView.scrollIntoView(this)
            swipeLeft(3)
            getChild((UiSelector().resourceId(getViewIdentifier("deleteButton")))).clickAndWaitForNewWindow()
        }

        // Confirm deletion
        device.findObject(UiSelector().text(context.getString(R.string.buttonMove))).clickAndWaitForNewWindow()

        // Assert we retrieve the initial files number
        assert(fileRecyclerView.childCount == initialFileNumber)
    }
}