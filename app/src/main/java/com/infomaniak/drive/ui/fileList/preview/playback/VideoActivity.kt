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
import android.os.Bundle
import android.util.Rational
import android.view.WindowManager
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.infomaniak.drive.R
import com.infomaniak.drive.databinding.ActivityVideoBinding
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.CONTROLLER_SHOW_TIMEOUT_MS
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getExoPlayer
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getMediaItem
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.getPictureInPictureParams
import com.infomaniak.drive.utils.isDontKeepActivitiesEnabled
import com.infomaniak.drive.utils.setupStatusBarForPreview
import com.infomaniak.drive.utils.shouldExcludeFromRecents
import com.infomaniak.drive.utils.toggleSystemBar

@UnstableApi
class VideoActivity : AppCompatActivity() {

    private val viewModel: PlaybackViewModel by viewModels()

    private val binding by lazy { ActivityVideoBinding.inflate(layoutInflater) }

    private val flagKeepScreenOn by lazy { WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON }

    private val playerListener = PlayerListener(
        this,
        isPlayingChanged = { isPlaying ->
            if (isPlaying) {
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

    private val exoPlayer: ExoPlayer by lazy { getExoPlayer() }
    private val videoRatio by lazy {
        exoPlayer.videoFormat?.let { videoFormat ->
            if (videoFormat.width > videoFormat.height) {
                Rational(16, 9)
            } else {
                Rational(9, 16)
            }
        }
    }

    private val finishPlayerReceiver = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == TAG) finish()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(binding.root)

        shouldExcludeFromRecents(!isDontKeepActivitiesEnabled())

        with(binding.playerView) {
            player = exoPlayer
            controllerShowTimeoutMs = CONTROLLER_SHOW_TIMEOUT_MS
            controllerHideOnTouch = false
            showController()
        }

        exoPlayer.addListener(playerListener)
        exoPlayer.playWhenReady = true

        loadVideo(intent)

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
        videoRatio?.let {
            getPictureInPictureParams(it)?.let { pictureInPictureParams ->
                setPictureInPictureParams(pictureInPictureParams)
            }
        }
    }

    private fun loadVideo(intent: Intent) {
        intent.extras?.let { VideoActivityArgs.fromBundle(it) }?.fileId?.let { videoFileId ->
            if (videoFileId > 0) {
                viewModel.loadFile(videoFileId)
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
