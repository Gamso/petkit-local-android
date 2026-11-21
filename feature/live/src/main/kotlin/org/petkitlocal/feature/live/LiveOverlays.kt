package org.petkitlocal.feature.live

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
/**
 * The spinner over the picture. Still in a preview or a screenshot (inspection mode): an endless
 * animation never lets Robolectric's looper go idle, so a screenshot of it never returns.
 */
@Composable
private fun BusyIndicator() {
    val modifier = Modifier.size(32.dp)
    if (LocalInspectionMode.current) {
        CircularProgressIndicator(
            progress = { STILL_PROGRESS },
            color = OnPicture,
            strokeWidth = 3.dp,
            modifier = modifier,
        )
    } else {
        CircularProgressIndicator(color = OnPicture, strokeWidth = 3.dp, modifier = modifier)
    }
}

private const val STILL_PROGRESS = 0.25f

/** Over the picture: what the session is doing when it is not simply playing. */
@Composable
internal fun SessionOverlay(uiState: LiveUiState, onRetry: () -> Unit) {
    val phase = uiState.session.phase
    val (busy, message) = when (uiState.availability) {
        LiveAvailability.Loading -> true to null

        LiveAvailability.NotACamera -> false to stringResource(R.string.live_not_a_camera)

        LiveAvailability.Gone -> false to stringResource(R.string.live_gone)

        LiveAvailability.Camera -> when (phase) {
            LivePhase.Idle, LivePhase.Playing -> return
            LivePhase.Starting -> true to stringResource(R.string.live_connecting)
            is LivePhase.Waiting -> true to stringResource(R.string.live_reconnecting, failureText(phase.failure))
            is LivePhase.Stopped -> false to failureText(phase.failure)
            LivePhase.SessionEnded -> false to stringResource(R.string.live_session_ended)
        }
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s, Alignment.CenterVertically),
        modifier = Modifier
            .fillMaxSize()
            .background(Picture.copy(alpha = SCRIM_ALPHA))
            .padding(PkTheme.spacing.m)
            .testTag(LiveTestTags.STATUS),
    ) {
        if (busy) BusyIndicator()
        if (message != null) {
            Text(
                message,
                style = PkTheme.typography.bodyMedium,
                color = OnPicture,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            )
        }
        if (phase is LivePhase.Stopped && uiState.availability == LiveAvailability.Camera) {
            PkButton(stringResource(R.string.live_retry), onRetry, icon = PkIcons.Refresh)
        }
    }
}

/** Why the video stopped, in the user's words; the server's own wording is not translated. */
@Composable
private fun failureText(failure: LiveFailure): String = stringResource(
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
internal fun Badges(session: LiveSession, modifier: Modifier = Modifier) {
    val playing = session.phase == LivePhase.Playing
    val onHls = session.transport == LiveTransport.Hls &&
        (playing || session.phase == LivePhase.Starting || session.phase is LivePhase.Waiting)
    if (!playing && !onHls) return
    Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), modifier = modifier) {
        if (playing) {
            Pill(Modifier.testTag(LiveTestTags.BADGE)) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(PkTheme.colors.danger),
                )
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.live_badge), style = PkTheme.typography.labelLarge, color = OnPicture)
            }
        }
        if (onHls) {
            Pill(Modifier.testTag(LiveTestTags.TRANSPORT)) {
                Text(stringResource(R.string.live_hls), style = PkTheme.typography.labelLarge, color = OnPicture)
            }
        }
    }
}

@Composable
internal fun Pill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(PkTheme.shapes.chip)
            .background(Picture.copy(alpha = PILL_ALPHA))
            .padding(horizontal = PkTheme.spacing.s, vertical = 6.dp),
    ) { content() }
}
