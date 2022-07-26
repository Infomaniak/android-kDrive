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

import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.filters.LargeTest
import com.infomaniak.drive.R
import com.infomaniak.drive.data.cache.FileController
import com.infomaniak.drive.data.models.File
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@LargeTest
class EspressoHomeUiTest : KDriveUiTest() {

    @BeforeEach
    override fun startApp() {
        super.startApp()
        onView(withId(R.id.homeFragment)).perform(click())
    }

    @DisplayName("Check home fragment's general layout is correctly displayed")
    @Test
    fun homeFragmentIsCorrectlyDisplayed() {
        checkViewVisibility(false, R.id.noNetworkCard)
        checkViewVisibility(false, R.id.notEnoughStorage)
        checkViewVisibility(true, R.id.switchDriveButton)
        checkViewVisibility(true, R.id.searchViewCard)
        checkViewVisibility(true, R.id.tabsHomeGroup)
        checkViewVisibility(true, R.id.homeViewPager)
    }

    @DisplayName("Check the activity tabs is correctly displayed when selected")
    @Test
    fun activitiesTabIsDisplayed() {
        clickOnHomeTabs(R.id.homeActivitiesButton)
        checkViewVisibility(true, R.id.userAvatar)
        checkViewVisibility(true, R.id.cardFilePreview1)
        checkViewVisibility(true, R.id.homeTabsTitle, R.string.homeLastActivities)
    }

    @DisplayName("Check the offline tabs is correctly displayed when selected")
    @Test
    fun offlineTabIsDisplayed() {
        clickOnHomeTabs(R.id.homeOfflineButton)
        val isOfflineFilesEmpty =
            FileController.getOfflineFiles(File.SortType.NAME_AZ, customRealm = uiRealm).isNullOrEmpty()
        checkViewVisibility(isOfflineFilesEmpty, R.id.noFilesLayout)
        checkViewVisibility(isOfflineFilesEmpty, stringRes = R.string.offlineFileNoFile)
        checkViewVisibility(!isOfflineFilesEmpty, R.id.toggleDisplayButton)
    }

    @DisplayName("Check the pictures tabs is correctly displayed when selected")
    @Test
    fun picturesTabIsDisplayed() {
        clickOnHomeTabs(R.id.homeGalleryButton)
        try {
            checkViewVisibility(true, R.id.preview)
        } catch (error: Throwable) {
            checkViewVisibility(true, stringRes = R.string.picturesNoFile)
        }
    }

    private fun clickOnHomeTabs(tabId: Int) {
        checkViewVisibility(true, tabId)
        onView(withId(tabId)).perform(click())
    }
}
