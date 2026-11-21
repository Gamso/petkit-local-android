package org.petkitlocal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
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
import org.petkitlocal.core.ui.live.DEFAULT_LIVE_ASPECT
import org.petkitlocal.core.ui.live.LIVE_PILL_ALPHA
import org.petkitlocal.core.ui.live.LIVE_SCRIM_ALPHA
import org.petkitlocal.core.ui.live.LiveBadges
import org.petkitlocal.core.ui.live.LiveExitFullscreenButton
import org.petkitlocal.core.ui.live.LiveFullscreenButton
import org.petkitlocal.core.ui.live.LiveMuteButton
import org.petkitlocal.core.ui.live.LivePicture
import org.petkitlocal.core.ui.live.LivePill
import org.petkitlocal.core.ui.live.LiveSnapshotButton
import org.petkitlocal.core.ui.live.LiveSnapshotNotice
import org.petkitlocal.core.ui.live.LiveStatus
import org.petkitlocal.core.ui.live.LiveStatusContent
import org.petkitlocal.core.ui.live.LiveVideoTestTags
import org.petkitlocal.core.ui.live.OnLivePicture
import org.petkitlocal.core.ui.live.SnapshotResult
import org.petkitlocal.core.ui.live.liveStatusOf
import org.petkitlocal.core.ui.talk.TalkCallbacks
import org.petkitlocal.core.ui.talk.TalkControls
import org.petkitlocal.core.ui.talk.TalkUiState

/**
 * What a camera page's live card shows: the camera's latest still, null before it has produced
 * any, and its live video playing in place ([session]), on the page or full screen. [today] and
 * [zone] place the still in time ("Still from 14:32").
 */
data class LiveCardState(
    val still: LatestStill? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val today: LocalDate = LocalDate.now(zone),
    /** The live video in the card (ADR 0005), as the page's ViewModel runs it. */
    val session: LiveSession = LiveSession(),
    /** Muted on the page by default, as in PetKit's app: a camera's sound should not surprise anyone. */
    val muted: Boolean = true,
    /** The same picture, the same player, taking the whole window. */
    val fullscreen: Boolean = false,
    /** Talking through the camera. */
    val talk: TalkUiState = TalkUiState(),
    val savingSnapshot: Boolean = false,
    /** What the last snapshot came to, for a few seconds. */
    val snapshot: SnapshotResult? = null,
) {
    /**
     * Whether the camera's sound is off: as the user set it, and always while Talk is busy, since
     * the camera has no echo cancellation. [muted] is kept, so the user's choice comes back.
     */
    val soundMuted: Boolean get() = muted || talk.busy
}

/** What the controls on a camera page's live card, and its full screen, do. */
data class LiveCardCallbacks(
    /** "Watch again", after the video stopped for good. */
    val onRetry: () -> Unit = {},
    val onToggleMute: () -> Unit = {},
    /** Full screen in place: the same player takes the whole window, in landscape. */
    val onFullscreen: () -> Unit = {},
    /** Back to the page from full screen. */
    val onExitFullscreen: () -> Unit = {},
    /** Full screen's snapshot of the frame shown. */
    val onSnapshot: () -> Unit = {},
    val talk: TalkCallbacks = TalkCallbacks(),
)

/** Test tags of the live card, for the Compose UI and end-to-end tests. */
object LiveCardTestTags {
    const val CARD = "live_card"

    /** The cached still, shown until the first frame. */
    const val STILL = "live_card_still"

    /** Full screen in place. */
    const val FULLSCREEN = "live_card_fullscreen"

    /** The card's picture taking the whole window. */
    const val FULLSCREEN_VIEW = "live_card_fullscreen_view"

    /** Back from full screen. */
    const val EXIT_FULLSCREEN = "live_card_exit_fullscreen"
}

/**
 * A camera's live video at the top of its page (SPEC.md §5.7), playing in place as in PetKit's
 * app, with Talk under it. Until the first frame, the latest still the camera produced (with when
 * it was taken) and a busy indicator; a slow first open says the camera is taking its time, not
 * that it failed. Then the picture with its "Live" badge. What stopped it, and the way to start it
 * again, are said on the picture with the live screen's own words. Over its top, always: the sound
 * (muted at first) and full screen, kept clear of the bottom corners where cameras print their
 * time. Tapping the picture itself does nothing. Dark in both themes, like the live video. [video]
 * draws the player's picture in the modifier it is given; [aspectRatio] is the video's.
 */
@Composable
fun LiveCard(
    deviceName: String,
    state: LiveCardState,
    callbacks: LiveCardCallbacks,
    modifier: Modifier = Modifier,
    aspectRatio: Float = DEFAULT_LIVE_ASPECT,
    video: @Composable (Modifier) -> Unit = {},
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
        modifier = modifier.fillMaxWidth(),
    ) {
        PkTheme(darkTheme = true) {
            val colors = PkTheme.colors
            LivePictureBox(
                deviceName = deviceName,
                state = state,
                onRetry = callbacks.onRetry,
                aspectRatio = aspectRatio,
                // Full screen, the picture is drawn there: one surface for the one player.
                video = if (state.fullscreen) ({}) else video,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(CARD_ASPECT)
                    .clip(PkTheme.shapes.card)
                    .background(Brush.linearGradient(listOf(colors.surfaceMuted, colors.background)))
                    .testTag(LiveCardTestTags.CARD),
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(PkTheme.spacing.s),
                ) {
                    LiveMuteButton(state.soundMuted, callbacks.onToggleMute)
                    LiveFullscreenButton(callbacks.onFullscreen, Modifier.testTag(LiveCardTestTags.FULLSCREEN))
                }
            }
        }
        TalkControls(state.talk, callbacks.talk)
    }
}

/**
 * The card's picture taking the whole window, the same player and the same session: badges and
 * sound, snapshot and the way back at the top, Talk at the bottom between the corners where the
 * camera prints its time. Always dark; the caller hides the system bars and turns the screen.
 */
@Composable
fun LiveCardFullscreen(
    deviceName: String,
    state: LiveCardState,
    callbacks: LiveCardCallbacks,
    modifier: Modifier = Modifier,
    aspectRatio: Float = DEFAULT_LIVE_ASPECT,
    video: @Composable (Modifier) -> Unit = {},
) {
    PkTheme(darkTheme = true) {
        LivePictureBox(
            deviceName = deviceName,
            state = state,
            onRetry = callbacks.onRetry,
            aspectRatio = aspectRatio,
            video = video,
            fullscreen = true,
            modifier = modifier
                .fillMaxSize()
                .background(LivePicture)
                // Nothing under it is reachable while it covers the page.
                .pointerInput(Unit) {}
                .testTag(LiveCardTestTags.FULLSCREEN_VIEW),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .safeDrawingPadding()
                    .padding(PkTheme.spacing.m),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
                    LiveMuteButton(state.soundMuted, callbacks.onToggleMute)
                    LiveSnapshotButton(state.savingSnapshot, callbacks.onSnapshot)
                    LiveExitFullscreenButton(
                        callbacks.onExitFullscreen,
                        Modifier.testTag(LiveCardTestTags.EXIT_FULLSCREEN),
                    )
                }
                LiveSnapshotNotice(
                    state.snapshot,
                    Modifier.clip(PkTheme.shapes.chip).background(LivePicture.copy(alpha = LIVE_PILL_ALPHA)),
                )
            }
            TalkControls(
                state.talk,
                callbacks.talk,
                Modifier
                    .align(Alignment.BottomCenter)
                    .safeDrawingPadding()
                    .padding(PkTheme.spacing.m)
                    .clip(PkTheme.shapes.card)
                    .background(LivePicture.copy(alpha = LIVE_PILL_ALPHA))
                    .padding(PkTheme.spacing.s),
            )
        }
    }
}

/**
 * The picture and what is said over it, on the page or full screen: [video] (at its own
 * [aspectRatio]), the still until it plays, the session's status, the badges top left and when the
 * still was taken bottom left. [controls] go over it last.
 */
@Composable
private fun LivePictureBox(
    deviceName: String,
    state: LiveCardState,
    onRetry: () -> Unit,
    aspectRatio: Float,
    video: @Composable (Modifier) -> Unit,
    modifier: Modifier = Modifier,
    fullscreen: Boolean = false,
    controls: @Composable BoxScope.() -> Unit,
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
    val edge = if (fullscreen) Modifier.safeDrawingPadding() else Modifier
    Box(modifier.semantics { contentDescription = description }) {
        video(
            Modifier
                .align(Alignment.Center)
                .aspectRatio(aspectRatio),
        )
        // The still covers the picture until it plays, and again once it stopped.
        if (still != null && !playing) {
            AsyncImage(
                model = still.thumbnailUrl,
                contentDescription = null,
                contentScale = if (fullscreen) ContentScale.Fit else ContentScale.Crop,
                modifier = Modifier
                    .matchParentSize()
                    .testTag(LiveCardTestTags.STILL),
            )
        }
        if (status != null) StatusOverlay(status, onRetry, fullscreen)
        LiveBadges(state.session, Modifier.align(Alignment.TopStart).then(edge).padding(PkTheme.spacing.s))
        // When the still was taken is in the card's description: TalkBack reads it once.
        if (taken != null && status?.canRetry != true && !fullscreen) {
            StillPill(taken, Modifier.align(Alignment.BottomStart).padding(PkTheme.spacing.s))
        }
        controls()
    }
}

/** What the session is doing, over a veil that leaves the still readable while it opens. */
@Composable
private fun BoxScope.StatusOverlay(status: LiveStatus, onRetry: () -> Unit, fullscreen: Boolean) {
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
            .padding(start = PkTheme.spacing.m, end = PkTheme.spacing.m, top = CONTROLS_HEIGHT)
            .testTag(LiveVideoTestTags.STATUS),
        compact = !fullscreen,
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

/** The controls' row at the top (a 48 dp button and its margin), which the status keeps clear of. */
private val CONTROLS_HEIGHT = 56.dp
