/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2026 Infomaniak Network SA
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
package com.infomaniak.drive.data.models.deeplink


const val ACTION_TYPE = "([a-z]+)"
const val ACTION = "(.*)"
const val DRIVE_ID = "(\\d+)"
const val ROLE_FOLDER = "([a-z-]+)"
const val UUID = "([a-z0-9-]+)"
const val FOLDER_ALL_PROPERTIES = "(.*)"
const val FILE_ID = "(\\d+)"
const val FOLDER_ID = "(\\d+)"
const val FILE_TYPE = "([a-z]+)"
const val END_OF_REGEX = "$"
const val KEY_PREVIEW = "preview"
const val PREVIEW = "$KEY_PREVIEW/$FILE_TYPE/$FILE_ID"
