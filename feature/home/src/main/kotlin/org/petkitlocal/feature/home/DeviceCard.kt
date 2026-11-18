package org.petkitlocal.feature.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.photo.DevicePhoto
import org.petkitlocal.core.designsystem.photo.PkDevicePhoto
import org.petkitlocal.core.designsystem.theme.PkColors
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.home.DayEvent
import org.petkitlocal.core.domain.home.DayEventType
import org.petkitlocal.core.domain.home.DeviceOverview
import org.petkitlocal.core.domain.home.DeviceStatus
import org.petkitlocal.core.domain.home.Headline
import org.petkitlocal.core.ui.rememberServerLabels

/**
 * One device on Home (docs/DESIGN.md, DeviceCard): name and picture (the product's photo, else a
 * glyph), the family's headline figure, the 24-hour strip of the day's cards, and the status band
 * at the bottom. [petColors] gives each pet's colour index, so a visit on the strip has its pet's
 * colour.
 *
 * A Bluetooth accessory with a card of its own says, discreetly under its name, which device it
 * reports through.
 */
@Composable
internal fun DeviceCard(
    overview: DeviceOverview,
    petColors: Map<Long, Int>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    Surface(
        onClick = onClick,
        shape = PkTheme.shapes.card,
        color = colors.surface,
        contentColor = colors.onSurface,
        shadowElevation = 1.dp,
        modifier = modifier,
    ) {
        Column {
            Column(
                verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, top = 14.dp, end = 14.dp, bottom = PkTheme.spacing.s),
            ) {
                // The name gets the card's whole width: model names are long, and at a large font
                // scale a word squeezed beside the glyph would break in the middle.
                Text(
                    overview.device.model,
                    style = PkTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                overview.relay?.let { RelayLine(it) }
                Row {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
                        modifier = Modifier.weight(1f),
                    ) {
                        HeadlineFigure(overview.headline)
                    }
                    DevicePicture(overview)
                }
                Spacer(Modifier.weight(1f))
                DayStrip(overview.dayEvents, petColors)
            }
            StatusBand(overview.status)
        }
    }
}

/**
 * The model's product photo when the app has one (issue #69, layout C), on the card's own colour;
 * else the family's glyph on a small tile.
 */
@Composable
private fun DevicePicture(overview: DeviceOverview) {
    val colors = PkTheme.colors
    val device = overview.device
    val photo = DevicePhoto.of(device.type, device.model)
    if (photo != null) {
        PkDevicePhoto(
            photo,
            decodeSize = PHOTO_SIZE,
            modifier = Modifier.size(PHOTO_SIZE),
            backdrop = colors.surface,
            shape = RoundedCornerShape(10.dp),
        )
        return
    }
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceMuted),
    ) {
        Icon(
            HomeIcons.forFamily(device.family),
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(22.dp),
        )
    }
}

private val PHOTO_SIZE = 56.dp

/** "via Pura Max": small and muted, part of the card (which opens the accessory itself). */
@Composable
private fun RelayLine(relay: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            PkIcons.Link,
            contentDescription = null,
            tint = PkTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(PkTheme.spacing.xxs))
        Text(
            stringResource(R.string.home_relay, relay),
            style = PkTheme.typography.labelMedium,
            color = PkTheme.colors.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HeadlineFigure(headline: Headline) {
    val locale = LocalConfiguration.current.locales[0]
    val (label, value, unit) = when (headline) {
        is Headline.Visits -> Triple(stringResource(R.string.home_visits_today), headline.count.toString(), null)

        is Headline.Served -> {
            val label = stringResource(R.string.home_served_today)
            val dispensed = headline.dispensed
            if (dispensed != null) {
                Triple(label, NumberFormat.getNumberInstance(locale).format(dispensed.value), dispensed.unit)
            } else {
                Triple(label, headline.meals.toString(), stringResource(R.string.home_meals_unit))
            }
        }

        is Headline.Water -> Triple(
            stringResource(R.string.home_water),
            stringResource(if (headline.low) R.string.home_water_low else R.string.home_water_ok),
            null,
        )

        is Headline.Drinks -> Triple(stringResource(R.string.home_drinks_today), headline.count.toString(), null)

        is Headline.Level -> Triple(
            levelLabel(headline.entityKey, headline.entityName, rememberServerLabels()),
            stringResource(R.string.home_percent, headline.percent),
            null,
        )

        Headline.None -> Triple(stringResource(R.string.home_no_figure), "–", null)
    }
    Text(label, style = PkTheme.typography.labelLarge, color = PkTheme.colors.onSurfaceMuted)
    Text(
        buildAnnotatedString {
            append(value)
            if (unit != null) {
                withStyle(
                    SpanStyle(
                        fontSize = PkTheme.typography.titleMedium.fontSize,
                        color = PkTheme.colors.onSurfaceMuted,
                    ),
                ) {
                    append(" $unit")
                }
            }
        },
        style = PkTheme.typography.headlineLarge,
        color = if (headline is Headline.Water && headline.low) PkTheme.colors.warning else PkTheme.colors.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    if (headline is Headline.Served && headline.dispensed != null) {
        Text(
            pluralStringResource(R.plurals.home_meals, headline.meals, headline.meals),
            style = PkTheme.typography.labelMedium,
            color = PkTheme.colors.onSurfaceMuted,
        )
    }
}

/**
 * The day's cards on a 0-24 h axis, one bar each: a visit or a drink as tall as it was long, in
 * its pet's colour; anything else short, in the colour of its kind. TalkBack reads the count.
 */
@Composable
internal fun DayStrip(events: List<DayEvent>, petColors: Map<Long, Int>, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val description = pluralStringResource(R.plurals.home_strip_description, events.size, events.size)
    Column(modifier.semantics(mergeDescendants = true) { contentDescription = description }) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(STRIP_HEIGHT),
        ) {
            val barWidth = BAR_WIDTH.toPx()
            drawLine(
                colors.outline,
                start = Offset(0f, size.height - 0.5f),
                end = Offset(size.width, size.height - 0.5f),
                strokeWidth = 1.dp.toPx(),
            )
            events.forEach { event ->
                val x = (size.width - barWidth) * event.minuteOfDay / MINUTES_PER_DAY
                val height = (barHeightFraction(event) * size.height).coerceAtLeast(MIN_BAR.toPx())
                drawRoundRect(
                    color = barColor(event, petColors, colors),
                    topLeft = Offset(x, size.height - height),
                    size = Size(barWidth, height),
                    cornerRadius = CornerRadius(barWidth / 2),
                )
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf(R.string.home_strip_start, R.string.home_strip_middle, R.string.home_strip_end).forEach {
                Text(stringResource(it), style = PkTheme.typography.labelSmall, color = colors.onSurfaceMuted)
            }
        }
    }
}

private val STRIP_HEIGHT = 34.dp
private val BAR_WIDTH = 4.dp
private val MIN_BAR = 8.dp
private const val MINUTES_PER_DAY = 24 * 60
private const val SHORT_BAR = 0.4f
private const val LONG_VISIT_SEC = 300f

/** A visit or a drink grows with its duration, up to five minutes; the rest are short. */
private fun barHeightFraction(event: DayEvent): Float {
    val duration = event.durationSec
    return if (duration != null && (event.type == DayEventType.Visit || event.type == DayEventType.Drink)) {
        (SHORT_BAR + (1 - SHORT_BAR) * (duration / LONG_VISIT_SEC)).coerceAtMost(1f)
    } else {
        SHORT_BAR
    }
}

private fun barColor(event: DayEvent, petColors: Map<Long, Int>, colors: PkColors): Color {
    val petColor = event.petId?.let(petColors::get)?.let(colors::pet)
    return when (event.type) {
        DayEventType.Alert -> colors.warning
        DayEventType.Visit, DayEventType.Drink, DayEventType.PetSeen -> petColor ?: colors.accent
        DayEventType.Meal -> colors.secondary
        DayEventType.Cleaning, DayEventType.Other -> colors.onSurfaceMuted
    }
}

/** The band at the bottom of a card: a word for the state, never its colour alone. */
@Composable
internal fun StatusBand(status: DeviceStatus, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    val (background, content) = when (status) {
        DeviceStatus.Idle -> colors.accentContainer to colors.onAccentContainer
        is DeviceStatus.Busy -> colors.surfaceMuted to colors.onSurface
        DeviceStatus.Offline -> colors.surfaceMuted to colors.onSurfaceMuted
        is DeviceStatus.Error -> colors.warningContainer to colors.onWarningContainer
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = PkTheme.spacing.s, vertical = 10.dp),
    ) {
        when (status) {
            DeviceStatus.Offline -> Icon(PkIcons.Offline, null, tint = content, modifier = Modifier.size(16.dp))

            is DeviceStatus.Error -> Icon(PkIcons.Warning, null, tint = colors.warning, modifier = Modifier.size(16.dp))

            else -> Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (status == DeviceStatus.Idle) colors.accent else colors.secondary),
            )
        }
        Spacer(Modifier.size(6.dp))
        Text(
            statusLabel(status, rememberServerLabels()),
            style = PkTheme.typography.labelLarge,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
