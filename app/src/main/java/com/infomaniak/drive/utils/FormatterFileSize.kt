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

import android.content.Context
import android.content.res.Resources
import android.text.format.Formatter
import kotlin.math.roundToLong

object FormatterFileSize {

    private const val FLAG_SHORTER = 1 shl 0
    private const val FLAG_CALCULATE_ROUNDED = 1 shl 1
    private const val FLAG_SI_UNITS = 1 shl 2
    private const val FLAG_IEC_UNITS = 1 shl 3

    class BytesResult(val value: String, val units: String, val roundedBytes: Long)

    fun formatShortFileSize(context: Context, sizeBytes: Long, justValue: Boolean = false): String {
        return try {
            val res = formatBytes(
                context.resources, sizeBytes,
                FLAG_IEC_UNITS or FLAG_SHORTER
            )
            if (justValue) res.value else context.getString(
                Resources.getSystem().getIdentifier("fileSizeSuffix", "string", "android"),
                res.value, res.units
            )
        } catch (notFoundException: Resources.NotFoundException) {
            Formatter.formatShortFileSize(context, sizeBytes)
        }
    }

    fun formatBytes(res: Resources, sizeBytes: Long, flags: Int): BytesResult {
        val unit = if (flags and FLAG_IEC_UNITS != 0) 1024 else 1000
        val isNegative = sizeBytes < 0
        var result = if (isNegative) (-sizeBytes).toFloat() else sizeBytes.toFloat()
        var suffix: Int = Resources.getSystem().getIdentifier("byteShort", "string", "android")
        var mult: Long = 1
        if (result > 900) {
            suffix = Resources.getSystem().getIdentifier("kilobyteShort", "string", "android")
            mult = unit.toLong()
            result /= unit
        }
        if (result > 900) {
            suffix = Resources.getSystem().getIdentifier("megabyteShort", "string", "android")
            mult *= unit.toLong()
            result /= unit
        }
        if (result > 900) {
            suffix = Resources.getSystem().getIdentifier("gigabyteShort", "string", "android")
            mult *= unit.toLong()
            result /= unit
        }
        if (result > 900) {
            suffix = Resources.getSystem().getIdentifier("terabyteShort", "string", "android")
            mult *= unit.toLong()
            result /= unit
        }
        if (result > 900) {
            suffix = Resources.getSystem().getIdentifier("petabyteShort", "string", "android")
            mult *= unit.toLong()
            result /= unit
        }
        // Note we calculate the rounded long by ourselves, but still let String.format()
        // compute the rounded value. String.format("%f", 0.1) might not return "0.1" due to
        // floating point errors.
        val roundFactor: Int
        val roundFormat: String
        if (mult == 1L || result >= 100) {
            roundFactor = 1
            roundFormat = "%.0f"
        } else if (result < 1) {
            roundFactor = 100
            roundFormat = "%.2f"
        } else if (result < 10) {
            if (flags and FLAG_SHORTER != 0) {
                roundFactor = 10
                roundFormat = "%.1f"
            } else {
                roundFactor = 100
                roundFormat = "%.2f"
            }
        } else { // 10 <= result < 100
            if (flags and FLAG_SHORTER != 0) {
                roundFactor = 1
                roundFormat = "%.0f"
            } else {
                roundFactor = 100
                roundFormat = "%.2f"
            }
        }
        if (isNegative) {
            result = -result
        }
        val roundedString = String.format(roundFormat, result)

        // Note this might overflow if abs(result) >= Long.MAX_VALUE / 100, but that's like 80PB so
        // it's okay (for now)...
        val roundedBytes =
            if (flags and FLAG_CALCULATE_ROUNDED == 0) 0
            else (result * roundFactor).roundToLong() * mult / roundFactor
        val units = res.getString(suffix)
        return BytesResult(roundedString, units, roundedBytes)
    }
}
