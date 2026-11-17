package org.petkitlocal.feature.device.fountain

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.fountain.FountainPage
import org.petkitlocal.core.domain.fountain.PetDrinks
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.StatCompareTile
import org.petkitlocal.feature.device.common.rememberDayChangeFormatter
import org.petkitlocal.feature.device.litter.CardTitle

/**
 * Today at this fountain: its drinks and their average duration since yesterday, and each pet's
 * drinks, on a fountain that records them (the W7H); the pump's time today on one that counts it
 * (a Bluetooth fountain, which keeps no yesterday to compare with, and reports no drinks).
 */
@Composable
internal fun FountainTodayCard(page: FountainPage, modifier: Modifier = Modifier) {
    val changes = rememberDayChangeFormatter()
    PkCard(modifier.fillMaxWidth().testTag(FountainTestTags.TODAY)) {
        CardTitle(stringResource(R.string.fountain_today))
        Row(
            horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier
                .padding(top = PkTheme.spacing.s)
                .height(IntrinsicSize.Min),
        ) {
            if (page.recordsDrinks) {
                StatCompareTile(
                    label = stringResource(R.string.fountain_drinks),
                    figure = page.today.drinks.toString(),
                    change = changes.count(page.today.drinks, page.yesterday.drinks.takeIf { it > 0 }),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
                val average = page.today.averageDurationSec
                StatCompareTile(
                    label = stringResource(R.string.fountain_average_drink),
                    figure = average?.let(::formatDurationMinSec) ?: stringResource(R.string.litter_no_average),
                    change = average?.let { changes.duration(it, page.yesterday.averageDurationSec) },
                    spokenFigure = average?.let(changes::spokenDuration) ?: stringResource(R.string.litter_no_average),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
            page.pumpTodaySec?.let { seconds ->
                StatCompareTile(
                    label = stringResource(R.string.fountain_pump_today),
                    figure = pumpTime(seconds),
                    change = null,
                    note = stringResource(R.string.fountain_pump_since_midnight),
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                )
            }
        }
        if (page.recordsDrinks) {
            page.perPet.forEach { drinks -> PetDrinksRow(drinks, page.colorIndexOf(drinks.pet.id)) }
        } else {
            Text(
                stringResource(R.string.fountain_no_drinks_reported),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = PkTheme.spacing.s),
            )
        }
    }
}

/** One pet's drinks today, with yesterday's: its colour as on the chart, its name, the counts. */
@Composable
private fun PetDrinksRow(drinks: PetDrinks, colorIndex: Int?) {
    val colors = PkTheme.colors
    val counts = listOf(
        pluralStringResource(R.plurals.fountain_pet_drinks, drinks.today, drinks.today),
        pluralStringResource(R.plurals.fountain_pet_yesterday, drinks.yesterday, drinks.yesterday),
    ).joinToString(" · ")
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PkTheme.spacing.s)
            .semantics(mergeDescendants = true) {}
            .testTag(FountainTestTags.pet(drinks.pet.id)),
    ) {
        Box(
            Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(colorIndex?.let(colors::pet) ?: colors.onSurfaceMuted),
        )
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            drinks.pet.name,
            style = PkTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(counts, style = PkTheme.typography.labelMedium, color = colors.onSurfaceMuted)
    }
}

/** The pump's time today: "4 h 05", or "35 min" under an hour. */
@Composable
private fun pumpTime(seconds: Int): String {
    val minutes = seconds.coerceAtLeast(0) / SECONDS_PER_MINUTE
    return if (minutes < MINUTES_PER_HOUR) {
        stringResource(R.string.fountain_minutes, minutes)
    } else {
        stringResource(R.string.fountain_hours_minutes, minutes / MINUTES_PER_HOUR, minutes % MINUTES_PER_HOUR)
    }
}

private const val SECONDS_PER_MINUTE = 60
private const val MINUTES_PER_HOUR = 60
