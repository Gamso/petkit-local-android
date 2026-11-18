package org.petkitlocal.feature.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.petkitlocal.core.designsystem.R as DesignR
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.EventField
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.MediaKind
import org.petkitlocal.core.domain.model.TimelineMedia
import org.petkitlocal.core.ui.MediaThumbnail
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.formatDate
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.core.ui.formatTimeOfDay
import org.petkitlocal.core.ui.formatWeightKg
import org.petkitlocal.core.ui.localizeEventFieldText
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.core.ui.timelineTitle

/** The detail sheet of one card, over History. */
@OptIn(ExperimentalMaterial3Api::class) // ModalBottomSheet
@Composable
internal fun EventSheet(sheet: EventSheetState, zone: ZoneId, callbacks: HistoryCallbacks) {
    ModalBottomSheet(
        onDismissRequest = callbacks.onCloseSheet,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = PkTheme.shapes.sheet,
        containerColor = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        modifier = Modifier.testTag(HistoryTestTags.SHEET),
    ) {
        EventSheetContent(sheet, zone, callbacks)
    }
}

/**
 * What the sheet holds, scrollable from the top: what happened and when, the event's photos and
 * clips, and its decoded fields — those this app can name first, the rest on demand.
 */
@Composable
internal fun EventSheetContent(
    sheet: EventSheetState,
    zone: ZoneId,
    callbacks: HistoryCallbacks,
    modifier: Modifier = Modifier,
) {
    val labels = rememberServerLabels()
    val locale = LocalConfiguration.current.locales[0]
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = PkTheme.spacing.gutter)
            .padding(bottom = PkTheme.spacing.xl)
            .navigationBarsPadding(),
    ) {
        SheetHeader(sheet, zone, labels, locale)
        sheet.media?.let { media -> MediaSection(media, callbacks) }
        FieldsSection(sheet, labels, locale, zone, callbacks.onRetryEvent)
        PkButton(
            stringResource(R.string.history_close),
            onClick = callbacks.onCloseSheet,
            style = PkButtonStyle.Secondary,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SheetHeader(sheet: EventSheetState, zone: ZoneId, labels: ServerLabels, locale: Locale) {
    val item = sheet.entry.item
    val colors = PkTheme.colors
    val time = item.shownAt?.let {
        val instant = it.toKotlinInstant()
        "${formatDate(it.atZone(zone).toLocalDate(), locale)} · ${formatTimeOfDay(instant, zone, locale)}"
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        EntryMark(sheet.entry)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(
                timelineTitle(item, labels),
                style = PkTheme.typography.titleLarge,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            listOfNotNull(time, sheet.entry.device?.modelName).takeIf { it.isNotEmpty() }?.let {
                Text(it.joinToString(" · "), style = PkTheme.typography.bodyMedium, color = colors.onSurfaceMuted)
            }
        }
    }
    val figures = listOfNotNull(
        item.weightG?.let { formatWeightKg(it.toDouble(), locale) },
        item.durationSec?.let(::formatDurationMinSec),
    )
    if (figures.isNotEmpty()) {
        Text(
            figures.joinToString("   "),
            style = PkTheme.typography.headlineSmall,
            color = colors.onSurface,
        )
    }
}

@Composable
private fun MediaSection(media: TimelineMedia, callbacks: HistoryCallbacks) {
    if (media.photos.isNotEmpty()) {
        SectionTitle(stringResource(R.string.history_photos))
        MediaRow(media.photos, callbacks.onOpenPhoto)
    }
    if (media.clips.isNotEmpty() || media.videoPending) {
        SectionTitle(stringResource(R.string.history_clips))
        if (media.clips.isNotEmpty()) MediaRow(media.clips, callbacks.onOpenClip)
        if (media.videoPending) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(PkIcons.Hourglass, contentDescription = null, tint = PkTheme.colors.onSurfaceMuted)
                Spacer(Modifier.width(PkTheme.spacing.xs))
                Text(
                    stringResource(R.string.history_video_pending),
                    style = PkTheme.typography.bodyMedium,
                    color = PkTheme.colors.onSurfaceMuted,
                )
            }
        }
    }
}

@Composable
private fun MediaRow(files: List<MediaFile>, onOpen: (MediaFile) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
        modifier = Modifier.horizontalScroll(rememberScrollState()),
    ) {
        files.forEach { file ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(MEDIA_SIZE)) {
                val name = mediaKindName(file.kind)
                MediaThumbnail(file, size = MEDIA_SIZE, description = name, onClick = { onOpen(file) })
                Text(
                    name,
                    style = PkTheme.typography.labelMedium,
                    color = PkTheme.colors.onSurfaceMuted,
                    maxLines = 2,
                    modifier = Modifier.padding(top = PkTheme.spacing.xxs),
                )
            }
        }
    }
}

private val MEDIA_SIZE = 104.dp

@Composable
internal fun mediaKindName(kind: MediaKind): String = stringResource(
    when (kind) {
        MediaKind.Snapshot -> R.string.history_media_snapshot
        MediaKind.Waste -> R.string.history_media_waste
        MediaKind.Health -> R.string.history_media_health
        MediaKind.Highlight -> R.string.history_media_highlight
        MediaKind.Playback -> R.string.history_media_playback
        MediaKind.Timelapse -> R.string.history_media_timelapse
    },
)

@Composable
private fun FieldsSection(
    sheet: EventSheetState,
    labels: ServerLabels,
    locale: Locale,
    zone: ZoneId,
    onRetry: () -> Unit,
) {
    SectionTitle(stringResource(R.string.history_details))
    val detail = sheet.detail
    when {
        detail != null -> Fields(detail.fields, labels, locale, zone)

        sheet.loading -> CircularProgressIndicator(
            color = PkTheme.colors.accent,
            strokeWidth = 3.dp,
            modifier = Modifier.padding(PkTheme.spacing.xs).width(24.dp).height(24.dp),
        )

        else -> Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
            Text(
                if (sheet.error is DomainError.Network) {
                    stringResource(R.string.history_unreachable)
                } else {
                    stringResource(R.string.history_details_failed)
                },
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurface,
            )
            PkButton(
                stringResource(DesignR.string.pk_retry),
                onClick = onRetry,
                style = PkButtonStyle.Secondary,
                icon = PkIcons.Refresh,
            )
        }
    }
}

/** The fields this app can name, then, on demand, the others as the server labels them. */
@Composable
private fun Fields(fields: List<EventField>, labels: ServerLabels, locale: Locale, zone: ZoneId) {
    var showAll by rememberSaveable { mutableStateOf(false) }
    val (known, others) = fields.partition { labels.knowsEventField(it.key) }
    val shown = if (showAll || known.isEmpty()) fields else known
    if (shown.isEmpty()) {
        Text(
            stringResource(R.string.history_no_details),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurfaceMuted,
        )
        return
    }
    Column {
        shown.forEachIndexed { index, field ->
            if (index > 0) HorizontalDivider(color = PkTheme.colors.outline)
            FieldRow(
                label = labels.eventField(field.key, field.label),
                value = localizeEventFieldText(field.text, labels, locale, zone),
            )
        }
    }
    if (known.isNotEmpty() && others.isNotEmpty()) {
        PkButton(
            if (showAll) {
                stringResource(R.string.history_fewer_details)
            } else {
                pluralStringResource(R.plurals.history_more_details, others.size, others.size)
            },
            onClick = { showAll = !showAll },
            style = PkButtonStyle.Text,
            icon = if (showAll) PkIcons.ExpandLess else PkIcons.ExpandMore,
        )
    }
}

@Composable
private fun FieldRow(label: String, value: String) {
    Row(
        verticalAlignment = Alignment.Top,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = PkTheme.spacing.xs)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(
            label,
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurfaceMuted,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            value,
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = PkTheme.typography.titleMedium,
        color = PkTheme.colors.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}
