package org.petkitlocal.feature.device.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.activity.ActivityAvatar
import org.petkitlocal.core.domain.activity.ActivityEntry
import org.petkitlocal.core.domain.activity.ActivityKind
import org.petkitlocal.core.domain.activity.ActivityStep
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.ui.MediaThumbnail
import org.petkitlocal.core.ui.PetAvatar
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.SymbolAvatar
import org.petkitlocal.core.ui.UnknownPetAvatar
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.core.ui.formatTimeOfDay
import org.petkitlocal.core.ui.formatWeightKg
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.core.ui.rememberTimeColumnWidth
import org.petkitlocal.core.ui.timelineSymbol
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.litter.CardTitle

/** Test tags of a device's activity, for the UI and end-to-end tests. */
object ActivityTestTags {
    const val CARD = "device:activity"

    fun row(id: Long): String = "device:activity:$id"
}

/**
 * What a page adds to a row of its activity: the feeder's portions in place of the row's own
 * details, and a clip to open beside it.
 */
internal data class ActivityRowExtras(
    /** Shown under the sentence instead of the row's own details. */
    val details: AnnotatedString? = null,
    /** What TalkBack reads for [details]. */
    val spokenDetails: String? = null,
    /** A clip of the event, opened from its thumbnail; nothing is drawn without one. */
    val clip: MediaFile? = null,
)

/**
 * Today's activity of a device, newest first (SPEC.md §5.3, Activity): one row per visit, drink or
 * meal with the pet's picture and a sentence, the steps that followed indented under it, and the
 * way to History. TalkBack reads each row, its steps included, as one item.
 *
 * A page may name it otherwise ([title]), put chips over it ([header]), list the whole day rather
 * than its last [maxRows], and add to each row ([extras]): the feeder's day does all four.
 */
@Composable
internal fun ActivityCard(
    entries: List<ActivityEntry>,
    zone: ZoneId,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier,
    title: String = stringResource(R.string.device_activity),
    maxRows: Int = MAX_ROWS,
    onOpenClip: (MediaFile) -> Unit = {},
    extras: @Composable (ActivityEntry) -> ActivityRowExtras? = { null },
    header: @Composable () -> Unit = {},
) {
    val locale = LocalConfiguration.current.locales[0]
    val timeWidth = rememberTimeColumnWidth(locale)
    PkCard(modifier.fillMaxWidth().testTag(ActivityTestTags.CARD)) {
        CardTitle(title)
        header()
        if (entries.isEmpty()) {
            Text(
                stringResource(R.string.device_activity_empty),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            )
        }
        entries.take(maxRows).forEach { entry ->
            ActivityRow(entry, zone, locale, timeWidth, extras(entry), onOpenClip)
        }
        PkButton(
            stringResource(R.string.device_see_history),
            onClick = onOpenHistory,
            style = PkButtonStyle.Text,
            icon = PkIcons.History,
            modifier = Modifier.padding(top = PkTheme.spacing.xs),
        )
    }
}

private const val MAX_ROWS = 6
private val AVATAR_SIZE = 36.dp

/** A clip's thumbnail beside its row: small, at the clips' own 16:10. */
private val CLIP_WIDTH = 64.dp
private const val CLIP_ASPECT = 1.6f

/** The connector runs just inside the picture's edge. */
private val CONNECTOR_INSET = 4.dp

@Composable
private fun ActivityRow(
    entry: ActivityEntry,
    zone: ZoneId,
    locale: Locale,
    timeWidth: Dp,
    extras: ActivityRowExtras?,
    onOpenClip: (MediaFile) -> Unit,
) {
    val colors = PkTheme.colors
    val labels = rememberServerLabels()
    val durations = rememberDayChangeFormatter()
    val time = entry.item.shownAt?.let { formatTimeOfDay(it.toKotlinInstant(), zone, locale) }.orEmpty()
    val weight = entry.item.weightG
        ?.takeIf { entry.kind == ActivityKind.Visit }
        ?.let { formatWeightKg(it.toDouble(), locale) }
    val headline = withName(
        listOfNotNull(sentence(entry, labels, ::formatDurationMinSec), weight).joinToString(" · "),
        entry.petName,
    )
    val spokenHeadline = listOfNotNull(sentence(entry, labels, durations::spokenDuration), weight).joinToString(", ")
    val details = extras?.details ?: eventDetails(entry, labels, locale)?.let(::AnnotatedString)
    val spokenDetails = extras?.spokenDetails ?: details?.text
    val steps = entry.steps.map { step -> StepLine(step, zone, locale, labels) }
    // One item for TalkBack: the row, then each step, durations in words.
    val spoken = (listOf(listOfNotNull(time, spokenHeadline, spokenDetails)) + steps.map { listOf(it.time, it.text) })
        .joinToString(". ") { parts -> parts.filter(String::isNotEmpty).joinToString(", ") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PkTheme.spacing.s)
            .semantics(mergeDescendants = true) { contentDescription = spoken }
            .testTag(ActivityTestTags.row(entry.item.id)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                time,
                style = PkTheme.typography.labelLarge,
                color = colors.onSurface,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.width(timeWidth),
            )
            ActivityPicture(entry)
            Column(Modifier.padding(start = PkTheme.spacing.s).weight(1f)) {
                Text(
                    headline,
                    style = PkTheme.typography.bodyLarge,
                    color = if (entry.outcome?.done == false) colors.danger else colors.onSurface,
                )
                details?.let { Text(it, style = PkTheme.typography.labelMedium, color = colors.onSurfaceMuted) }
            }
            extras?.clip?.let { clip ->
                MediaThumbnail(
                    clip,
                    size = CLIP_WIDTH,
                    aspectRatio = CLIP_ASPECT,
                    description = stringResource(R.string.device_activity_play),
                    onClick = { onOpenClip(clip) },
                    modifier = Modifier.padding(start = PkTheme.spacing.s),
                )
            }
        }
        if (steps.isNotEmpty()) StepLines(steps, Modifier.padding(start = timeWidth))
    }
}

/**
 * The steps under their row, behind a dotted line falling from the row's picture, as on the
 * mockup: they start under the picture rather than under the sentence, which leaves them room at
 * a large font size.
 */
@Composable
private fun StepLines(steps: List<StepLine>, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val line = colors.outline
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
        modifier = modifier
            .padding(start = CONNECTOR_INSET, top = PkTheme.spacing.xs)
            .drawBehind {
                val x = if (layoutDirection == LayoutDirection.Rtl) size.width else 0f
                val dot = 2.dp.toPx()
                drawLine(
                    color = line,
                    start = Offset(x, dot),
                    end = Offset(x, size.height - dot),
                    strokeWidth = dot,
                    cap = StrokeCap.Round,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(0f, dot * DOT_GAP)),
                )
            }
            .padding(start = PkTheme.spacing.s),
    ) {
        steps.forEach { step ->
            Text(
                listOf(step.time, step.text).filter(String::isNotEmpty).joinToString(" · "),
                style = PkTheme.typography.bodyMedium,
                color = if (step.failed) colors.danger else colors.onSurfaceMuted,
            )
        }
    }
}

/** Dots of the connector are this many dot widths apart. */
private const val DOT_GAP = 3f

/** A step as its line shows it: its time and what happened, translated. */
private class StepLine(val time: String, val text: String, val failed: Boolean) {
    constructor(step: ActivityStep, zone: ZoneId, locale: Locale, labels: ServerLabels) : this(
        time = step.at?.let { formatTimeOfDay(it.toKotlinInstant(), zone, locale) }.orEmpty(),
        text = labels.step(step.parsed, step.label),
        failed = step.failed,
    )
}

/** The pet's photo, initial or paw; the kind's symbol for a row that is not about a pet. */
@Composable
private fun ActivityPicture(entry: ActivityEntry) {
    when (val avatar = entry.avatar) {
        is ActivityAvatar.Pet -> PetAvatar(avatar.name, avatar.photoUrl, avatar.colorIndex, AVATAR_SIZE)

        ActivityAvatar.Unknown -> UnknownPetAvatar(AVATAR_SIZE)

        null -> {
            val (icon, tint) = timelineSymbol(entry.item.categories)
            SymbolAvatar(icon, tint, AVATAR_SIZE)
        }
    }
}

/** [text] with the pet's [name] in bold, as on the mockup, wherever the language puts it. */
private fun withName(text: String, name: String?): AnnotatedString = buildAnnotatedString {
    append(text)
    val at = name?.let(text::indexOf) ?: -1
    if (name != null && at >= 0) addStyle(SpanStyle(fontWeight = FontWeight.SemiBold), at, at + name.length)
}

/**
 * What a row says: "Mochi spent 1:35", "A pet drank", "Cleaning done". [duration] writes a
 * duration: as shown, or in words for TalkBack.
 */
@Composable
private fun sentence(entry: ActivityEntry, labels: ServerLabels, duration: (Int) -> String): String {
    val item = entry.item
    val name = entry.petName
    val seconds = item.durationSec?.let(duration)
    return when (entry.kind) {
        ActivityKind.Visit -> petSentence(
            name,
            seconds,
            R.string.activity_visit,
            R.string.activity_visit_short,
            R.string.activity_visit_unknown,
            R.string.activity_visit_unknown_short,
        )

        ActivityKind.Drink -> petSentence(
            name,
            seconds,
            R.string.activity_drink,
            R.string.activity_drink_short,
            R.string.activity_drink_unknown,
            R.string.activity_drink_unknown_short,
        )

        // A meal names its pet only when a camera feeder recognised one.
        ActivityKind.Meal -> if (item.pet != null) {
            petSentence(
                name,
                seconds,
                R.string.activity_meal,
                R.string.activity_meal_short,
                R.string.activity_meal_unknown,
                R.string.activity_meal_unknown_short,
            )
        } else {
            eventTitle(entry, labels)
        }

        ActivityKind.PetSeen -> name?.let { stringResource(R.string.activity_pet_seen, it) }
            ?: stringResource(R.string.activity_pet_seen_unknown)

        ActivityKind.Event -> eventTitle(entry, labels)
    }
}

/** A sentence about a pet: named or not, with how long or without. */
@Composable
@Suppress("LongParameterList") // The four wordings of one sentence.
private fun petSentence(
    name: String?,
    duration: String?,
    named: Int,
    namedShort: Int,
    unnamed: Int,
    unnamedShort: Int,
): String = when {
    name != null && duration != null -> stringResource(named, name, duration)
    name != null -> stringResource(namedShort, name)
    duration != null -> stringResource(unnamed, duration)
    else -> stringResource(unnamedShort)
}

/** A row not about a pet: its outcome translated ("Cleaning done"), else its kind. */
private fun eventTitle(entry: ActivityEntry, labels: ServerLabels): String {
    val item = entry.item
    return labels.step(entry.outcome, labels.eventKind(item.eventKind ?: item.kind, item.label))
}

/** Under an event's title: how it was started, and its duration. */
private fun eventDetails(entry: ActivityEntry, labels: ServerLabels, locale: Locale): String? {
    if (entry.kind != ActivityKind.Event && entry.avatar != null) return null
    val item = entry.item
    return listOfNotNull(
        entry.outcome?.trigger?.let(labels::eventFieldValue),
        item.durationSec?.let(::formatDurationMinSec),
        item.weightG?.let { formatWeightKg(it.toDouble(), locale) },
    ).joinToString(" · ").ifEmpty { null }
}
