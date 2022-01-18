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
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.Env
import com.infomaniak.drive.utils.UiTestUtils
import com.infomaniak.drive.utils.UiTestUtils.device
import com.infomaniak.drive.utils.UiTestUtils.getDeviceViewById
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Test

class MenuUiTest {
    @Before
    fun startApp() {
        device.pressHome()

        val launcherPackage: String = device.launcherPackageName
        ViewMatchers.assertThat(launcherPackage, CoreMatchers.notNullValue())
        device.wait(Until.hasObject(By.pkg(launcherPackage).depth(0)), 3000)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val intent = context.packageManager.getLaunchIntentForPackage(UiTestUtils.APP_PACKAGE)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        context.startActivity(intent)
        device.wait(Until.hasObject(By.pkg(UiTestUtils.APP_PACKAGE).depth(0)), UiTestUtils.LAUNCH_TIMEOUT)
        getDeviceViewById("menuFragment").clickAndWaitForNewWindow()
    }

    @Test
    fun testAddUser() {
        getDeviceViewById("changeUserIcon").clickAndWaitForNewWindow()
        getDeviceViewById("addUser").clickAndWaitForNewWindow()
        getDeviceViewById("nextButton").click()
        getDeviceViewById("nextButton").click()
        getDeviceViewById("connectButton").clickAndWaitForNewWindow()

        with(device) {
            // Username
            findObject(UiSelector().instance(0).className(EditText::class.java)).text = Env.NEW_USER_NAME

            // Password
            findObject(UiSelector().instance(1).className(EditText::class.java)).text = Env.NEW_USER_PASSWORD

            // Save button
            try {
                findObject(UiSelector().text("CONNECTION")).clickAndWaitForNewWindow(6000)
            } catch (exception: UiObjectNotFoundException) {
                findObject(UiSelector().text("CONNEXION")).clickAndWaitForNewWindow(6000)
            }

            assert(AccountUtils.currentUserId == Env.NEW_USER_ID) { "User Id should be ${Env.NEW_USER_ID} but is ${AccountUtils.currentUserId}" }
            getDeviceViewById("menuFragment").clickAndWaitForNewWindow(2000)

            // Cheat to scroll down because nestedScrollView doesn't want to scroll
            swipe(displayWidth / 4, displayHeight - 20, displayWidth / 4, displayHeight / 4, 5)
            getDeviceViewById("logout").clickAndWaitForNewWindow()
            findObject(UiSelector().text(UiTestUtils.context.getString(R.string.buttonConfirm))).clickAndWaitForNewWindow()
        }
    }
}