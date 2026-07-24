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
import com.geniusscansdk.scanflow.FlowOutput
import com.geniusscansdk.scanflow.ScanFlowConfiguration
import com.geniusscansdk.scanflow.ScanFlowConfiguration.OcrConfiguration
import com.geniusscansdk.scanflow.ScanFlowConfiguration.OcrOutputFormat
import com.geniusscansdk.scanflow.ScanFlowErrorCode
import com.geniusscansdk.scanflow.ScanFlowResult
import com.infomaniak.core.common.utils.FORMAT_NEW_FILE
import com.infomaniak.core.common.utils.format
import com.infomaniak.core.legacy.utils.Utils
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.core.ui.view.utils.SnackbarUtils.showSnackbar
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.SaveExternalFilesActivity
import com.infomaniak.drive.ui.SaveExternalFilesActivityArgs
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.drive.utils.IGeniusScanUtils
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.drive.utils.Utils.Shortcuts
import io.sentry.Sentry
import java.util.Date
import java.util.EnumSet

object GeniusScanUtils : IGeniusScanUtils<ScanFlowConfiguration, FlowOutput<ScanFlowResult>> {

    /**
     * To keep the OCR process from taking too long, we always include the three languages most commonly used by Infomaniak.
     * Additional languages are only included if their language appears in the device preferred locales.
     */
    private val supportedLanguages by lazy {
        mutableListOf(
            "fr-FR",
            "de-DE",
            "en-US",
        ).apply {
            Utils.getPreferredLocaleList().forEach {
                when (it.language) {
                    "da" -> add("da")
                    "el" -> add("el")
                    "es" -> add("es-ES")
                    "fi" -> add("fi")
                    "it" -> add("it-IT")
                    "nl" -> add("nl")
                    "pl" -> add("pl")
                    "pt" -> add("pt-BR")
                    "sv" -> add("sv")
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

    override fun getScanFlowContract() = ScanActivity.Contract()

    override fun Activity.startScanFlow(resultLauncher: ActivityResultLauncher<ScanFlowConfiguration>) {
        ShortcutManagerCompat.reportShortcutUsed(this, Shortcuts.SCAN.id)

        removeOldScanFiles()
        val scanConfiguration = ScanFlowConfiguration().apply {
            backgroundColor = ContextCompat.getColor(this@startScanFlow, R.color.previewBackground)
            foregroundColor = ContextCompat.getColor(this@startScanFlow, R.color.white)
            highlightColor = ContextCompat.getColor(this@startScanFlow, R.color.accent)
            ocrConfiguration = getOcrConfiguration()
            defaultCurvatureCorrection = ScanFlowConfiguration.CurvatureCorrectionMode.ENABLED
        }
        resultLauncher.launch(scanConfiguration)
    }

    override fun Activity.scanResultProcessing(result: FlowOutput<ScanFlowResult>, folder: File?) {
        try {
            val scanResult = when (result) {
                is FlowOutput.Success -> result.result
                is FlowOutput.Error -> {
                    if (result.error.code != ScanFlowErrorCode.CANCELLATION) {
                        result.error.underlyingError?.let { Sentry.captureException(it) }
                        showSnackbar(R.string.anErrorHasOccurred)
                    }
                    return
                }
            }

            val geniusScanFile = scanResult.multiPageDocument ?: run {
                Sentry.captureMessage("GeniusScan SDK v6 returned null multiPageDocument after successful scan flow")
                showSnackbar(R.string.anErrorHasOccurred)
                return
            }
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
}
