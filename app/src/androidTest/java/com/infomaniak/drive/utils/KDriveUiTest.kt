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

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.runner.permission.PermissionRequester
import androidx.test.uiautomator.*
import com.infomaniak.drive.KDriveTest
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.ui.MainActivity
import de.mannodermaus.junit5.ActivityScenarioExtension
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension

open class KDriveUiTest : KDriveTest() {

    @JvmField
    @RegisterExtension
    val activityExtension = ActivityScenarioExtension.launch<MainActivity>()

    var device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun getViewIdentifier(id: String) = "$APP_PACKAGE:id/$id"

    @BeforeEach
    open fun startApp() {
        // Close the bottomSheetModal displayed because it's the user's first connection
        closeBottomSheetInfoModalIfDisplayed()
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
            fileRecyclerView.scrollForward()
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

    fun switchToDriveInstance(instanceNumero: Int) {
        getDeviceViewById("homeFragment").clickAndWaitForNewWindow()
        if (DriveInfosController.getDrivesCount(AccountUtils.currentUserId) > 1) {
            getDeviceViewById("switchDriveButton").clickAndWaitForNewWindow()
            selectDriveInList(instanceNumero) // Switch to dev test drive
            // Close the bottomSheet modal displayed to have info on categories
            closeBottomSheetInfoModalIfDisplayed()
        }
    }


    fun closeBottomSheetInfoModalIfDisplayed() {
        try {
            onView(withId(R.id.secondaryActionButton)).perform(click())
        } catch (exception: NoMatchingViewException) {
            try {
                onView(withId(R.id.actionButton)).perform(click())
            } catch (noMatchingException: NoMatchingViewException) {
                // Continue if bottomSheet are not displayed
            }
        }
    }

    fun checkViewVisibility(isVisible: Boolean, viewId: Int? = null, stringId: Int? = null) {
        val matchers = Matchers.allOf(arrayListOf<Matcher<View>>().apply {
            viewId?.let { add(withId(it)) }
            stringId?.let { add(ViewMatchers.withText(it)) }
            Assertions.assertFalse(isEmpty())
        })

        onView(first(matchers)).check(ViewAssertions.matches(ViewMatchers.withEffectiveVisibility(if (isVisible) ViewMatchers.Visibility.VISIBLE else ViewMatchers.Visibility.GONE)))
    }

    private fun first(matcher: Matcher<View?>): Matcher<View?> {
        return object : TypeSafeMatcher<View?>() {
            var isFirst = true

            override fun describeTo(description: Description) {
                matcher.describeTo(description.appendText(" at first index"))
            }

            override fun matchesSafely(view: View?): Boolean = (matcher.matches(view) && isFirst).also { if (it) isFirst = false }
        }
    }
}