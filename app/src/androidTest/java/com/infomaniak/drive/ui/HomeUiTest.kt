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

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers.assertThat
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.Until
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.UiTestUtils.APP_PACKAGE
import com.infomaniak.drive.utils.UiTestUtils.LAUNCH_TIMEOUT
import com.infomaniak.drive.utils.UiTestUtils.device
import com.infomaniak.drive.utils.UiTestUtils.getDeviceViewById
import com.infomaniak.drive.utils.UiTestUtils.selectDriveInList
import org.hamcrest.CoreMatchers.notNullValue
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * UI Tests relative to a home (drive switch, drive activities, file search)
 */
class HomeUiTest {

    @BeforeEach
    fun startApp() {
        device.pressHome()

        val launcherPackage: String = device.launcherPackageName
        assertThat(launcherPackage, notNullValue())
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), 3000)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(APP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(APP_PACKAGE).depth(0)), LAUNCH_TIMEOUT)
    }

    @Test
    @DisplayName("Check UI to switch drive from home then user menu")
    fun testSwitchDrive() {
        // Change drive from homeFragment
        getDeviceViewById("homeFragment").clickAndWaitForNewWindow()
        if (DriveInfosController.getDrivesCount(AccountUtils.currentUserId) < 2) {
            // finding switchDriveButton should throw because it only appears if user has at least 2 drives
            Assertions.assertThrows(UiObjectNotFoundException::class.java) {
                getDeviceViewById("switchDriveButton").clickAndWaitForNewWindow()
            }

        }

        getDeviceViewById("switchDriveButton").clickAndWaitForNewWindow()
        selectDriveInList(0)

        val driveId = AccountUtils.currentDriveId

        // Change drive from menuFragment
        getDeviceViewById("menuFragment").clickAndWaitForNewWindow()
        getDeviceViewById("driveIcon").clickAndWaitForNewWindow()
        selectDriveInList(1)
        assert(AccountUtils.currentDriveId != driveId) { "Drive id should be different" }
    }
}