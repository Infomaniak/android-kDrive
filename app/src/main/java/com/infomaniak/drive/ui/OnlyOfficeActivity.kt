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
import android.os.Bundle
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import com.infomaniak.drive.R
import com.infomaniak.lib.core.InfomaniakCore
import kotlinx.android.synthetic.main.activity_only_office.*

class OnlyOfficeActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_only_office)

        val url = intent.getStringExtra(ONLYOFFICE_URL_TAG)!!
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
                    if (newProgress == 100)
                        progressBar.isGone = true
                }
            }
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
    }
}