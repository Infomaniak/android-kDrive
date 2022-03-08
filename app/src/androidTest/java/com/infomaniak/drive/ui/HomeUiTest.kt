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

import androidx.test.uiautomator.UiObjectNotFoundException
import com.infomaniak.drive.data.cache.DriveInfosController
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.KDriveUiTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test

/**
 * UI Tests relative to a home (drive switch, drive activities, file search)
 */
class HomeUiTest : KDriveUiTest() {

    @Test
    @DisplayName("Check UI to switch drive from home then user menu")
    fun testSwitchDrive() {
        // Change drive from homeFragment
        getDeviceViewById("homeFragment").clickAndWaitForNewWindow()
        if (DriveInfosController.hasSingleDrive(AccountUtils.currentUserId)) {
            // finding switchDriveButton should throw because it only appears if user has at least 2 drives
            Assertions.assertThrows(UiObjectNotFoundException::class.java) {
                getDeviceViewById("switchDriveButton").clickAndWaitForNewWindow()
            }
        } else {
            switchToDriveInstance(0)

            val driveId = userDrive.driveId

            // Change drive from menuFragment
            getDeviceViewById("menuFragment").clickAndWaitForNewWindow()
            getDeviceViewById("driveIcon").clickAndWaitForNewWindow()
            selectDriveInList(1) // Switch back to dev test drive
            assert(AccountUtils.currentDriveId != driveId) { "Drive id should be different" }
        }
    }
}
