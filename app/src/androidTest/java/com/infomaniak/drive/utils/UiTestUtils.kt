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
package com.infomaniak.drive.utils

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.*
import com.infomaniak.drive.R
import org.hamcrest.CoreMatchers

object UiTestUtils {
    private const val APP_PACKAGE = "com.infomaniak.drive"
    private const val LAUNCH_TIMEOUT = 5000L

    var context: Context = ApplicationProvider.getApplicationContext()
    var device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun getViewIdentifier(id: String) = "$APP_PACKAGE:id/$id"

    fun startApp() {
        device.pressHome()
        val launcherPackage: String = device.launcherPackageName
        ViewMatchers.assertThat(launcherPackage, CoreMatchers.notNullValue())
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), 3000)
        context = ApplicationProvider.getApplicationContext()

        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
        // Close the bottomSheetModal displayed because it's the user's first connection
        closeBottomSheetInfoModalIfDisplayed(false)
    }

    fun createPrivateFolder(folderName: String) {
        getDeviceViewById("mainFab").clickAndWaitForNewWindow()

        UiCollection(UiSelector().resourceId(getViewIdentifier("addFileBottomSheetLayout"))).getChildByText(
            UiSelector().resourceId(getViewIdentifier("folderCreateText")),
            context.getString(R.string.allFolder)
        ).click()

        UiCollection(UiSelector().resourceId(getViewIdentifier("newFolderLayout"))).getChildByText(
            UiSelector().resourceId(getViewIdentifier("privateFolder")), context.getString(R.string.allFolder)
        ).click()

        UiCollection(UiSelector().resourceId(getViewIdentifier("createFolderLayout"))).apply {
            getChildByInstance(UiSelector().resourceId(getViewIdentifier("folderNameValueInput")), 0).text = folderName
            val permissionsRecyclerView = UiScrollable(UiSelector().resourceId(getViewIdentifier("permissionsRecyclerView")))
            permissionsRecyclerView.getChildByText(
                UiSelector().resourceId(getViewIdentifier("permissionCard")),
                context.getString(R.string.createFolderMeOnly)
            ).run {
                permissionsRecyclerView.scrollIntoView(this)
                click()
            }
            getChildByText(
                UiSelector().resourceId(getViewIdentifier("createFolderButton")),
                context.getString(R.string.buttonCreateFolder)
            ).clickAndWaitForNewWindow()
        }
    }

    fun deleteFile(fileRecyclerView: UiScrollable, fileName: String) {
        (fileRecyclerView.getChildByText(UiSelector().resourceId(getViewIdentifier("fileCardView")), fileName)).apply {
            fileRecyclerView.scrollIntoView(this)
            getChild(UiSelector().resourceId(getViewIdentifier("menuButton"))).click()
            UiScrollable(UiSelector().resourceId(getViewIdentifier("scrollView"))).apply {
                scrollForward()
                getChild(UiSelector().resourceId(getViewIdentifier("deleteFile"))).click()
            }
        }
        device.findObject(UiSelector().text(context.getString(R.string.buttonMove))).clickAndWaitForNewWindow()
    }

    fun openFileShareDetails(fileRecyclerView: UiScrollable, fileName: String) {
        (fileRecyclerView.getChildByText(UiSelector().resourceId(getViewIdentifier("fileCardView")), fileName)).apply {
            fileRecyclerView.scrollIntoView(this)
            getChild((UiSelector().resourceId(getViewIdentifier("menuButton")))).click()
        }
        getDeviceViewById("fileRights").clickAndWaitForNewWindow()
    }

    fun getDeviceViewById(id: String): UiObject = device.findObject(UiSelector().resourceId(getViewIdentifier(id)))

    fun findFileInList(fileRecyclerView: UiScrollable, fileName: String): UiObject? {
        return try {
            fileRecyclerView.getChildByText(UiSelector().resourceId(getViewIdentifier("fileCardView")), fileName)
        } catch (exception: UiObjectNotFoundException) {
            null
        }
    }

    fun createPublicShareLink(recyclerView: UiScrollable) {
        recyclerView.getChildByText(
            UiSelector().resourceId(getViewIdentifier("permissionTitle")),
            context.getString(R.string.shareLinkPublicRightTitle)
        ).click()
        device.apply {
            findObject(UiSelector().resourceId(getViewIdentifier("saveButton"))).clickAndWaitForNewWindow()
            findObject(UiSelector().resourceId(getViewIdentifier("shareLinkButton"))).clickAndWaitForNewWindow()
        }
    }

    fun selectDriveInList(instance: Int) {
        UiCollection(UiSelector().resourceId(getViewIdentifier("selectRecyclerView"))).getChildByInstance(
            UiSelector().resourceId(getViewIdentifier("itemSelectText")), instance
        ).clickAndWaitForNewWindow()
    }

    fun closeBottomSheetInfoModalIfDisplayed(isCategoryInformation: Boolean) {
        try {
            var id = "secondaryActionButton"
            if (isCategoryInformation) {
                id = "actionButton"
            }
            device.findObject(UiSelector().resourceId(getViewIdentifier(id))).click()
        } catch (exception: UiObjectNotFoundException) {
        }
    }
}