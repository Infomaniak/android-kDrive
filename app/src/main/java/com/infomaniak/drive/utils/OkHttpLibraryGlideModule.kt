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
import com.bumptech.glide.Glide
import com.bumptech.glide.Registry
import com.bumptech.glide.annotation.GlideModule
import com.bumptech.glide.integration.okhttp3.OkHttpStreamFetcher
import com.bumptech.glide.integration.okhttp3.OkHttpUrlLoader
import com.bumptech.glide.load.Options
import com.bumptech.glide.load.model.*
import com.bumptech.glide.module.AppGlideModule
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import okhttp3.Call
import java.io.InputStream

@GlideModule
class OkHttpLibraryGlideModule : AppGlideModule() {

    override fun registerComponents(context: Context, glide: Glide, registry: Registry) {
        val factory = OkHttpUrlLoader.Factory(HttpClient.okHttpClient)
        registry.replace(GlideUrl::class.java, InputStream::class.java, factory)
    }

    //TODO Only auth url
    private class GlideAuthLoader(private val client: Call.Factory) : ModelLoader<GlideAuthUrl, InputStream> {

        override fun buildLoadData(
            model: GlideAuthUrl,
            width: Int,
            height: Int,
            options: Options
        ): ModelLoader.LoadData<InputStream> {
            return ModelLoader.LoadData(model, OkHttpStreamFetcher(client, model))
        }

        override fun handles(model: GlideAuthUrl) = true

        //TODO
        class Factory(private val client: Call.Factory) : ModelLoaderFactory<GlideAuthUrl, InputStream> {

            override fun build(multiFactory: MultiModelLoaderFactory): ModelLoader<GlideAuthUrl, InputStream> {
                return GlideAuthLoader(client)
            }

            override fun teardown() = Unit

        }
    }

    //TODO Temp GlideUrl: you should have a custom glideUrl just for the links with authentication
    class GlideAuthUrl(url: String?) : GlideUrl(
        url,
        LazyHeaders.Builder()
            .apply { HttpUtils.getHeaders().toMap().filter { it.key != "Cache-Control" } }
            .build()
    )
}
