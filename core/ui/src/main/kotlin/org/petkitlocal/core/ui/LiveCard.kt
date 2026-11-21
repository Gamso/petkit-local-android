package org.petkitlocal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.video.LatestStill
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.ui.live.LIVE_SCRIM_ALPHA
import org.petkitlocal.core.ui.live.LiveBadges
import org.petkitlocal.core.ui.live.LiveFullscreenButton
import org.petkitlocal.core.ui.live.LiveMuteButton
import org.petkitlocal.core.ui.live.LivePicture
import org.petkitlocal.core.ui.live.LivePill
import org.petkitlocal.core.ui.live.LiveStatus
import org.petkitlocal.core.ui.live.LiveStatusContent
import org.petkitlocal.core.ui.live.LiveVideoTestTags
import org.petkitlocal.core.ui.live.OnLivePicture
import org.petkitlocal.core.ui.live.liveStatusOf

/**
 * What a camera page's live card shows: the camera's latest still, null before it has produced
 * any, and its live video playing in place ([session]). [today] and [zone] place the still in time
 * ("Still from 14:32").
 */
data class LiveCardState(
    val still: LatestStill? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val today: LocalDate = LocalDate.now(zone),
    /** The live video in the card (ADR 0005), as the page's ViewModel runs it. */
    val session: LiveSession = LiveSession(),
    /** Muted on the page by default, as in PetKit's app: a camera's sound should not surprise anyone. */
    val muted: Boolean = true,
)

/** What the controls on a camera page's live card do. */
data class LiveCardCallbacks(
    /** "Watch again", after the video stopped for good. */
    val onRetry: () -> Unit = {},
    val onToggleMute: () -> Unit = {},
    /** The live video screen, with its Talk and snapshots. */
    val onFullscreen: () -> Unit = {},
)

/** Test tags of the live card, for the Compose UI and end-to-end tests. */
object LiveCardTestTags {
    const val CARD = "live_card"

    /** The cached still, shown until the first frame. */
    const val STILL = "live_card_still"

    /** Full screen: the way to the live video screen. */
    const val FULLSCREEN = "live_card_fullscreen"
}

/**
 * A camera's live video at the top of its page (SPEC.md §5.7), playing in place as in PetKit's
 * app. Until the first frame, the latest still the camera produced (with when it was taken) and a
 * busy indicator; a slow first open says the camera is taking its time, not that it failed. Then
 * the picture with its "Live" badge. What stopped it, and the way to start it again, are said on
 * the picture with the live screen's own words. Over it, always: the sound (muted at first) and
 * full screen, which opens the live video screen. Tapping the picture itself does nothing. Dark in
 * both themes, like the live video. [video] draws the player's picture in the modifier it is given.
 */
@Composable
fun LiveCard(
    deviceName: String,
    state: LiveCardState,
    callbacks: LiveCardCallbacks,
    modifier: Modifier = Modifier,
    video: @Composable (Modifier) -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val still = state.still
    val playing = state.session.phase == LivePhase.Playing
    val taken = still?.at?.let { stillLabel(it, state.today, state.zone, locale) }?.takeUnless { playing }
    val description = if (taken != null) {
        stringResource(R.string.live_card_description_still, deviceName, taken)
    } else {
        stringResource(R.string.live_card_description, deviceName)
    }
    val status = liveStatusOf(state.session)
    PkTheme(darkTheme = true) {
        val colors = PkTheme.colors
        val spacing = PkTheme.spacing
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT)
                .clip(PkTheme.shapes.card)
                .background(Brush.linearGradient(listOf(colors.surfaceMuted, colors.background)))
                .semantics { contentDescription = description }
                .testTag(LiveCardTestTags.CARD),
        ) {
            video(Modifier.matchParentSize())
            // The still covers the picture until it plays, and again once it stopped.
            if (still != null && !playing) {
                AsyncImage(
                    model = still.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .testTag(LiveCardTestTags.STILL),
                )
            }
            if (status != null) StatusOverlay(status, callbacks.onRetry)
            LiveBadges(state.session, Modifier.align(Alignment.TopStart).padding(spacing.s))
            // When the still was taken is in the card's description: TalkBack reads it once.
            if (taken != null && status?.canRetry != true) {
                StillPill(taken, Modifier.align(Alignment.TopEnd).padding(spacing.s))
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing.s),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(spacing.s),
            ) {
                LiveMuteButton(state.muted, callbacks.onToggleMute)
                LiveFullscreenButton(callbacks.onFullscreen, Modifier.testTag(LiveCardTestTags.FULLSCREEN))
            }
        }
    }
}

/** What the session is doing, over a veil that leaves the still readable while it opens. */
@Composable
private fun BoxScope.StatusOverlay(status: LiveStatus, onRetry: () -> Unit) {
    val scrim = if (status.busy) BUSY_SCRIM_ALPHA else LIVE_SCRIM_ALPHA
    Box(
        Modifier
            .matchParentSize()
            .background(LivePicture.copy(alpha = scrim)),
    )
    LiveStatusContent(
        status = status,
        onRetry = onRetry,
        modifier = Modifier
            .matchParentSize()
            .padding(start = PkTheme.spacing.m, end = PkTheme.spacing.m, bottom = CONTROLS_HEIGHT)
            .testTag(LiveVideoTestTags.STATUS),
        compact = true,
    )
}

/** When the still was taken, kept out of TalkBack's way: the card's description says it. */
@Composable
private fun StillPill(taken: String, modifier: Modifier = Modifier) {
    LivePill(modifier.clearAndSetSemantics {}) {
        Text(
            taken,
            style = PkTheme.typography.labelLarge,
            color = OnLivePicture,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** "Still from 14:32", "Still from yesterday, 09:10", or the day without its year. */
@Composable
private fun stillLabel(at: Instant, today: LocalDate, zone: ZoneId, locale: Locale): String {
    val time = formatTimeOfDay(at.toKotlinInstant(), zone, locale)
    return when (val day = at.atZone(zone).toLocalDate()) {
        today -> stringResource(R.string.live_card_still_today, time)
        today.minusDays(1) -> stringResource(R.string.live_card_still_yesterday, time)
        else -> stringResource(R.string.live_card_still_on_day, formatMonthDay(day, locale), time)
    }
}

private const val CARD_ASPECT = 16f / 9f

/** Lighter than over an error: the still stays readable while the video opens. */
private const val BUSY_SCRIM_ALPHA = 0.35f

/** The controls' row at the bottom (a 48 dp button and its margin), which the status keeps clear of. */
private val CONTROLS_HEIGHT = 56.dp
