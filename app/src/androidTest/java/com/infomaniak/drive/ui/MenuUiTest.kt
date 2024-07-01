/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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

import android.widget.EditText
import androidx.test.filters.LargeTest
import androidx.test.uiautomator.UiObjectNotFoundException
import androidx.test.uiautomator.UiSelector
import com.infomaniak.drive.R
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.AccountUtils.removeUser
import com.infomaniak.drive.utils.Env
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

@LargeTest
@Disabled
class MenuUiTest : KDriveUiTest() {

    @BeforeEach
    override fun startApp() {
        super.startApp()
        getDeviceViewById("menuFragment").clickAndWaitForNewWindow()
    }

    @Test
    @DisplayName("Check UI to add a new kdrive user then log him off")
    fun testAddUser() {
        // Remove the other user if he's already in the database
        runBlocking { AccountUtils.getUserById(Env.NEW_USER_ID)?.let { removeUser(context, it) } }
        swipeDownNestedScrollView()
        getDeviceViewById("changeUserIcon").clickAndWaitForNewWindow()
        getDeviceViewById("addUser").clickAndWaitForNewWindow()
        getDeviceViewById("nextButton").clickAndWaitForNewWindow()
        getDeviceViewById("nextButton").clickAndWaitForNewWindow()
        getDeviceViewById("connectButton").clickAndWaitForNewWindow()
        with(device) {

            // Username
            findObject(UiSelector().instance(0).className(EditText::class.java)).setText(Env.NEW_USER_NAME)

            // Password
            findObject(UiSelector().instance(1).className(EditText::class.java)).setText(Env.NEW_USER_PASSWORD)

            // Save button
            try {
                findObject(UiSelector().text("CONNECTION")).clickAndWaitForNewWindow(LONG_TIMEOUT)
            } catch (exception: UiObjectNotFoundException) {
                findObject(UiSelector().text("CONNEXION")).clickAndWaitForNewWindow(LONG_TIMEOUT)
            }

            // Close the bottom sheet displayed for categories information
            closeBottomSheetInfoModalIfDisplayed()
            getDeviceViewById("menuFragment").clickAndWaitForNewWindow(SHORT_TIMEOUT)
            assert(AccountUtils.currentUserId == Env.NEW_USER_ID) { "User Id should be ${Env.NEW_USER_ID} but is ${AccountUtils.currentUserId}" }
            swipeDownNestedScrollView()
            getDeviceViewById("logout").clickAndWaitForNewWindow()
            findObject(UiSelector().text(context.getString(R.string.buttonConfirm))).clickAndWaitForNewWindow()
        }
    }

    private fun swipeDownNestedScrollView() {
        with(device) {
            // Cheat to scroll down because nestedScrollView doesn't want to scroll
            swipe(displayWidth / 4, displayHeight - 20, displayWidth / 4, displayHeight / 4, 5)
        }
    }
}
