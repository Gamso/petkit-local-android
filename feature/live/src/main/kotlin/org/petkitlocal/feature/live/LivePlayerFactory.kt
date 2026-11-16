package org.petkitlocal.feature.live

import android.content.Context
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LiveTransport

/**
 * Builds the player of a camera's live video. `:app` binds it to Media3's ExoPlayer on
 * core:network's live sources (the fMP4 WebSocket, or HLS), both on the app's authenticated
 * client; this module sees only the [Player] interface. The player comes with its source set and
 * is prepared by the caller, which also releases it: releasing it closes its socket.
 */
interface LivePlayerFactory {
    fun create(context: Context, deviceId: Long, transport: LiveTransport): Player

    /** What stopped a player of this factory, from the error it reported. */
    fun failureOf(error: PlaybackException): LiveFailure
}
