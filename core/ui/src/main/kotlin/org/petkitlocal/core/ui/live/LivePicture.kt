package org.petkitlocal.core.ui.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.ui.R

/*
 * What is drawn over a camera's live picture, the same on its page's live card and on the live
 * video screen: the badges, what the session is doing, its retry, and the sound button.
 */

/** Test tags of what is drawn over a live picture, wherever it is. */
object LiveVideoTestTags {
    /** "Live" while frames are shown. */
    const val BADGE = "live_badge"

    /** "HLS" while on the fallback. */
    const val TRANSPORT = "live_transport"

    /** What the session is doing when it is not simply playing. */
    const val STATUS = "live_status"
}

/** The picture's own colours, whatever the theme: black around it, white on it. */
val LivePicture = Color.Black
val OnLivePicture = Color.White
const val LIVE_SCRIM_ALPHA = 0.6f
const val LIVE_PILL_ALPHA = 0.55f

/** What a live session says over the picture when it is not simply playing. */
@Immutable
data class LiveStatus(val busy: Boolean, val message: String?, val canRetry: Boolean = false)

/** What [session] says over the picture, or null while it plays or has not started. */
@Composable
fun liveStatusOf(session: LiveSession): LiveStatus? = when (val phase = session.phase) {
    LivePhase.Idle, LivePhase.Playing -> null

    LivePhase.Starting -> LiveStatus(
        busy = true,
        message = stringResource(
            if (session.startingSlowly) R.string.live_connecting_slow else R.string.live_connecting,
        ),
    )

    is LivePhase.Waiting -> LiveStatus(true, stringResource(R.string.live_reconnecting, liveFailureText(phase.failure)))

    is LivePhase.Stopped -> LiveStatus(false, liveFailureText(phase.failure), canRetry = true)

    LivePhase.SessionEnded -> LiveStatus(false, stringResource(R.string.live_session_ended))
}

/**
 * [status] in a column centred in [modifier]: the spinner while busy, the message (announced by
 * TalkBack as it changes), and "Watch again" when only the user can start it again. [compact]
 * fits it in a page's live card, whose controls take the bottom of the picture.
 */
@Composable
fun LiveStatusContent(
    status: LiveStatus,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(
            if (compact) PkTheme.spacing.xs else PkTheme.spacing.s,
            Alignment.CenterVertically,
        ),
        modifier = modifier,
    ) {
        if (status.busy) LiveBusyIndicator()
        if (status.message != null) {
            Text(
                status.message,
                style = if (compact) PkTheme.typography.labelLarge else PkTheme.typography.bodyMedium,
                color = OnLivePicture,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (status.canRetry) PkButton(stringResource(R.string.live_retry), onRetry, icon = PkIcons.Refresh)
    }
}

/**
 * The spinner over the picture. Still in a preview or a screenshot (inspection mode): an endless
 * animation never lets Robolectric's looper go idle, so a screenshot of it never returns.
 */
@Composable
fun LiveBusyIndicator(modifier: Modifier = Modifier) {
    val sized = modifier.size(32.dp)
    if (LocalInspectionMode.current) {
        CircularProgressIndicator(
            progress = { STILL_PROGRESS },
            color = OnLivePicture,
            strokeWidth = 3.dp,
            modifier = sized,
        )
    } else {
        CircularProgressIndicator(color = OnLivePicture, strokeWidth = 3.dp, modifier = sized)
    }
}

private const val STILL_PROGRESS = 0.25f

/** Why the video stopped, in the user's words; the server's own wording is not translated. */
@Composable
fun liveFailureText(failure: LiveFailure): String = stringResource(
    when (failure) {
        LiveFailure.StreamEnded -> R.string.live_failure_ended

        LiveFailure.TooSlow -> R.string.live_failure_too_slow

        LiveFailure.Replaced -> R.string.live_failure_replaced

        LiveFailure.SessionEnded -> R.string.live_session_ended

        is LiveFailure.StreamError -> R.string.live_failure_camera_busy

        is LiveFailure.Network -> R.string.live_failure_network

        is LiveFailure.Playback -> R.string.live_failure_playback

        is LiveFailure.Refused -> when (failure.error) {
            is DomainError.NotFound -> R.string.live_failure_no_stream
            is DomainError.RateLimited -> R.string.live_failure_too_many
            is DomainError.Network -> R.string.live_failure_network
            else -> R.string.live_failure_refused
        }
    },
)

/** "Live" while frames are shown, and "HLS" while on the fallback. */
@Composable
fun LiveBadges(session: LiveSession, modifier: Modifier = Modifier) {
    val playing = session.phase == LivePhase.Playing
    val onHls = session.transport == LiveTransport.Hls &&
        (playing || session.phase == LivePhase.Starting || session.phase is LivePhase.Waiting)
    if (!playing && !onHls) return
    Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), modifier = modifier) {
        if (playing) {
            LivePill(Modifier.testTag(LiveVideoTestTags.BADGE)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PkTheme.colors.danger),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.live_badge), style = PkTheme.typography.labelLarge, color = OnLivePicture)
            }
        }
        if (onHls) {
            LivePill(Modifier.testTag(LiveVideoTestTags.TRANSPORT)) {
                Text(stringResource(R.string.live_hls), style = PkTheme.typography.labelLarge, color = OnLivePicture)
            }
        }
    }
}

/** A label over the picture: light on a dark veil, readable whatever the picture shows. */
@Composable
fun LivePill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(PkTheme.shapes.chip)
            .background(LivePicture.copy(alpha = LIVE_PILL_ALPHA))
            .padding(horizontal = PkTheme.spacing.s, vertical = 6.dp),
    ) { content() }
}

/** The camera's sound: off ("Turn sound on") or on ("Mute"). */
@Composable
fun LiveMuteButton(muted: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    if (muted) {
        PkIconButton(PkIcons.VolumeOff, stringResource(R.string.live_unmute), onToggle, modifier)
    } else {
        PkIconButton(PkIcons.VolumeUp, stringResource(R.string.live_mute), onToggle, modifier)
    }
}

/** Full screen: the live video screen, where Talk and snapshots are. */
@Composable
fun LiveFullscreenButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    PkIconButton(PkIcons.Fullscreen, stringResource(R.string.live_fullscreen), onClick, modifier)
}
