package org.petkitlocal.feature.history

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.petkitlocal.core.designsystem.R as DesignR
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkFilterChip
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.history.HistoryEntry
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.formatDate
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.core.ui.formatTimeOfDay
import org.petkitlocal.core.ui.formatWeightKg
import org.petkitlocal.core.ui.rememberServerLabels

/** Test tags of History's parts, for the Compose UI and end-to-end tests. */
object HistoryTestTags {
    const val SCREEN = "history"
    const val LIST = "history_list"
    const val SHEET = "history_sheet"
    const val PHOTO_VIEWER = "history_photo_viewer"
    const val CLIP_PLAYER = "history_clip_player"

    fun day(day: LocalDate) = "history_day_$day"

    fun card(id: Long) = "history_card_$id"

    fun time(id: Long) = "history_time_$id"

    fun filter(key: String) = "history_filter_$key"
}

/**
 * The week strip (docs/DESIGN.md, DayStrip): seven local days, the selected one filled, with the
 * way to the week before and, unless this week ends today, to the week after.
 */
@Composable
internal fun DayStrip(
    week: List<LocalDate>,
    selected: LocalDate,
    today: LocalDate,
    hasNextWeek: Boolean,
    onSelect: (LocalDate) -> Unit,
    onShiftWeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        PkIconButton(PkIcons.ChevronLeft, stringResource(R.string.history_previous_week), { onShiftWeek(-1) })
        // The days share what the arrows leave, so the strip fits a narrow phone at any font size.
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            week.forEach { day ->
                DayCell(day, day == selected, day == today, locale, onClick = { onSelect(day) }, Modifier.weight(1f))
            }
        }
        if (hasNextWeek) {
            PkIconButton(PkIcons.ChevronRight, stringResource(R.string.history_next_week), { onShiftWeek(1) })
        } else {
            // Keeps the days where they are; there is no later week to go to.
            Spacer(Modifier.size(PkTheme.spacing.minTouchTarget))
        }
    }
}

@Composable
private fun DayCell(
    day: LocalDate,
    selected: Boolean,
    today: Boolean,
    locale: Locale,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    val description = if (today) {
        "${stringResource(R.string.history_today)}, ${formatDate(day, locale)}"
    } else {
        "${day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)}, ${formatDate(day, locale)}"
    }
    val content = if (selected) colors.onAccent else colors.onSurface
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(PkTheme.shapes.tile)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .background(if (selected) colors.accent else Color.Transparent)
            .padding(vertical = PkTheme.spacing.xs)
            .semantics(mergeDescendants = true) { contentDescription = description }
            .testTag(HistoryTestTags.day(day)),
    ) {
        Text(
            day.dayOfWeek.getDisplayName(TextStyle.NARROW, locale),
            style = PkTheme.typography.labelMedium,
            color = if (selected) colors.onAccent else colors.onSurfaceMuted,
        )
        Text(day.dayOfMonth.toString(), style = PkTheme.typography.titleMedium, color = content)
        // Today is marked by a dot as well as by its name read aloud: never by colour alone.
        Box(
            Modifier
                .padding(top = 2.dp)
                .size(4.dp)
                .clip(CircleShape)
                .background(if (today) content else Color.Transparent),
        )
    }
}

/** The device and pet pickers, then the filter chips (single-select, "all" first). */
@Composable
internal fun FilterRow(
    state: HistoryUiState,
    callbacks: HistoryCallbacks,
    labels: ServerLabels,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = PkTheme.spacing.gutter),
    ) {
        // The pickers first: a History opened from a device's page shows which device at once.
        if (state.devices.size > 1 || state.query.deviceId != null) {
            PickerChip(
                label = state.device?.model ?: if (state.query.deviceId != null) {
                    stringResource(R.string.history_unknown_device)
                } else {
                    stringResource(R.string.history_all_devices)
                },
                selected = state.query.deviceId != null,
                menuTitle = stringResource(R.string.history_pick_device),
                options =
                    listOf(null to stringResource(R.string.history_all_devices)) +
                        state.devices.map { it.id to it.model },
                onPick = callbacks.onDevice,
            )
        }
        if (state.pets.isNotEmpty()) {
            PickerChip(
                label = state.pet?.name ?: stringResource(R.string.history_all_pets),
                selected = state.query.petId != null,
                menuTitle = stringResource(R.string.history_pick_pet),
                options =
                    listOf(null to stringResource(R.string.history_all_pets)) + state.pets.map { it.id to it.name },
                onPick = callbacks.onPet,
            )
        }
        HistoryUiState.CATEGORIES.forEach { category ->
            val key = category?.key ?: ALL
            PkFilterChip(
                label = labels.filter(key),
                selected = state.query.category == category,
                onClick = { callbacks.onCategory(category) },
                modifier = Modifier.testTag(HistoryTestTags.filter(key)),
            )
        }
    }
}

private const val ALL = "all"

/** A chip naming the current choice, opening the list of choices. */
@Composable
private fun PickerChip(
    label: String,
    selected: Boolean,
    menuTitle: String,
    options: List<Pair<Long?, String>>,
    onPick: (Long?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    val colors = PkTheme.colors
    Box {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
                .clip(PkTheme.shapes.chip)
                .selectable(selected = selected, onClick = { open = true }, role = Role.DropdownList)
                .background(if (selected) colors.accentContainer else colors.surface)
                .border(1.dp, if (selected) colors.accent else colors.outline, PkTheme.shapes.chip)
                .padding(start = PkTheme.spacing.m, end = PkTheme.spacing.xs)
                .semantics { contentDescription = "$menuTitle: $label" },
        ) {
            Text(
                label,
                style = PkTheme.typography.labelLarge,
                color = if (selected) colors.onAccentContainer else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 160.dp),
            )
            Icon(
                PkIcons.ExpandMore,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = colors.surface) {
            options.forEach { (id, name) ->
                DropdownMenuItem(
                    text = { Text(name, style = PkTheme.typography.bodyLarge, color = colors.onSurface) },
                    onClick = {
                        open = false
                        onPick(id)
                    },
                )
            }
        }
    }
}

/**
 * One card of the timeline (docs/DESIGN.md, TimelineRow): its time, the pet's colour or the
 * kind's symbol, what happened, on which device, the weight and duration, and its media.
 */
@Composable
internal fun HistoryCard(entry: HistoryEntry, zone: ZoneId, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val item = entry.item
    val colors = PkTheme.colors
    val labels = rememberServerLabels()
    val locale = LocalConfiguration.current.locales[0]
    val details = listOfNotNull(
        entry.device?.model ?: item.deviceId?.let { stringResource(R.string.history_unknown_device) },
        item.durationSec?.let(::formatDurationMinSec),
        item.weightG?.let { formatWeightKg(it.toDouble(), locale) },
    ).joinToString(" · ")
    val timeWidth = rememberTimeColumnWidth(locale)
    PkCard(modifier.fillMaxWidth().testTag(HistoryTestTags.card(item.id)), onClick = onClick) {
        // The card is one button: TalkBack reads its time, title and details together.
        Row(verticalAlignment = Alignment.Top) {
            Text(
                item.shownAt?.let { formatTimeOfDay(it.toKotlinInstant(), zone, locale) }.orEmpty(),
                style = PkTheme.typography.labelLarge,
                color = colors.onSurfaceMuted,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(timeWidth).padding(top = 2.dp).testTag(HistoryTestTags.time(item.id)),
            )
            EntryMark(entry)
            Column(Modifier.padding(start = PkTheme.spacing.s).weight(1f)) {
                Text(
                    entryTitle(item, labels),
                    style = PkTheme.typography.titleMedium,
                    color = colors.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (details.isNotEmpty()) {
                    Text(details, style = PkTheme.typography.bodyMedium, color = colors.onSurfaceMuted)
                }
            }
        }
        val files = item.media?.files.orEmpty()
        if (files.isNotEmpty() || item.media?.videoPending == true) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
                modifier = Modifier.padding(
                    start = timeWidth + MARK_SIZE + PkTheme.spacing.s,
                    top = PkTheme.spacing.s,
                ),
            ) {
                files.take(MAX_THUMBNAILS).forEach { MediaThumbnail(it, size = 56.dp) }
                if (files.isEmpty()) PendingVideo(size = 56.dp)
            }
        }
    }
}

private val MARK_SIZE = 24.dp
private const val MAX_THUMBNAILS = 3

/** What a card is about: the pet for a visit, otherwise its kind, by key. */
@Composable
internal fun entryTitle(item: TimelineItem, labels: ServerLabels): String {
    val visit = TimelineCategory.Toileting in item.categories
    return if (visit) {
        item.pet?.name ?: stringResource(R.string.history_unknown_pet)
    } else {
        val kind = labels.eventKind(item.eventKind ?: item.kind, item.label)
        item.pet?.name?.let { "$kind · $it" } ?: kind
    }
}

/** A visit's pet colour, or the symbol of the card's kind. */
@Composable
internal fun EntryMark(entry: HistoryEntry, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val categories = entry.item.categories
    if (TimelineCategory.Toileting in categories) {
        val color = entry.petColorIndex?.let(colors::pet) ?: colors.onSurfaceMuted
        Box(modifier.size(MARK_SIZE), contentAlignment = Alignment.Center) {
            Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        }
        return
    }
    val (icon, tint) = markIcon(categories)
    Icon(icon, contentDescription = null, tint = tint, modifier = modifier.size(MARK_SIZE))
}

@Composable
private fun markIcon(categories: Set<TimelineCategory>): Pair<ImageVector, Color> {
    val colors = PkTheme.colors
    return when {
        TimelineCategory.Fault in categories || TimelineCategory.HealthAlert in categories ->
            PkIcons.Warning to
                colors.warning

        TimelineCategory.Cleaning in categories -> PkIcons.Clean to colors.onSurfaceMuted

        TimelineCategory.Feeding in categories -> PkIcons.Feed to colors.onSurfaceMuted

        TimelineCategory.Drinking in categories -> PkIcons.WaterDrop to colors.onSurfaceMuted

        TimelineCategory.Pet in categories -> PkIcons.Pets to colors.onSurfaceMuted

        else -> PkIcons.History to colors.onSurfaceMuted
    }
}

/**
 * A photo's or a clip's still, fetched with the bearer header by the app's image loader; a clip
 * carries a play badge. Decorative where its card says what it is; [onClick] makes it a button.
 */
@Composable
internal fun MediaThumbnail(
    file: MediaFile,
    size: Dp,
    modifier: Modifier = Modifier,
    description: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val colors = PkTheme.colors
    val clickable = if (onClick != null) {
        Modifier.selectable(selected = false, onClick = onClick, role = Role.Button)
    } else {
        Modifier
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(PkTheme.shapes.tile)
            .background(colors.surfaceMuted)
            .then(clickable)
            .then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier),
    ) {
        // Until the still arrives (or if it cannot): a photo's symbol; a clip has its play badge.
        if (!file.kind.isVideo) {
            Icon(
                PkIcons.Photo,
                contentDescription = null,
                tint = colors.onSurfaceMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        if (file.thumbnailUrl != null) {
            AsyncImage(
                model = file.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (file.kind.isVideo) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = BADGE_ALPHA)),
            ) {
                Icon(PkIcons.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

private const val BADGE_ALPHA = 0.55f

/** Stands for a recording the server is still assembling. */
@Composable
internal fun PendingVideo(size: Dp, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .clip(PkTheme.shapes.tile)
            .background(colors.surfaceMuted),
    ) {
        Icon(
            PkIcons.Hourglass,
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

/** A line under the list: more is loading, or the next page failed with its retry. */
@Composable
internal fun ListFooter(paging: HistoryPaging, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth().height(56.dp)) {
        when {
            paging.error != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.history_load_more_failed),
                    style = PkTheme.typography.bodyMedium,
                    color = colors.onSurfaceMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f, fill = false),
                )
                PkIconButton(PkIcons.Refresh, stringResource(DesignR.string.pk_retry), onRetry)
            }

            paging.loading -> {
                val loading = stringResource(R.string.history_loading_more)
                CircularProgressIndicator(
                    color = colors.accent,
                    strokeWidth = 3.dp,
                    modifier = Modifier
                        .size(24.dp)
                        .semantics { contentDescription = loading },
                )
            }
        }
    }
}
