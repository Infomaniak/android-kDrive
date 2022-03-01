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
package com.infomaniak.drive.ui

import android.view.View
import androidx.lifecycle.Lifecycle
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.*
import androidx.test.filters.LargeTest
import com.infomaniak.drive.KDriveTest
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import de.mannodermaus.junit5.ActivityScenarioExtension
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.Matchers.allOf
import org.hamcrest.TypeSafeMatcher
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

@LargeTest
class EspressoHomeUiTest : KDriveTest() {

    @RegisterExtension
    val activityExtension = ActivityScenarioExtension.launch<MainActivity>()

    @BeforeEach
    fun beforeEach() {
        activityExtension.scenario.moveToState(Lifecycle.State.RESUMED)
        onView(withId(R.id.homeFragment)).perform(click())
    }

    @Test
    fun homeFragmentIsCorrectlyDisplayed() {
        checkViewVisibility(false, R.id.noNetworkCard)
        checkViewVisibility(true, R.id.switchDriveButton)
        checkViewVisibility(true, R.id.searchViewCard)
        checkViewVisibility(false, R.id.notEnoughStorage)
        checkViewVisibility(true, R.id.tabsHomeGroup)
        checkViewVisibility(true, R.id.homeViewPager)
    }

    @Test
    fun activitiesTabIsDisplayed() {
        clickOnHomeTabs(R.id.homeActivitiesButton)
        checkViewVisibility(true, R.id.userAvatar)
        checkViewVisibility(true, R.id.cardFilePreview1)
        checkViewVisibility(true, R.id.homeTabsTitle, R.string.homeLastActivities)
    }

    @Test
    fun offlineTabIsDisplayed() {
        clickOnHomeTabs(R.id.homeOfflineButton)
        val isOfflineFilesEmpty =
            FileController.getOfflineFiles(File.SortType.NAME_AZ, customRealm = uiRealm).isNullOrEmpty()
        checkViewVisibility(isOfflineFilesEmpty, R.id.noFilesLayout)
        checkViewVisibility(isOfflineFilesEmpty, stringId = R.string.offlineFileNoFile)
        checkViewVisibility(!isOfflineFilesEmpty, R.id.toggleDisplayButton)
    }

    @Test
    fun picturesTabIsDisplayed() {
        clickOnHomeTabs(R.id.homePicturesButton)
        try {
            checkViewVisibility(true, R.id.picture)
        } catch (error: Throwable) {
            checkViewVisibility(true, stringId = R.string.picturesNoFile)
        }
    }

    private fun clickOnHomeTabs(tabId: Int) {
        checkViewVisibility(true, tabId)
        onView(withId(tabId)).perform(click())
    }

    private fun checkViewVisibility(isVisible: Boolean, viewId: Int? = null, stringId: Int? = null) {
        val matchers = allOf(arrayListOf<Matcher<View>>().apply {
            viewId?.let { add(withId(it)) }
            stringId?.let { add(withText(it)) }
            assertFalse(isEmpty())
        })

        onView(first(matchers)).check(matches(withEffectiveVisibility(if (isVisible) Visibility.VISIBLE else Visibility.GONE)))
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