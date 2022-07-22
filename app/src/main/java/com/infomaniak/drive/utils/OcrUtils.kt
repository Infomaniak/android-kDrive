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
package com.infomaniak.drive.utils

import android.content.Context
import com.geniusscansdk.scanflow.ScanConfiguration
import com.infomaniak.drive.R
import java.io.File
import java.io.FileOutputStream

object OcrUtils {

    private val listLang = arrayOf(
        Pair("eng", R.raw.eng),
        Pair("fra", R.raw.fra),
        Pair("ita", R.raw.ita),
        Pair("deu", R.raw.deu),
        Pair("spa", R.raw.spa)
    )

    fun Context.getOcrConfiguration(): ScanConfiguration.OcrConfiguration {
        copyOcrDataFiles()
        return ScanConfiguration.OcrConfiguration().apply {
            languages = listLang.map { it.first }
            languagesDirectory = getOCRdataDirectory()
        }
    }

    private fun Context.copyOcrDataFiles() {
        getExternalFilesDir(null)?.listFiles()?.forEach { if (it.isFile) it.delete() }

        val ocrDirectory = getOCRdataDirectory()
        if (ocrDirectory.exists()) return
        ocrDirectory.mkdir()

        listLang.forEach { (lang, res) ->
            resources?.openRawResource(res).use { inputStream ->
                FileOutputStream(File(ocrDirectory, "$lang.traineddata")).use {
                    inputStream?.copyTo(it)
                }
            }
        }
    }

    private fun Context.getOCRdataDirectory() = java.io.File(getExternalFilesDir(null), "ocr_dir")

}
