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
package com.infomaniak.drive.ui.fileList.preview

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ext.okhttp.OkHttpDataSource
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSourceFactory
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.upstream.FileDataSource
import com.google.android.exoplayer2.util.EventLogger
import com.google.android.exoplayer2.util.Util
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import kotlinx.android.synthetic.main.fragment_preview_others.*
import kotlinx.android.synthetic.main.fragment_preview_video.*
import kotlinx.android.synthetic.main.fragment_preview_video.container
import java.io.File

open class PreviewVideoFragment : PreviewFragment() {

    private var exoPlayer: ExoPlayer? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_preview_video, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return

        bigOpenWithButton.apply {
            isGone = true
            setOnClickListener { (parentFragment as? PreviewSliderFragment)?.openWithClicked() }
        }

        fileIcon.setImageResource(file.getFileType().icon)
        container?.layoutTransition?.setAnimateParentHierarchy(false)
        fileName.text = file.name

        playerView.setOnClickListener {
            if (playerView.isControllerFullyVisible) (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
        }
        errorLayout.setOnClickListener {
            (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
        }
    }

    override fun onResume() {
        super.onResume()
        if (exoPlayer == null) initializePlayer()
    }

    override fun onPause() {
        exoPlayer?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        exoPlayer?.release()
        super.onDestroy()
    }

    private fun initializePlayer() {
        createPlayer()
        addPlayerListeners()
    }

    private fun createPlayer() {
        val context = requireContext()

        val offlineFile = if (file.isOffline) {
            val userId = previewSliderViewModel.userDrive.userId
            file.getOfflineFile(requireContext(), userId)
        } else {
            null
        }
        val offlineIsComplete = offlineFile?.let { file.isOfflineAndIntact(offlineFile) } ?: false

        val trackSelector = getTrackSelector(context)

        exoPlayer = ExoPlayer.Builder(context, getRenderersFactory(context.applicationContext))
            .setMediaSourceFactory(getMediaSourceFactory(context, offlineIsComplete))
            .setTrackSelector(trackSelector)
            .build()

        exoPlayer?.apply {
            addAnalyticsListener(EventLogger(trackSelector))
            setAudioAttributes(AudioAttributes.DEFAULT,  /* handleAudioFocus= */true)
            playWhenReady = false

            playerView.player = this
            playerView.controllerShowTimeoutMs = 1000
            playerView.controllerHideOnTouch = false

            setMediaItem(MediaItem.fromUri(getUri(offlineFile, offlineIsComplete)))

            prepare()
        }
    }

    private fun addPlayerListeners() {
        exoPlayer?.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                super.onIsPlayingChanged(isPlaying)
                if (isPlaying) (parentFragment as? PreviewSliderFragment)?.toggleFullscreen()
            }

            override fun onPlayerError(error: PlaybackException) {
                super.onPlayerError(error)
                error.printStackTrace()
                when (error.message) {
                    "Source error" -> previewDescription?.setText(R.string.previewVideoSourceError)
                    else -> previewDescription?.setText(R.string.previewLoadError)
                }
                bigOpenWithButton?.isVisible = true
                errorLayout?.isVisible = true
                playerView?.isGone = true
                previewDescription?.isVisible = true
            }
        })
    }

    private fun getTrackSelector(context: Context): DefaultTrackSelector {
        return DefaultTrackSelector(context).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
    }

    private fun getRenderersFactory(appContext: Context): RenderersFactory {
        return DefaultRenderersFactory(appContext).setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    }

    private fun getMediaSourceFactory(context: Context, offlineIsComplete: Boolean): MediaSourceFactory {
        val dataSourceFactory = if (offlineIsComplete) getOfflineDataSourceFactory() else getDataSourceFactory(context)
        return DefaultMediaSourceFactory(dataSourceFactory)
    }

    private fun getOfflineDataSourceFactory(): DataSource.Factory {
        return DataSource.Factory { FileDataSource() }
    }

    private fun getDataSourceFactory(context: Context): DataSource.Factory {
        val appContext = context.applicationContext
        val userAgent = Util.getUserAgent(appContext, context.getString(R.string.app_name))
        val okHttpDataSource = OkHttpDataSource.Factory(HttpClient.okHttpClient).apply {
            setUserAgent(userAgent)
            setDefaultRequestProperties(HttpUtils.getHeaders().toMap())
        }
        return DefaultDataSource.Factory(appContext, okHttpDataSource)
    }

    private fun getUri(offlineFile: File?, offlineIsComplete: Boolean): Uri {
        return if (offlineFile != null && offlineIsComplete) {
            offlineFile.toUri()
        } else {
            Uri.parse(ApiRoutes.downloadFile(file))
        }
    }
}
