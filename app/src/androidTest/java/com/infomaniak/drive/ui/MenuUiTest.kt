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
package com.infomaniak.drive.ui

import android.content.Context
import android.content.Intent
import android.widget.EditText
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.matcher.ViewMatchers
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiSelector
import androidx.test.uiautomator.Until
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.UiTestUtils
import com.infomaniak.drive.utils.UiTestUtils.device
import com.infomaniak.drive.utils.UiTestUtils.getDeviceViewById
import org.hamcrest.CoreMatchers
import org.junit.Before
import org.junit.Test
import java.lang.Thread.sleep

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
        getDeviceViewById("menuFragment")?.clickAndWaitForNewWindow()
    }

    @Test
    fun testAddUser() {
        val finalUserId = 0 // To replace by Infomaniak User Id
        val finalUsername = "user-name" // To replace by Infomaniak User email
        val finalPassword = "password" // To replace by Infomaniak password

        getDeviceViewById("changeUser")?.clickAndWaitForNewWindow()
        getDeviceViewById("addUser")?.clickAndWaitForNewWindow()
        getDeviceViewById("nextButton")?.click()
        getDeviceViewById("nextButton")?.click()
        getDeviceViewById("connectButton")?.clickAndWaitForNewWindow()

        // Username
        device.findObject(UiSelector().instance(0).className(EditText::class.java)).text = finalUsername

        // Password
        device.findObject(UiSelector().text("Mot de passe")).text = finalPassword
        device.findObject(UiSelector().text("CONNEXION")).clickAndWaitForNewWindow()

        sleep(6000)

        assert(AccountUtils.currentUserId == finalUserId)
        getDeviceViewById("menuFragment")?.clickAndWaitForNewWindow()
        // Cheat to scroll to bottom of screen
        device.swipe(
            device.displayWidth * 3 / 4,
            device.displayHeight * 9 / 10,
            device.displayWidth * 3 / 4,
            device.displayHeight * 1 / 10,
            10
        )
        getDeviceViewById("logout")?.clickAndWaitForNewWindow()
        device.findObject(UiSelector().text(UiTestUtils.context.getString(R.string.buttonConfirm))).clickAndWaitForNewWindow()
    }
}