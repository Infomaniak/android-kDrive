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
package com.infomaniak.drive

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice

object UiTestHelper {
    const val APP_PACKAGE = "com.infomaniak.drive"
    const val LAUNCH_TIMEOUT = 5000L
    const val DEFAULT_DRIVE_NAME = "infomaniak"
    const val DEFAULT_DRIVE_ID = 100338

    fun getDevice(): UiDevice = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
}