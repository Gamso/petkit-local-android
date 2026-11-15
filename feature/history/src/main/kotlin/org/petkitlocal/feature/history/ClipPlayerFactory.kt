package org.petkitlocal.feature.history

import android.content.Context
import androidx.media3.common.Player

/**
 * Builds the player of a clip. `:app` binds it to Media3's ExoPlayer reading through the app's
 * authenticated HTTP stack (core:network), so a clip carries the bearer header and seeks with
 * `Range` requests; this module sees only the [Player] interface. The caller releases the player.
 */
fun interface ClipPlayerFactory {
    fun create(context: Context): Player
}
