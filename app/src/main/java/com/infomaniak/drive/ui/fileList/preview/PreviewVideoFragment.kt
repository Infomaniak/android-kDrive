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
package com.infomaniak.drive.ui.fileList.preview

import android.content.ComponentName
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.ListenableFuture
import com.infomaniak.drive.MatomoDrive.trackEvent
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.databinding.FragmentPreviewVideoBinding
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.fileList.preview.PreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.utils.IOFile

@UnstableApi
open class PreviewVideoFragment : PreviewFragment() {

    private var _binding: FragmentPreviewVideoBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private var mediaControllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val flagKeepScreenOn = WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
            if (isPlaying) {
                trackMediaPlayerEvent("play")
                toggleFullscreen()
                activity?.window?.addFlags(flagKeepScreenOn)
            } else {
                trackMediaPlayerEvent("pause")
                activity?.window?.clearFlags(flagKeepScreenOn)
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            super.onPlayerError(error)
            error.printStackTrace()

            _binding?.errorLayout?.let {
                when (error.message) {
                    "Source error" -> it.previewDescription.setText(R.string.previewVideoSourceError)
                    else -> it.previewDescription.setText(R.string.previewLoadError)
                }
                it.bigOpenWithButton.isVisible = true
                it.root.isVisible = true
                it.previewDescription.isVisible = true
            }
            _binding?.playerView?.isGone = true
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewVideoBinding.inflate(inflater, container, false).also { _binding = it }.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
        super.onViewCreated(view, savedInstanceState)

        if (noCurrentFile()) return

        container.layoutTransition?.setAnimateParentHierarchy(false)

        errorLayout.apply {
            bigOpenWithButton.apply {
                isGone = true
                setOnClickListener { openWithClicked() }
            }
            fileIcon.setImageResource(file.getFileType().icon)
            fileName.text = file.name
            root.setOnClickListener { toggleFullscreen() }
        }

        playerView.setOnClickListener {
            if ((it as PlayerView).isControllerFullyVisible) {
                trackMediaPlayerEvent("toggleFullScreen")
                toggleFullscreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (!noCurrentFile() && mediaController == null) createPlayer()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onDestroy() {
        mediaController?.let {
            it.release()
            it.removeListener(playerListener)
        }
        mediaController = null

        mediaControllerFuture?.let { MediaController.releaseFuture(it) }
        mediaControllerFuture = null
        super.onDestroy()
    }

    private fun createPlayer() {
        val offlineFile = getOfflineFile()
        val offlineIsComplete = offlineFile?.let { file.isOfflineAndIntact(offlineFile) } ?: false
        initMediaController(offlineFile, offlineIsComplete)
    }

    private fun getOfflineFile(): IOFile? {
        return if (file.isOffline) {
            file.getOfflineFile(requireContext(), previewSliderViewModel.userDrive.userId)
        } else {
            null
        }
    }

    private fun initMediaController(offlineFile: IOFile?, offlineIsComplete: Boolean) = with(binding) {
        val context = requireContext()
        val playbackSessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        mediaControllerFuture = MediaController.Builder(context, playbackSessionToken).buildAsync()
        mediaControllerFuture?.addListener({
            mediaController = mediaControllerFuture?.get()
            mediaController?.setMediaItem(getMediaItem(offlineFile, offlineIsComplete))
            mediaController?.addListener(playerListener)

            playerView.player = mediaController
            playerView.controllerShowTimeoutMs = CONTROLLER_SHOW_TIMEOUT_MS
            playerView.controllerHideOnTouch = false
        }, ContextCompat.getMainExecutor(context))
    }

    private fun getMediaItem(offlineFile: IOFile?, offlineIsComplete: Boolean): MediaItem {
        val mediaMetadata = MediaMetadata.Builder()
            .setTitle(file.name)
            .build()
        return MediaItem.Builder()
            .setMediaId(file.id.toString())
            .setMediaMetadata(mediaMetadata)
            .setUri(getUri(offlineFile, offlineIsComplete))
            .build()
    }

    private fun getUri(offlineFile: IOFile?, offlineIsComplete: Boolean): Uri {
        return if (offlineFile != null && offlineIsComplete) {
            offlineFile.toUri()
        } else {
            Uri.parse(ApiRoutes.downloadFile(file))
        }
    }

    private fun trackMediaPlayerEvent(name: String, value: Float? = null) {
        trackEvent("mediaPlayer", name, value = value)
    }

    companion object {
        private const val CONTROLLER_SHOW_TIMEOUT_MS = 2000
    }
}
