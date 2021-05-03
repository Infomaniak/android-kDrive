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
package com.infomaniak.drive.ui.fileList.preview

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.VISIBLE
import android.view.ViewGroup
import androidx.core.net.toUri
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.File
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import kotlinx.android.synthetic.main.fragment_preview_others.*
import kotlinx.android.synthetic.main.fragment_preview_video.*
import kotlinx.android.synthetic.main.fragment_preview_video.container


open class PreviewVideoFragment(file: File) : PreviewFragment(file) {

    private lateinit var simpleExoPlayer: SimpleExoPlayer

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview_video, container, false)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)
        fileIcon.setImageResource(previewViewModel.currentFile.getFileType().icon)
        container?.layoutTransition?.setAnimateParentHierarchy(false)
        fileName.text = previewViewModel.currentFile.name
    }

    override fun onStart() {
        super.onStart()

        if (!::simpleExoPlayer.isInitialized) {
            initializePlayer()

            simpleExoPlayer.addListener(object : Player.EventListener {
                override fun onPlayerError(error: ExoPlaybackException) {
                    error.printStackTrace()
                    when (error.message) {
                        "Source error" -> previewDescription.setText(R.string.previewVideoSourceError)
                        else -> previewDescription.setText(R.string.previewLoadError)
                    }
                    playerView.visibility = GONE
                    previewDescription.visibility = VISIBLE
                    errorLayout.visibility = VISIBLE
                }
            })
        }

    }

    override fun onPause() {
        super.onPause()
        simpleExoPlayer.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        simpleExoPlayer.release()
    }

    private fun initializePlayer() {
        val context = requireContext()
        val renderersFactory: RenderersFactory = buildRenderersFactory(context)

        val mediaSourceFactory: MediaSourceFactory =
            if (offlineFile.exists()) DefaultMediaSourceFactory(getOfflineDataSourceFactory())
            else DefaultMediaSourceFactory(getDataSourceFactory(context))

        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
        simpleExoPlayer = SimpleExoPlayer.Builder(context, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .build()

        simpleExoPlayer.apply {
            addAnalyticsListener(EventLogger(trackSelector))
            setAudioAttributes(AudioAttributes.DEFAULT,  /* handleAudioFocus= */true)
            playWhenReady = false

            playerView.player = this
            playerView.setControlDispatcher(DefaultControlDispatcher())

            if (previewViewModel.currentFile.isOffline && !previewViewModel.currentFile.isOldData(requireContext())) {
                setMediaItem(MediaItem.fromUri(offlineFile.toUri()))
            } else {
                setMediaItem(MediaItem.fromUri(Uri.parse(ApiRoutes.downloadFile(previewViewModel.currentFile))))
            }

            prepare()
        }
    }

    private fun buildRenderersFactory(context: Context): RenderersFactory {
        return DefaultRenderersFactory(context.applicationContext)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    }

    @Synchronized
    fun getDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext

        val userAgent = Util.getUserAgent(appContext, context.getString(R.string.app_name))
        val okHttpDataSource = OkHttpDataSource.Factory(HttpClient.okHttpClient).apply {
            setUserAgent(userAgent)
            setDefaultRequestProperties(HttpUtils.getHeaders().toMap())
        }
        return DefaultDataSourceFactory(appContext, okHttpDataSource)
    }

    @Synchronized
    fun getOfflineDataSourceFactory(): DataSource.Factory {
        return DataSource.Factory {
            FileDataSource()
        }
    }
}