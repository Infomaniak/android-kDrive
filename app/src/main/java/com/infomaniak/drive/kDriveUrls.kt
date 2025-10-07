/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import com.infomaniak.core.network.ApiEnvironment

private val host = ApiEnvironment.current.host

val CREATE_ACCOUNT_URL = "https://welcome.$host/signup/ikdrive?app=true"
val CREATE_ACCOUNT_SUCCESS_HOST = "kdrive.$host"
val CREATE_ACCOUNT_CANCEL_HOST = "welcome.$host"
val DRIVE_API_V2 = "https://api.kdrive.$host/2/drive/"
val DRIVE_API_V3 = "https://api.kdrive.$host/3/drive/"
val OFFICE_URL = "https://kdrive.$host/app/office/"
val SHARE_URL_V1 = "https://kdrive.$host/app/"
val SHARE_URL_V2 = "https://kdrive.$host/2/app/"
val SHARE_URL_V3 = "https://kdrive.$host/3/app/"
