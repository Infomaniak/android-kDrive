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

@Throws(InvalidFormatting::class)
internal fun MatchResult?.parseOptionalString(groupName: String): String? = this?.groups[groupName]?.value

@Throws(InvalidFormatting::class)
internal fun MatchResult.parseString(groupName: String): String = parseOptionalString(groupName) ?: throw InvalidFormatting()

@Throws(InvalidFormatting::class)
internal fun MatchResult?.parseOptionalId(groupName: String): Int? {
    return runCatching { parseOptionalString(groupName)?.toInt() }.getOrElse { throw InvalidFormatting() }
}

@Throws(InvalidFormatting::class)
internal fun MatchResult?.parseId(groupName: String): Int = parseOptionalId(groupName) ?: throw InvalidFormatting()

internal fun <T> MatchResult?.tryMatchFor(groupName: String, block: (MatchResult) -> T): T? {
    return runCatching { hasGroup(groupName)?.let(block) }.getOrNull()
}

private fun MatchResult?.hasGroup(groupName: String): MatchResult? = takeIf { this?.groups[groupName] != null }
