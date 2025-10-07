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
package operations

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders

fun HttpResponse.hasExpectedContentType(): Boolean {
    val acceptedContentType = request.headers[HttpHeaders.Accept]
    val receivedContentType = headers[HttpHeaders.ContentType]

    when (acceptedContentType) {
        receivedContentType, null -> return true
    }

    val expectedContentType = ContentType.parse(acceptedContentType)

    if (expectedContentType == ContentType.Any) return true

    val actualContentType = receivedContentType?.let { ContentType.parse(it) }
    return actualContentType?.match(expectedContentType) ?: false
}
