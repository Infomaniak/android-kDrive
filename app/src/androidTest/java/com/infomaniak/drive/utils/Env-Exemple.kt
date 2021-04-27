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
package com.infomaniak.drive.utils

/**
 * File to be copied and completed, it is also necessary to remove the -Example in the file name, so that the tests can work
 */
object `Env-Exemple` {
    /**
     * Specify here, if you want to use the account of the user already logged in the application
     * true to enable it, otherwise use the [TOKEN] field
     */
    const val USE_CURRENT_USER = false

    /**
     * Set it, if [USE_CURRENT_USER] is false
     */
    const val TOKEN = ""

    /**
     * The drive used for the tests
     */
    const val DRIVE_ID = 140946
}