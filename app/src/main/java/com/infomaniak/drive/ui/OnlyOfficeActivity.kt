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
package com.infomaniak.drive.ui

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebSettingsCompat.FORCE_DARK_OFF
import androidx.webkit.WebSettingsCompat.FORCE_DARK_ON
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewFeature
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ActivityOnlyOfficeBinding
import com.infomaniak.drive.utils.AccountUtils
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import com.infomaniak.lib.core.utils.isNightModeEnabled
import com.infomaniak.lib.core.utils.showToast
import io.sentry.Sentry
import io.sentry.SentryLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.URL

class OnlyOfficeActivity : AppCompatActivity() {

    private val binding by lazy { ActivityOnlyOfficeBinding.inflate(layoutInflater) }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?): Unit = with(binding) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)

        val url = intent.getStringExtra(ONLYOFFICE_URL_TAG)!!
        val filename = intent.getStringExtra(ONLYOFFICE_FILENAME_TAG)!!
        val headers = mapOf("Authorization" to "Bearer ${AccountUtils.currentUser?.apiToken?.accessToken}")

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        setDarkMode()

        webView.apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            loadUrl(url, headers)

            webViewClient = object : WebViewClientCompat() {
                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    popBackIfNeeded(request.url.toString())
                    view.loadUrl(request.url.toString(), headers)
                    return true
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progressBar.progress = newProgress
                    if (newProgress == 100) progressBar.isGone = true
                }
            }

            setDownloadListener { url, _, _, _, _ ->
                if (url.endsWith(".pdf")) sendToPrintPDF(url, filename) else openUrl(url)
            }
        }
    }

    @SuppressLint("RequiresFeature")
    private fun setDarkMode() = with(binding) {
        if (SDK_INT >= 29 &&
            WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)
        ) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, isNightModeEnabled())
        } else {
            if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) &&
                WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)
            ) {
                WebSettingsCompat.setForceDarkStrategy(webView.settings, WebSettingsCompat.DARK_STRATEGY_WEB_THEME_DARKENING_ONLY)
                WebSettingsCompat.setForceDark(
                    webView.settings,
                    if (isNightModeEnabled()) FORCE_DARK_ON else FORCE_DARK_OFF
                )
            }
        }
    }

    /**
     * If the file is not a correct PDF, an exception will be thrown in the main thread by PrintManager
     * OnlyOfficeActivity will be "finish" and there is nothing we can do about it
     * https://stackoverflow.com/questions/60508970/malformed-pdf-print-doesnt-catch-runtimeexception
     */
    private fun sendToPrintPDF(url: String, filename: String) {
        val printDocumentAdapter: PrintDocumentAdapter = object : PrintDocumentAdapter() {

            override fun onWrite(
                pages: Array<PageRange>,
                destination: ParcelFileDescriptor,
                cancellationSignal: CancellationSignal,
                resultCallback: WriteResultCallback
            ) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        BufferedInputStream(URL(url).openStream()).use { input ->
                            FileOutputStream(destination.fileDescriptor).use { output ->
                                input.copyTo(output)
                            }
                        }
                        resultCallback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                    } catch (e: Exception) {
                        resultCallback.onWriteFailed(getString(R.string.anErrorHasOccurred))
                    }
                }
            }

            override fun onLayout(
                oldAttributes: PrintAttributes?,
                newAttributes: PrintAttributes?,
                cancellationSignal: CancellationSignal,
                callback: LayoutResultCallback,
                extras: Bundle?
            ) {
                if (cancellationSignal.isCanceled) {
                    callback.onLayoutCancelled()
                    return
                }
                val printDocumentInfo = PrintDocumentInfo
                    .Builder(filename)
                    .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                    .build()
                callback.onLayoutFinished(printDocumentInfo, true)
            }

        }

        (this.getSystemService(Context.PRINT_SERVICE) as PrintManager).apply {
            try {
                print("PRINT_ONLYOFFICE_PDF_SERVICE", printDocumentAdapter, null)
            } catch (activityNotFoundException: ActivityNotFoundException) {
                showToast(R.string.errorNoSupportingAppFound)
                Sentry.withScope { scope ->
                    scope.level = SentryLevel.WARNING
                    Sentry.captureException(activityNotFoundException)
                }
            }
        }

        onBackPressedDispatcher.addCallback {
            with(binding.webView) { if (canGoBack()) goBack() else finish() }
        }
    }

    private fun popBackIfNeeded(url: String) {
        val popBackNeeded = !url.contains(Regex("^https.*/app/(office/\\d+|share/\\d+/[a-z0-9\\-]+/preview/text)/\\d+"))
        if (popBackNeeded) finish()
    }

    companion object {
        const val ONLYOFFICE_URL_TAG = "office_url_tag"
        const val ONLYOFFICE_FILENAME_TAG = "office_filename_tag"
    }
}
