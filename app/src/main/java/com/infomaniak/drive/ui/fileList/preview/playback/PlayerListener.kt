/*
 * Infomaniak kDrive - Android
 * Copyright (C) 2024-2025 Infomaniak Network SA
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

import android.app.Activity
import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.infomaniak.core.sentry.SentryLog
import com.infomaniak.drive.MatomoDrive.trackEvent

class PlayerListener(
    private val activity: Activity?,
    private val isPlayingChanged: (Boolean) -> Unit,
    private val onError: (String?) -> Unit,
) : Player.Listener {

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        activity?.trackMediaPlayerEvent(if (isPlaying) "play" else "pause")
        isPlayingChanged(isPlaying)
    }

    override fun onPlayerError(playbackException: PlaybackException) {
        super.onPlayerError(playbackException)
        handlePlayerError(playbackException)
    }

    private fun handlePlayerError(playbackException: PlaybackException) {
        SentryLog.d(TAG, "Error during media playback", playbackException)
        onError(playbackException.message)
    }

    companion object {

        private const val TAG = "PlayerListener"

        fun Context.trackMediaPlayerEvent(name: String, value: Float? = null) {
            trackEvent("mediaPlayer", name, value = value)
        }
    }
}
