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
package com.infomaniak.drive.data.models

data class ShareableItems(
    val emails: ArrayList<FeedbackAccessResource<String, Invitation>> = ArrayList(),
    val teams: ArrayList<FeedbackAccessResource<Int, Team>> = ArrayList(),
    val users: ArrayList<FeedbackAccessResource<Int, DriveUser>> = ArrayList(),
) {

    data class FeedbackAccessResource<IdType, AccessType>(
        /** The email,team or user identifier filled in the parameters */
        var id: IdType,
        /** The invitation send */
        var result: Boolean,
        /**  The invitation send, null if access was not found */
        var access: AccessType?,
        /** Additional message about why email fail to be sent */
        var message: String
    )
}