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
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.infomaniak.drive.ui.fileList.preview.playback.PlaybackUtils.setServiceOnDisconnect

@UnstableApi
class PlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()

        setServiceOnDisconnect {
            release()
            stopSelf()
        }
    }

    // The user dismissed the app from the recent tasks
    override fun onTaskRemoved(rootIntent: Intent?) {
        PlaybackUtils.mediaSession?.player?.let { player ->
            if (!player.playWhenReady
                || player.mediaItemCount == 0
                || player.playbackState == Player.STATE_ENDED
                || !player.isPlaying
            ) {
                release()
                stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = PlaybackUtils.mediaSession

    override fun onDestroy() {
        release()
        super.onDestroy()
    }

    private fun release() {
        PlaybackUtils.releasePlayer()
    }
}
