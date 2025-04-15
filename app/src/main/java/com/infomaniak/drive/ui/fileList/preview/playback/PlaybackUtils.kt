/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2025 Infomaniak Network SA
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

import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
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
import androidx.media3.session.MediaController
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.data.models.File
import com.infomaniak.drive.ui.MainActivity
import com.infomaniak.drive.utils.IOFile
import com.infomaniak.lib.core.networking.HttpClient
import com.infomaniak.lib.core.networking.HttpUtils
import com.infomaniak.lib.core.utils.NotificationUtilsCore
import java.util.concurrent.Executor

@UnstableApi
object PlaybackUtils {

    const val CONTROLLER_SHOW_TIMEOUT_MS = 2000

    var activePlayer: ExoPlayer? = null
    var mediaSession: MediaSession? = null

    private var onServiceDisconnect: (() -> Unit)? = null
    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    fun setServiceOnDisconnect(onDisconnect: () -> Unit) {
        this.onServiceDisconnect = onDisconnect
    }

    fun Context.getMediaController(mainExecutor: Executor, callback: (MediaController) -> Unit) {
        if (mediaController == null) {
            val playbackSessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
            mediaControllerFuture = MediaController.Builder(this, playbackSessionToken).buildAsync().apply {
                addListener(
                    getRunnable(callback),
                    mainExecutor,
                )
            }
        } else {
            callback(mediaController!!)
        }
    }

    fun releasePlayer() {
        activePlayer?.release()
        activePlayer = null
        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        mediaController?.release()
        mediaController = null
        mediaSession?.release()
        mediaSession = null
        onServiceDisconnect = null
    }

    private fun getRunnable(callback: (MediaController) -> Unit): Runnable {
        return Runnable {
            if (mediaController == null) {
                mediaController = mediaControllerFuture?.get()?.apply {
                    callback(this)
                }
            } else {
                callback(mediaController!!)
            }
        }
    }

    fun Context.setMediaSession(isMediaVideo: Boolean) {
        if (mediaSession == null) {
            mediaSession = MediaSession.Builder(this, activePlayer!!)
                .setCallback(getMediaSessionCallback())
                .setBitmapLoader(getBitmapLoader())
                .setSessionActivity(getPendingIntent(isMediaVideo))
                .build()
        } else {
            mediaSession?.player = activePlayer!!
        }
    }

    fun Context.getExoPlayer(): ExoPlayer {
        return ExoPlayer.Builder(this, getRenderersFactory())
            .setMediaSourceFactory(DefaultMediaSourceFactory(getDataSourceFactory()))
            .setTrackSelector(getTrackSelector())
            .build().apply {
                addAnalyticsListener(EventLogger())
                setAudioAttributes(AudioAttributes.DEFAULT,  /* handleAudioFocus= */true)
                playWhenReady = false
                prepare()
            }
    }

    fun getMediaItem(file: File, offlineFile: IOFile?, offlineIsComplete: Boolean): MediaItem {
        val uri = getUri(file, offlineFile, offlineIsComplete)
        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(file.name)

        if (file.getFileType() == ExtensionType.VIDEO) {
            mediaMetadataBuilder.setArtworkUri(getThumbnailUri(file))
        }

        return MediaItem.Builder()
            .setMediaId(file.id.toString())
            .setMediaMetadata(mediaMetadataBuilder.build())
            .setUri(uri)
            .build()
    }

    private fun getThumbnailUri(file: File): Uri {
        return ApiRoutes.getThumbnailUrl(file).toUri()
    }

    private fun getUri(file: File, offlineFile: IOFile?, offlineIsComplete: Boolean): Uri {
        return if (offlineFile != null && offlineIsComplete) {
            offlineFile.toUri()
        } else {
            ApiRoutes.getDownloadFileUrl(file).toUri()
        }
    }

    private fun Context.getDataSourceFactory(): DataSource.Factory {
        val okHttpDataSource = OkHttpDataSource.Factory(HttpClient.okHttpClient).apply {
            setUserAgent(Util.getUserAgent(this@getDataSourceFactory, getString(R.string.app_name)))
            setDefaultRequestProperties(HttpUtils.getHeaders().toMap())
        }
        return DefaultDataSource.Factory(this, okHttpDataSource)
    }

    private fun getMediaSessionCallback(): MediaSession.Callback {
        return object : MediaSession.Callback {

            // When the user returns from the PreviewPlaybackFragment, we want to stop
            // the service because it does not make sense to have the media notification
            // when the user willingly quits the PreviewPlaybackFragment.
            override fun onDisconnected(session: MediaSession, controller: MediaSession.ControllerInfo) {
                super.onDisconnected(session, controller)
                onServiceDisconnect?.invoke()
            }
        }
    }

    private fun Context.getRenderersFactory(): RenderersFactory {
        return DefaultRenderersFactory(this)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
    }

    private fun Context.getTrackSelector(): DefaultTrackSelector {
        return DefaultTrackSelector(this).apply {
            setParameters(buildUponParameters().setMaxVideoSizeSd())
        }
    }

    private fun Context.getPendingIntent(isMediaVideo: Boolean): PendingIntent {

        val intent = getIntentForMedia(isMediaVideo)

        return PendingIntent.getActivity(
            this,
            0,
            intent,
            NotificationUtilsCore.pendingIntentFlags
        )
    }

    private fun Context.getIntentForMedia(isMediaVideo: Boolean): Intent {
        return if (isMediaVideo) {
            Intent(this, VideoActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        } else {
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_MAIN
                addCategory(Intent.CATEGORY_LAUNCHER)
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
            }
        }
    }

    private fun Context.getBitmapLoader(): DataSourceBitmapLoader {
        return DataSourceBitmapLoader(DataSourceBitmapLoader.DEFAULT_EXECUTOR_SERVICE.get(), getDataSourceFactory())
    }

    fun getPictureInPictureParams(ratio: Rational): PictureInPictureParams? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            val pipParams = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                pipParams.setAutoEnterEnabled(true)
            }

            pipParams.build()
        } else {
            null
        }
    }
}
