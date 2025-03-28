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
package com.infomaniak.drive.ui.fileList.preview.playback

import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.annotation.RequiresApi
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import com.infomaniak.drive.R
import com.infomaniak.drive.data.api.ApiRoutes
import com.infomaniak.drive.data.models.ExtensionType
import com.infomaniak.drive.databinding.FragmentPreviewPlaybackBinding
import com.infomaniak.drive.ui.BasePreviewSliderFragment
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.openWithClicked
import com.infomaniak.drive.ui.BasePreviewSliderFragment.Companion.toggleFullscreen
import com.infomaniak.drive.ui.fileList.preview.PreviewFragment
import com.infomaniak.drive.ui.fileList.preview.playback.PlayerListener.Companion.trackMediaPlayerEvent
import com.infomaniak.drive.utils.IOFile

@UnstableApi
open class PreviewPlaybackFragment : PreviewFragment() {

    private var _binding: FragmentPreviewPlaybackBinding? = null
    private val binding get() = _binding!! // This property is only valid between onCreateView and onDestroyView

    private val flagKeepScreenOn by lazy { WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON }

    private val offlineFile: IOFile? by lazy {
        if (file.isOffline) {
            file.getOfflineFile(requireContext(), previewSliderViewModel.userDrive.userId)
        } else {
            null
        }
    }

    private val offlineIsComplete by lazy { isOfflineFileComplete(offlineFile) }

    @RequiresApi(Build.VERSION_CODES.N)
    private val playerListener = PlayerListener(
        activity,
        isPlayingChanged = { isPlaying ->
            if (isPlaying) {
                toggleFullscreen()
                activity?.window?.addFlags(flagKeepScreenOn)
            } else {
                activity?.window?.clearFlags(flagKeepScreenOn)
            }
        },
        onError = { playbackExceptionMessage ->
            _binding?.errorLayout?.apply {
                when (playbackExceptionMessage) {
                    "Source error" -> previewDescription.setText(R.string.previewVideoSourceError)
                    else -> previewDescription.setText(R.string.previewLoadError)
                }
                bigOpenWithButton.isVisible = true
                root.isVisible = true
                previewDescription.isVisible = true
            }
            _binding?.playerView?.isGone = true
        },
    )

    private var isInPictureInPictureMode = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return FragmentPreviewPlaybackBinding.inflate(inflater, container, false).also { _binding = it }.root
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
                context?.trackMediaPlayerEvent("toggleFullScreen")
                toggleFullscreen()
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    override fun onResume() {
        super.onResume()
        getMediaController { mediaController ->
            if (!mediaController.isPlaying && !isInPictureInPictureMode) {
                mediaController.removeListener(playerListener)
                mediaController.addListener(playerListener)

                mediaController.setMediaItem(
                    getMediaItem(offlineFile, offlineIsComplete),
                    (parentFragment as BasePreviewSliderFragment).positionsForMedia[file.id] ?: 0L,
                )

                //switchTargetView(mediaController, binding.playerView, binding.playerView)

                binding.playerView.player = mediaController
                binding.playerView.controllerShowTimeoutMs = CONTROLLER_SHOW_TIMEOUT_MS
                binding.playerView.controllerHideOnTouch = false

                binding.playerView.setShowSubtitleButton(true)
            } else {
                isInPictureInPictureMode = false
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Compute the percentage of the video the user watched before exiting
        getMediaController { mediaController ->
            val currentMediaPercentage = mediaController.currentPosition.times(100)
            val currentMediaDuration = mediaController.contentDuration
            requireContext().trackMediaPlayerEvent("duration", currentMediaPercentage.div(currentMediaDuration + 1).toFloat())
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.playerView.player?.release()
        _binding = null
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode)

        if (!this.isInPictureInPictureMode) this.isInPictureInPictureMode = isInPictureInPictureMode
    }

    fun onFragmentUnselected() {
        getMediaController { mediaController ->
            if (mediaController.currentMediaItem?.mediaId?.toInt() == file.id) {
                mediaController.pause()
                (parentFragment as BasePreviewSliderFragment).positionsForMedia[file.id] = mediaController.currentPosition
            }
            binding.playerView.player = null
        }
    }

    private fun isOfflineFileComplete(offlineFile: IOFile?) = offlineFile?.let { file.isOfflineAndIntact(it) } ?: false

    private fun getMediaItem(offlineFile: IOFile?, offlineIsComplete: Boolean): MediaItem {
        val uri = getUri(offlineFile, offlineIsComplete)
        val mediaMetadataBuilder = MediaMetadata.Builder()
            .setTitle(file.name)

        if (file.getFileType() == ExtensionType.VIDEO) {
            mediaMetadataBuilder.setArtworkUri(getThumbnailUri())
        }

        return MediaItem.Builder()
            .setMediaId(file.id.toString())
            .setMediaMetadata(mediaMetadataBuilder.build())
            .setUri(uri)
            .build()
    }

    private fun getThumbnailUri(): Uri {
        return ApiRoutes.getThumbnailUrl(file).toUri()
    }

    private fun getUri(offlineFile: IOFile?, offlineIsComplete: Boolean): Uri {
        return if (offlineFile != null && offlineIsComplete) {
            offlineFile.toUri()
        } else {
            ApiRoutes.getDownloadFileUrl(file).toUri()
        }
    }

    companion object {
        private const val CONTROLLER_SHOW_TIMEOUT_MS = 2000
    }
}
