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
package com.infomaniak.drive.ui

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.*
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import com.infomaniak.drive.R
import com.infomaniak.lib.core.InfomaniakCore
import com.infomaniak.lib.core.utils.UtilsUi.openUrl
import kotlinx.android.synthetic.main.activity_only_office.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.FileOutputStream
import java.net.URL


class OnlyOfficeActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_only_office)

        val url = intent.getStringExtra(ONLYOFFICE_URL_TAG)!!
        val filename = intent.getStringExtra(ONLYOFFICE_FILENAME_TAG)!!
        val headers = mapOf("Authorization" to "Bearer ${InfomaniakCore.bearerToken}")

        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.apply {
            settings.javaScriptEnabled = true
            loadUrl(url, headers)

            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest): Boolean {
                    popBackIfNeeded(request.url.toString())
                    view?.loadUrl(request.url.toString(), headers)
                    return true
                }

                override fun shouldOverrideUrlLoading(view: WebView?, url: String): Boolean {
                    popBackIfNeeded(url)
                    view?.loadUrl(url, headers)
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
            print("PRINT_PDF_ONLYOFFICE_SERVICE", printDocumentAdapter, null)
        }
    }

    override fun onBackPressed() {
        if (webView.canGoBack()) webView.goBack()
        else finish()
        super.onBackPressed()
    }

    private fun popBackIfNeeded(url: String) {
        val popBackNeeded = !url.contains(Regex("^https.*/app/office/\\d+/\\d+"))
        if (popBackNeeded) finish()
    }

    companion object {
        const val ONLYOFFICE_URL_TAG = "office_url_tag"
        const val ONLYOFFICE_FILENAME_TAG = "office_filename_tag"
    }
}