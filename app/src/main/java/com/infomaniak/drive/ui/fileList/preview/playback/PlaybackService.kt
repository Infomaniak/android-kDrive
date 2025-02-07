/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024 Infomaniak Network SA
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
package com.infomaniak.drive.ui.fileList.preview.playback

import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceBitmapLoader
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.RenderersFactory
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.util.EventLogger
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.infomaniak.drive.R
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils

@UnstableApi
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var exoPlayer: ExoPlayer? = null

    private val mediaSessionCallback = object : MediaSession.Callback {

        // When the user returns from the PreviewPlaybackFragment, we want to stop
        // the service because it does not make sense to have the media notification
        // when the user willingly quits the PreviewPlaybackFragment.
        override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
            super.onDisconnected(session, controller)
            stopSelf()
        }
    }

    override fun onCreate() {
        super.onCreate()
        exoPlayer = ExoPlayer.Builder(this, getRenderersFactory())
            .setMediaSourceFactory(DefaultMediaSourceFactory(getDataSourceFactory()))
            .setTrackSelector(getTrackSelector())
            .build().apply {
                addAnalyticsListener(EventLogger())
                setAudioAttributes(AudioAttributes.DEFAULT,  /* handleAudioFocus= */true)
                playWhenReady = false
                mediaSession = MediaSession.Builder(this@PlaybackService, this)
                    .setCallback(mediaSessionCallback)
                    .setBitmapLoader(getBitmapLoader())
                    .build()
                prepare()
            }
    }

    // The user dismissed the app from the recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player!!
        if (!player.playWhenReady
            || player.mediaItemCount == 0
            || player.playbackState == Player.STATE_ENDED
            || !player.isPlaying
        ) {
            stopSelf()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }

    private fun getRenderersFactory(): RenderersFactory {
        return DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    }

    private fun getDataSourceFactory(): DataSource.Factory {
        val okHttpDataSource = OkHttpDataSource.Factory(HttpClient.okHttpClient).apply {
            setUserAgent(Util.getUserAgent(this@PlaybackService, getString(R.string.app_name)))
            setDefaultRequestProperties(HttpUtils.getHeaders().toMap())
        }
        return DefaultDataSource.Factory(this, okHttpDataSource)
    }

    private fun getBitmapLoader(): DataSourceBitmapLoader {
        return DataSourceBitmapLoader(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(), getDataSourceFactory())
    }

    private fun getTrackSelector(): DefaultTrackSelector {
        return DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
    }
}
