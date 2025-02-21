/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2022-2024 Infomaniak Network SA
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
import androidx.activity.result.ActivityResultLauncher
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.content.pm.ShortcutManagerCompat
import com.geniusscansdk.core.GeniusScanSDK
import com.geniusscansdk.core.LicenseException
import com.geniusscansdk.scanflow.ScanActivity
import com.geniusscansdk.scanflow.ScanConfiguration
import com.geniusscansdk.scanflow.ScanConfiguration.OcrConfiguration
import com.geniusscansdk.scanflow.ScanConfiguration.OcrOutputFormat
import com.geniusscansdk.scanflow.ScanResult
import com.infomaniak.core.utils.FORMAT_NEW_FILE
import com.infomaniak.core.utils.format
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IGeniusScanUtils
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.Utils.Shortcuts
import com.infomaniak.lib.core.utils.SentryLog
import com.infomaniak.lib.core.utils.SnackbarUtils.showSnackbar
import com.infomaniak.lib.core.utils.Utils
import io.sentry.Sentry
import java.util.Date
import java.util.EnumSet

object GeniusScanUtils : IGeniusScanUtils {

    private const val SCAN_CONFIGURATION_KEY = "SCAN_CONFIGURATION_KEY"
    private const val SCAN_RESULT_KEY = "SCAN_RESULT_KEY"
    private const val ERROR_KEY = "ERROR_KEY"

    private val supportedLanguages by lazy {
        mutableListOf(
            "fr-FR",
            "de-DE",
            "en-US",
        ).apply {
            Utils.getPreferredLocaleList().forEach {
                when (it.language) {
                    "it" -> add("it-IT")
                    "es" -> add("es-ES")
                }
            }
        }
    }

    private fun getOcrConfiguration() = OcrConfiguration().apply {
        outputFormats = EnumSet.of(OcrOutputFormat.TEXT_LAYER_IN_PDF)
        languages = supportedLanguages
    }

    private fun Context.removeOldScanFiles() {
        getExternalFilesDir(null)?.listFiles()?.forEach { if (it.isFile) it.delete() }
    }

    override fun Context.initGeniusScanSdk() = try {
        GeniusScanSDK.setLicenseKey(this, GeniusScanEnv.GENIUS_SCAN_KEY)
        true
    } catch (licenseException: LicenseException) {
        licenseException.printStackTrace()
        SentryLog.e("GeniusScanSDK", "The license is expired or invalid", throwable = licenseException)
        false
    }

    override fun Activity.startScanFlow(resultLauncher: ActivityResultLauncher<Intent>) {
        ShortcutManagerCompat.reportShortcutUsed(this, Shortcuts.SCAN.id)

        removeOldScanFiles()
        val scanConfiguration = ScanConfiguration().apply {
            backgroundColor = ContextCompat.getColor(this@startScanFlow, R.color.previewBackground)
            foregroundColor = ContextCompat.getColor(this@startScanFlow, R.color.white)
            highlightColor = ContextCompat.getColor(this@startScanFlow, R.color.accent)
            ocrConfiguration = getOcrConfiguration()
            defaultCurvatureCorrection = ScanConfiguration.CurvatureCorrectionMode.ENABLED
        }
        scanWithConfiguration(scanConfiguration, resultLauncher)
    }

    override fun Activity.scanResultProcessing(intent: Intent, folder: File?) {
        try {
            val geniusScanFile = intent.getScanResult().multiPageDocument!!
            val newName = "scan_${Date().format(FORMAT_NEW_FILE)}.${geniusScanFile.extension}"
            val scanFile = IOFile(geniusScanFile.parent, newName)
            geniusScanFile.renameTo(scanFile)

            val uri = FileProvider.getUriForFile(this, getString(R.string.FILE_AUTHORITY), scanFile)

            Intent(this@scanResultProcessing, SaveExternalFilesActivity::class.java).apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtras(
                    SaveExternalFilesActivityArgs(
                        userId = AccountUtils.currentUserId,
                        driveId = folder?.driveId ?: AccountUtils.currentDriveId,
                        folderId = folder?.id ?: -1
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

    private fun Context.scanWithConfiguration(
        scanConfiguration: ScanConfiguration,
        resultLauncher: ActivityResultLauncher<Intent>,
    ) {
        Intent(this, ScanActivity::class.java).apply {
            putExtra(SCAN_CONFIGURATION_KEY, scanConfiguration)
            resultLauncher.launch(this)
        }
    }

    private fun Intent.getScanResult(): ScanResult {
        (getSerializableExtra(ERROR_KEY) as? Exception)?.let { throw it }

        return getSerializableExtra(SCAN_RESULT_KEY) as ScanResult
    }
}
