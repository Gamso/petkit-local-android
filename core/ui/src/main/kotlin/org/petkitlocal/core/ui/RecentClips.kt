package org.petkitlocal.core.ui

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.petkitlocal.core.designsystem.R as DesignR
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.video.RecentClip

/**
 * A camera's recent clips as a screen shows them: what the cache holds ([clips]), and where the
 * refresh that brings the newest from the server stands. [today] and [zone] place each clip in
 * time ("Today, 14:32").
 */
data class RecentClipsState(
    val clips: List<RecentClip> = emptyList(),
    /** The cache has been read. */
    val cacheLoaded: Boolean = false,
    val refreshing: Boolean = false,
    /** A refresh has answered: what the cache holds is what the server has. */
    val refreshed: Boolean = false,
    /** Why the last refresh failed; cleared by the next one. */
    val error: DomainError? = null,
    val zone: ZoneId = ZoneId.systemDefault(),
    val today: LocalDate = LocalDate.now(zone),
) {
    /** Nothing to show yet, and the server has not answered: the skeleton. */
    val loading: Boolean get() = clips.isEmpty() && !refreshed && (error == null || refreshing)

    /** Nothing cached, and the server could not be read: the error with its retry. */
    val failedEmpty: Boolean get() = clips.isEmpty() && !refreshed && error != null && !refreshing

    /** The camera has recorded no clip, as the server says. */
    val empty: Boolean get() = clips.isEmpty() && refreshed
}

/** Test tags of the recent clips, for the Compose UI and end-to-end tests. */
object RecentClipsTestTags {
    const val SECTION = "recent_clips"

    fun clip(itemId: Long) = "recent_clip_$itemId"
}

/**
 * "Recent clips" (SPEC.md §5.7), under a camera's live video and on its page: the newest clips
 * side by side, each opening the clip player, then the way to History for the rest. Loading, it
 * keeps the room the clips will take; with none, it says so in one line; when the server cannot
 * be read and nothing is cached, it says why, with a retry.
 */
@Composable
fun RecentClipsCard(
    state: RecentClipsState,
    onOpenClip: (MediaFile) -> Unit,
    onSeeAll: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PkCard(modifier.fillMaxWidth().testTag(RecentClipsTestTags.SECTION)) {
        Text(
            stringResource(R.string.recent_clips_title),
            style = PkTheme.typography.titleMedium,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        Box(Modifier.padding(top = PkTheme.spacing.s)) {
            when {
                state.loading -> LoadingTiles()
                state.failedEmpty -> FailedLine(state.error, onRetry)
                state.empty -> Line(stringResource(R.string.recent_clips_empty))
                else -> ClipRow(state, onOpenClip)
            }
        }
        if (state.clips.isNotEmpty()) {
            PkButton(
                stringResource(R.string.recent_clips_see_all),
                onClick = onSeeAll,
                style = PkButtonStyle.Text,
                icon = PkIcons.History,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            )
        }
    }
}

@Composable
private fun ClipRow(state: RecentClipsState, onOpenClip: (MediaFile) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
        items(state.clips, key = { it.item.id }) { clip ->
            ClipTile(clip, state.today, state.zone, onClick = { onOpenClip(clip.clip) })
        }
    }
}

/** One clip: its still with a play badge, what happened, and when. One button for TalkBack. */
@Composable
private fun ClipTile(clip: RecentClip, today: LocalDate, zone: ZoneId, onClick: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val title = timelineTitle(clip.item, rememberServerLabels())
    val time = clip.item.shownAt?.let { whenLabel(it, today, zone, locale) }
    val play = stringResource(R.string.recent_clips_play)
    Column(
        modifier = Modifier
            .width(TILE_WIDTH)
            // Not clipped to the still's rounded shape: its corners would cut the lines under it.
            .clickable(onClickLabel = play, role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {}
            .testTag(RecentClipsTestTags.clip(clip.item.id)),
    ) {
        MediaThumbnail(clip.clip, size = TILE_WIDTH, aspectRatio = TILE_ASPECT)
        Text(
            title,
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = PkTheme.spacing.xxs),
        )
        if (time != null) {
            Text(
                time,
                style = PkTheme.typography.labelMedium,
                color = PkTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** "Today, 14:32", "Yesterday, 09:10", or the day without its year: "Nov 12, 7:05 AM". */
@Composable
private fun whenLabel(at: Instant, today: LocalDate, zone: ZoneId, locale: Locale): String {
    val time = formatTimeOfDay(at.toKotlinInstant(), zone, locale)
    return when (val day = at.atZone(zone).toLocalDate()) {
        today -> stringResource(R.string.recent_clips_today, time)
        today.minusDays(1) -> stringResource(R.string.recent_clips_yesterday, time)
        else -> stringResource(R.string.recent_clips_on_day, formatMonthDay(day, locale), time)
    }
}

/** A day as the locale writes it without its year: `Nov 12`, `12 nov.`. */
private fun formatMonthDay(day: LocalDate, locale: Locale): String =
    DateTimeFormatter.ofPattern(DateFormat.getBestDateTimePattern(locale, "MMMd"), locale).format(day)

/** Grey tiles where the clips will be: static, so screenshots are stable. */
@Composable
private fun LoadingTiles() {
    val label = stringResource(DesignR.string.pk_loading)
    Row(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = Modifier
            .horizontalScroll(rememberScrollState(), enabled = false)
            .semantics { contentDescription = label },
    ) {
        repeat(LOADING_TILES) {
            Box(
                Modifier
                    .width(TILE_WIDTH)
                    .aspectRatio(TILE_ASPECT)
                    .clip(PkTheme.shapes.tile)
                    .background(PkTheme.colors.surfaceMuted),
            )
        }
    }
}

@Composable
private fun FailedLine(error: DomainError?, onRetry: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        Line(
            stringResource(
                if (error is DomainError.Network) R.string.recent_clips_unreachable else R.string.recent_clips_failed,
            ),
        )
        PkButton(
            stringResource(DesignR.string.pk_retry),
            onClick = onRetry,
            style = PkButtonStyle.Secondary,
            icon = PkIcons.Refresh,
        )
    }
}

@Composable
private fun Line(text: String) {
    Text(text, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
}

private val TILE_WIDTH = 136.dp
private const val TILE_ASPECT = 16f / 9f
private const val LOADING_TILES = 3
