/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2025 Infomaniak Network SA
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

import android.view.View
import androidx.annotation.IdRes
import androidx.annotation.StringRes
import androidx.recyclerview.widget.RecyclerView
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.pressBack
import androidx.test.espresso.NoMatchingViewException
import androidx.test.espresso.PerformException
import androidx.test.espresso.UiController
import androidx.test.espresso.ViewAction
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.doesNotExist
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.contrib.RecyclerViewActions
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.espresso.matcher.ViewMatchers.hasDescendant
import androidx.test.espresso.matcher.ViewMatchers.hasSibling
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withEffectiveVisibility
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withResourceName
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.util.HumanReadables
import androidx.test.espresso.util.TreeIterables
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiCollection
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.UiObject
import androidx.test.uiautomator.UiScrollable
import androidx.test.uiautomator.UiSelector
import com.infomaniak.drive.KDriveTest
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.AccountUtils
import de.mannodermaus.junit5.ActivityScenarioExtension
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.extension.RegisterExtension
import java.util.concurrent.TimeoutException

open class KDriveUiTest : KDriveTest() {

    @JvmField
    @RegisterExtension
    val activityExtension = ActivityScenarioExtension.launch<MainActivity>()

    protected val LONG_TIMEOUT = 6000L
    protected val SHORT_TIMEOUT = 2000L

    var device: UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    fun getViewIdentifier(id: String) = "$APP_PACKAGE:id/$id"

    @BeforeEach
    open fun startApp() {
        // Close the bottomSheetModal displayed because it's the user's first connection
        closeBottomSheetInfoModalIfDisplayed()
    }

    fun createPrivateFolder(folderName: String) {
        getDeviceViewById("mainFab").clickAndWaitForNewWindow()

        UiCollection(UiSelector().resourceId(getViewIdentifier("addFileBottomSheetLayout"))).getChild(
            UiSelector().resourceId(getViewIdentifier("folderCreateText"))
        ).clickAndWaitForNewWindow()

        UiCollection(UiSelector().resourceId(getViewIdentifier("newFolderLayout"))).getChild(
            UiSelector().resourceId(getViewIdentifier("privateFolder"))
        ).clickAndWaitForNewWindow()

        UiCollection(UiSelector().resourceId(getViewIdentifier("createFolderLayout"))).apply {
            getChildByInstance(UiSelector().resourceId(getViewIdentifier("folderNameValueInput")), 0).setText(folderName)
            val permissionsRecyclerView = UiScrollable(UiSelector().resourceId(getViewIdentifier("permissionsRecyclerView")))
            permissionsRecyclerView.getChildByText(
                UiSelector().resourceId(getViewIdentifier("permissionCard")),
                context.getString(R.string.createFolderMeOnly)
            ).run {
                permissionsRecyclerView.scrollIntoView(this)
                click()
            }
            getChild(UiSelector().resourceId(getViewIdentifier("createFolderButton"))).clickAndWaitForNewWindow()
        }
    }

    fun deleteFile(fileName: String) {
        openFileListItemMenu(fileName)
        UiScrollable(UiSelector().resourceId(getViewIdentifier("scrollView"))).apply {
            scrollForward()
            getChild(UiSelector().resourceId(getViewIdentifier("deleteFile"))).click()
        }
        device.findObject(UiSelector().text(context.getString(R.string.buttonMove))).clickAndWaitForNewWindow()
    }

    fun openFileShareDetails(fileName: String) {
        openFileListItemMenu(fileName)
        getDeviceViewById("fileRights").clickAndWaitForNewWindow()
    }

    fun getDeviceViewById(id: String): UiObject = device.findObject(UiSelector().resourceId(getViewIdentifier(id)))

    fun findFileIfInList(fileName: String, mustBeInList: Boolean) {
        if (mustBeInList) {
            // Try to scroll to the file
            onView(withResourceName("fileRecyclerView")).perform(
                RecyclerViewActions.scrollTo<RecyclerView.ViewHolder>(hasDescendant(withText(fileName)))
            )
            onView(isRoot()).perform(waitUntilShown(withText(fileName), SHORT_TIMEOUT))
            // Assert the file is displayed
            onView(withText(fileName)).check(matches(isDisplayed()))
        } else {
            // Assert the file does not exists in view hierarchy
            onView(withText(fileName)).check(doesNotExist())
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
        if (!DriveInfosController.hasSingleDrive(AccountUtils.currentUserId)) {
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
        } catch (loseFocusException: RuntimeException) {
            // if the focus was lost by root
            pressBack()
        }
    }

    /**
     * Checks if a view is visible by the user
     * Search for the first view matching the given [viewId], [stringRes] (or both)
     *
     * @param isVisible True if the view must be displayed to the user
     * @param viewId Id of the targeted view
     * @param stringRes Id of the text inside the targeted view
     */
    fun checkViewVisibility(isVisible: Boolean, @IdRes viewId: Int? = null, @StringRes stringRes: Int? = null) {
        val matchers = allOf(arrayListOf<Matcher<View>>().apply {
            viewId?.let { add(withId(it)) }
            stringRes?.let { add(withText(it)) }
            Assertions.assertFalse(isEmpty())
        })
        if (isVisible) onView(isRoot()).perform(waitUntilShown(matchers, LONG_TIMEOUT))
        onView(matchers.first()).check(
            matches(
                withEffectiveVisibility(if (isVisible) ViewMatchers.Visibility.VISIBLE else ViewMatchers.Visibility.GONE)
            )
        )
    }

    /**
     * ViewAction that waits until the element matching [matcher] is shown to user, or throw if it's not after [timeout] passed.
     * @param matcher The matcher of the view to wait for.
     * @param timeout The timeout of until when to wait for.
     */
    private fun waitUntilShown(matcher: Matcher<View>, timeout: Long): ViewAction {
        return object : ViewAction {
            override fun getConstraints() = isRoot()

            override fun getDescription() = "wait until a specific view is shown during $timeout millis."

            override fun perform(uiController: UiController, view: View?) {
                uiController.loopMainThreadUntilIdle()
                val endTime = System.currentTimeMillis() + timeout
                do {
                    // If a displayed matching view is found in the view hierarchy, return to stop waiting
                    TreeIterables.breadthFirstViewTraversal(view).forEach { if (matcher.matches(it) && it.isShown) return }
                    uiController.loopMainThreadForAtLeast(50)
                } while (System.currentTimeMillis() < endTime)

                // View not found
                throw PerformException.Builder()
                    .withActionDescription(this.description)
                    .withViewDescription(HumanReadables.describe(view))
                    .withCause(TimeoutException())
                    .build()
            }
        }
    }

    private fun Matcher<View?>.first(): Matcher<View?> {
        return object : TypeSafeMatcher<View?>() {
            var isFirst = true

            override fun describeTo(description: Description) {
                this@first.describeTo(description.appendText("at first index"))
            }

            override fun matchesSafely(view: View?): Boolean =
                (this@first.matches(view) && isFirst).also { if (it) isFirst = false }
        }
    }

    private fun openFileListItemMenu(fileName: String) {
        findFileIfInList(fileName, true)
        onView(
            allOf(
                withResourceName("menuButton"),
                isDescendantOfA(allOf(withResourceName("endIconLayout"), hasSibling(hasDescendant(withText(fileName)))))
            )
        ).perform(click())
    }
}
