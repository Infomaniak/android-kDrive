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
package com.infomaniak.drive

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.geniusscansdk.core.GeniusScanSDK
import com.geniusscansdk.core.LicenseException
import com.geniusscansdk.scanflow.ScanConfiguration
import com.geniusscansdk.scanflow.ScanFlow
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.FORMAT_NEW_FILE
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.format
import io.sentry.Sentry
import java.io.File
import java.io.FileOutputStream
import java.util.*

object GeniusScanUtils {

    const val SCAN_REQUEST = ScanFlow.SCAN_REQUEST

    private val supportedLanguages = mapOf(
        "fra" to R.raw.fra,
        "deu" to R.raw.deu,
        "eng" to R.raw.eng,
        "ita" to R.raw.ita,
        "spa" to R.raw.spa
    )

    private fun Context.getOcrConfiguration(): ScanConfiguration.OcrConfiguration {
        copyOcrDataFiles()

        return ScanConfiguration.OcrConfiguration().apply {
            languages = supportedLanguages.keys.toList()
            languagesDirectory = getOCRdataDirectory()
        }
    }

    private fun Context.copyOcrDataFiles() {
        getExternalFilesDir(null)?.listFiles()?.forEach { if (it.isFile) it.delete() }

        val ocrDirectory = getOCRdataDirectory()
        if (ocrDirectory.exists()) return
        ocrDirectory.mkdir()

        supportedLanguages.forEach { (lang, res) ->
            resources?.openRawResource(res).use { inputStream ->
                FileOutputStream(File(ocrDirectory, "$lang.traineddata")).use {
                    inputStream?.copyTo(it)
                }
            }
        }
    }

    private fun Context.getOCRdataDirectory() = File(getExternalFilesDir(null), "ocr_dir")

    private fun Context.removeOldScanFiles() {
        getExternalFilesDir(null)?.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    fun Context.initGeniusScanSdk() = try {
        GeniusScanSDK.init(this, BuildConfig.GENIUS_SCAN_KEY)
        true
    } catch (licenseException: LicenseException) {
        licenseException.printStackTrace()
        Log.e("GeniusScanSDK", "The license is expired or invalid")
        Sentry.captureException(licenseException)
        false
    }

    fun Activity.startScanFlow() {
        removeOldScanFiles()
        val scanConfiguration = ScanConfiguration().apply {
            backgroundColor = ContextCompat.getColor(baseContext, R.color.previewBackground)
            foregroundColor = ContextCompat.getColor(baseContext, R.color.white)
            highlightColor = ContextCompat.getColor(baseContext, R.color.accent)
            ocrConfiguration = getOcrConfiguration()
        }
        ScanFlow.scanWithConfiguration(this, scanConfiguration)
    }

    fun MainActivity.scanResultProcessing(intent: Intent?) {
        try {
            val geniusScanFile = ScanFlow.getScanResultFromActivityResult(intent).multiPageDocument!!
            val newName = "scan_${Date().format(FORMAT_NEW_FILE)}.${geniusScanFile.extension}"
            val scanFile = File(geniusScanFile.parent, newName)
            geniusScanFile.renameTo(scanFile)

            val uri = FileProvider.getUriForFile(this, getString(R.string.FILE_AUTHORITY), scanFile)

            Intent(this, SaveExternalFilesActivity::class.java).apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtras(
                    SaveExternalFilesActivityArgs(
                        userId = AccountUtils.currentUserId,
                        userDriveId = AccountUtils.currentDriveId,
                        folderId = getFileListFragment()?.folderId ?: -1
                    ).toBundle()
                )
                type = "/pdf"
                startActivity(this)
            }
        } catch (exception: Exception) {
            exception.printStackTrace()
            Sentry.captureException(exception)
            showSnackbar(R.string.anErrorHasOccurred)
        }
    }

}
