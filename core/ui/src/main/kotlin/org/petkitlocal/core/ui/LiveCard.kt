package org.petkitlocal.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
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
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.video.LatestStill

/**
 * What a camera page's live card shows: the camera's latest still, null before it has produced
 * any. [today] and [zone] place the still in time ("Still from 14:32").
 */
data class LiveCardState(
    val still: LatestStill? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val today: LocalDate = LocalDate.now(zone),
)

/** Test tags of the live card, for the Compose UI and end-to-end tests. */
object LiveCardTestTags {
    const val CARD = "live_card"
}

/**
 * The way to a camera's live video, at the top of its page (SPEC.md §5.7): the latest still the
 * camera produced with when it was taken, a play button and "Watch live"; a plain dark card with
 * the play button before there is any still. Nothing plays until it is tapped: a stream costs
 * battery and data, and takes one of the server's few viewer slots. Dark in both themes, like the
 * live video it opens. One button for TalkBack, naming [deviceName] and the still's time.
 */
@Composable
fun LiveCard(deviceName: String, state: LiveCardState, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val still = state.still
    val taken = still?.at?.let { stillLabel(it, state.today, state.zone, locale) }
    val description = if (taken != null) {
        stringResource(R.string.live_card_description_still, deviceName, taken)
    } else {
        stringResource(R.string.live_card_description, deviceName)
    }
    PkTheme(darkTheme = true) {
        val colors = PkTheme.colors
        Box(
            modifier = modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT)
                .clip(PkTheme.shapes.card)
                .background(Brush.linearGradient(listOf(colors.surfaceMuted, colors.background)))
                .clickable(role = Role.Button, onClick = onOpen)
                .semantics { contentDescription = description }
                .testTag(LiveCardTestTags.CARD),
        ) {
            if (still != null) {
                AsyncImage(
                    model = still.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            }
            // What the card says is in its description: TalkBack reads it once, as one button.
            Box(
                Modifier
                    .matchParentSize()
                    .padding(PkTheme.spacing.s)
                    .clearAndSetSemantics {},
            ) {
                if (taken != null) {
                    PicturePill(Modifier.align(Alignment.TopEnd)) { PillText(taken) }
                }
                PlayButton(Modifier.align(Alignment.Center))
                PicturePill(Modifier.align(Alignment.BottomStart)) {
                    Box(
                        Modifier
                            .size(LIVE_DOT)
                            .clip(CircleShape)
                            .background(colors.success),
                    )
                    Spacer(Modifier.width(PkTheme.spacing.xs))
                    PillText(stringResource(R.string.live_card_watch))
                }
            }
        }
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

@Composable
private fun PlayButton(modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(PLAY_SIZE)
            .clip(CircleShape)
            .background(colors.onSurface),
    ) {
        Icon(PkIcons.PlayArrow, contentDescription = null, tint = colors.background, modifier = Modifier.size(32.dp))
    }
}

/** A label over the picture: light on a dark veil, readable whatever the still shows. */
@Composable
private fun PicturePill(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(PkTheme.shapes.chip)
            .background(Color.Black.copy(alpha = VEIL_ALPHA))
            .padding(horizontal = PkTheme.spacing.s, vertical = 6.dp),
    ) { content() }
}

@Composable
private fun PillText(text: String) {
    Text(
        text,
        style = PkTheme.typography.labelLarge,
        color = Color.White,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

private const val CARD_ASPECT = 16f / 9f
private const val VEIL_ALPHA = 0.6f
private val PLAY_SIZE = 56.dp
private val LIVE_DOT = 8.dp
