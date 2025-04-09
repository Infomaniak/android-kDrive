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

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ActivityVideoBinding
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.CONTROLLER_SHOW_TIMEOUT_MS
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getExoPlayer
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getMediaController
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getMediaItem
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getPictureInPictureParams
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getRatio
import com.infomaniak.drive.utils.setupStatusBarForPreview
import com.infomaniak.drive.utils.shouldExcludeFromRecents
import com.infomaniak.drive.utils.toggleSystemBar


@UnstableApi
class VideoActivity : AppCompatActivity() {

    private val viewModel: PlaybackViewModel by viewModels()

    private val binding by lazy { ActivityVideoBinding.inflate(layoutInflater) }

    private val flagKeepScreenOn by lazy { WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON }

    @RequiresApi(Build.VERSION_CODES.N)
    private val playerListener = PlayerListener(
        this,
        isPlayingChanged = { isPlaying ->
            if (isPlaying) {
                exoPlayer.videoFormat?.let { videoFormat ->
                    videoRatio = getRatio(videoFormat.width, videoFormat.height)
                }
                setPIPParams()
                window?.addFlags(flagKeepScreenOn)
            } else {
                window?.clearFlags(flagKeepScreenOn)
            }
        },
        onError = { playbackExceptionMessage ->
            binding.errorLayout.apply {
                when (playbackExceptionMessage) {
                    "Source error" -> previewDescription.setText(R.string.previewVideoSourceError)
                    else -> previewDescription.setText(R.string.previewLoadError)
                }
                bigOpenWithButton.isVisible = true
                root.isVisible = true
                previewDescription.isVisible = true
            }
            binding.playerView.isGone = true
        },
    )

    private val executor by lazy { ContextCompat.getMainExecutor(this) }
    private val exoPlayer: ExoPlayer by lazy { getExoPlayer() }

    private var videoRatio = Rational(1, 1)

    private val finishPlayerReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TAG) finish()
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        shouldExcludeFromRecents(true)

        binding.playerView.player = exoPlayer
        binding.playerView.controllerShowTimeoutMs = CONTROLLER_SHOW_TIMEOUT_MS
        binding.playerView.controllerHideOnTouch = false

        applicationContext.getMediaController(executor) {
            exoPlayer.addListener(playerListener)

            loadVideo(intent)
            exoPlayer.playWhenReady = true

            binding.playerView.player = exoPlayer
            binding.playerView.controllerShowTimeoutMs = CONTROLLER_SHOW_TIMEOUT_MS
            binding.playerView.controllerHideOnTouch = false
        }

        toggleSystemBar(show = false)

        LocalBroadcastManager.getInstance(this).registerReceiver(finishPlayerReceiver, IntentFilter(TAG))
    }

    override fun onStart() {
        super.onStart()
        setupStatusBarForPreview()
    }

    override fun onDestroy() {
        LocalBroadcastManager.getInstance(this).unregisterReceiver(finishPlayerReceiver)
        binding.playerView.player?.release()
        super.onDestroy()
    }

    private fun setPIPParams() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getPictureInPictureParams(videoRatio)?.let {
                setPictureInPictureParams(it)
            }
        }
    }

    private fun loadVideo(intent: Intent) {
        intent.extras?.let { VideoActivityArgs.fromBundle(it) }?.let {
            it.userDrive
            if (it.fileId > 0) {
                viewModel.currentFile = viewModel.getCurrentFile(it.fileId)
                exoPlayer.setMediaItem(getMediaItem(viewModel.currentFile!!, viewModel.offlineFile, viewModel.offlineIsComplete))
            } else {
                finish()
            }
        }
    }

    companion object {
        const val TAG = "VideoActivity"
    }
}
