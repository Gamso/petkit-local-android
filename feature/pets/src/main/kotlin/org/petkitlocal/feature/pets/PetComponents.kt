package org.petkitlocal.feature.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.pets.PetPage
import org.petkitlocal.core.domain.pets.PetVisit
import org.petkitlocal.core.ui.PetAvatar
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.core.ui.formatTimeOfDay
import org.petkitlocal.core.ui.formatWeightKg

/** Test tags of the pet screens' parts, for the Compose UI and end-to-end tests. */
object PetsTestTags {
    const val LIST_SCREEN = "pets"
    const val LIST = "pets_list"
    const val SCREEN = "pet"
    const val PAGE = "pet_page"
    const val CHART = "pet_weight_chart"

    fun card(petId: Long) = "pets_card_$petId"

    fun switch(petId: Long) = "pet_switch_$petId"

    fun visit(id: Long) = "pet_visit_$id"
}

/** How the litter boxes tell [pet] from the others, and the weight a visit is compared with. */
@Composable
internal fun recognitionLines(pet: Pet): Pair<String, String> {
    val locale = LocalConfiguration.current.locales[0]
    val how = stringResource(
        // The server serves a photo only when the pet has reference faces.
        if (pet.photoUrl != null) R.string.pet_recognition_face else R.string.pet_recognition_weight,
    )
    val weight = pet.weightG?.let { formatWeightKg(it, locale) }
    val reference = when {
        weight == null -> stringResource(R.string.pet_reference_none)
        pet.weightLearned -> stringResource(R.string.pet_reference_learned, weight)
        else -> stringResource(R.string.pet_reference_entered, weight)
    }
    return how to reference
}

/**
 * The other pets, a tap away (SPEC.md §5.5): each with its avatar and name, the one shown filled
 * and checked, so the choice never rests on colour alone.
 */
@Composable
internal fun PetSwitcher(pets: List<Pet>, selected: Long, onSelect: (Long) -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    LazyRow(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), modifier = modifier) {
        itemsIndexed(pets, key = { _, pet -> pet.id }) { index, pet ->
            val isSelected = pet.id == selected
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
                    .clip(PkTheme.shapes.chip)
                    .selectable(selected = isSelected, onClick = { onSelect(pet.id) }, role = Role.RadioButton)
                    .background(if (isSelected) colors.accentContainer else colors.surface)
                    .border(1.dp, if (isSelected) colors.accent else colors.outline, PkTheme.shapes.chip)
                    .padding(start = PkTheme.spacing.xxs, end = PkTheme.spacing.m)
                    .testTag(PetsTestTags.switch(pet.id)),
            ) {
                PetAvatar(pet.name, pet.photoUrl, index, size = 36.dp)
                Spacer(Modifier.width(PkTheme.spacing.xs))
                if (isSelected) {
                    Icon(
                        PkIcons.Check,
                        contentDescription = null,
                        tint = colors.onAccentContainer,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(PkTheme.spacing.xxs))
                }
                Text(
                    pet.name,
                    style = PkTheme.typography.labelLarge,
                    color = if (isSelected) colors.onAccentContainer else colors.onSurface,
                    maxLines = 1,
                )
            }
        }
    }
}

/** The pet's avatar, name and how it is recognised. */
@Composable
internal fun PetProfile(page: PetPage, modifier: Modifier = Modifier) {
    val (how, reference) = recognitionLines(page.pet)
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        PetAvatar(page.pet.name, page.pet.photoUrl, page.colorIndex, size = 72.dp)
        Column(Modifier.padding(start = PkTheme.spacing.m).weight(1f)) {
            Text(
                page.pet.name,
                style = PkTheme.typography.headlineSmall,
                color = PkTheme.colors.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() },
            )
            Text(how, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
            Text(reference, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
        }
    }
}

/** Weight, visits today and last visit (SPEC.md §5.5), side by side, as tall as the tallest. */
@Composable
internal fun PetTiles(page: PetPage, today: LocalDate, zone: ZoneId, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val stats = page.activity?.stats
    val none = stringResource(R.string.pet_no_figure)
    val lastVisit = stats?.lastVisitAt
    val weight: @Composable (Modifier) -> Unit = { tile ->
        StatTile(
            label = stringResource(R.string.pet_weight),
            figure = stats?.medianWeightG?.let { formatWeightKg(it, locale) } ?: none,
            detail = stringResource(
                if (stats?.medianWeightG != null) R.string.pet_weight_median else R.string.pet_weight_none,
            ),
            modifier = tile,
        )
    }
    val visits: @Composable (Modifier) -> Unit = { tile ->
        StatTile(
            label = stringResource(R.string.pet_visits_today),
            figure = page.visitsToday?.toString() ?: none,
            detail = if (page.visitsToday == null) stringResource(R.string.pet_visits_today_stale) else null,
            modifier = tile,
        )
    }
    val last: @Composable (Modifier) -> Unit = { tile ->
        StatTile(
            label = stringResource(R.string.pet_last_visit),
            figure = lastVisit?.let { formatTimeOfDay(it.toKotlinInstant(), zone, locale) } ?: none,
            detail = if (lastVisit == null) {
                stringResource(R.string.pet_last_visit_none)
            } else {
                listOfNotNull(
                    dayLabel(lastVisit.atZone(zone).toLocalDate(), today, locale),
                    page.lastVisitDevice?.modelName,
                ).joinToString(" · ")
            },
            modifier = tile,
        )
    }
    val gap = PkTheme.spacing.xs
    // Three across on a phone; with a large font the last one takes a row of its own rather than
    // breaking its words.
    val minWidth = TILE_MIN_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f)
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val threeAcross = maxWidth >= minWidth * 3 + gap * 2
        Column(verticalArrangement = Arrangement.spacedBy(gap)) {
            Row(horizontalArrangement = Arrangement.spacedBy(gap), modifier = Modifier.height(IntrinsicSize.Min)) {
                val tile = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                weight(tile)
                visits(tile)
                if (threeAcross) last(tile)
            }
            if (!threeAcross) last(Modifier.fillMaxWidth())
        }
    }
}

/** The narrowest a tile gets at 100 % font scale; it grows with the font. */
private val TILE_MIN_WIDTH = 96.dp

/** A tile's figure shrinks to fit rather than wrap: three tiles share a phone's width. */
@Composable
private fun StatTile(label: String, figure: String, detail: String?, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
        modifier = modifier
            .clip(PkTheme.shapes.tile)
            .background(colors.surface)
            .padding(PkTheme.spacing.s)
            .semantics(mergeDescendants = true) {},
    ) {
        Text(label, style = PkTheme.typography.labelLarge, color = colors.onSurfaceMuted)
        BasicText(
            figure,
            style = PkTheme.typography.headlineSmall.copy(color = colors.onSurface),
            maxLines = 1,
            autoSize = TextAutoSize.StepBased(
                minFontSize = MIN_FIGURE_SIZE,
                maxFontSize = PkTheme.typography.headlineSmall.fontSize,
            ),
        )
        if (detail != null) {
            Text(detail, style = PkTheme.typography.labelMedium, color = colors.onSurfaceMuted)
        }
    }
}

private val MIN_FIGURE_SIZE = 14.sp

/**
 * The pet's recent visits, newest first; each opens History on its day, narrowed to the pet, and
 * the button below opens it on today.
 */
@Composable
internal fun RecentVisitsCard(
    page: PetPage,
    today: LocalDate,
    zone: ZoneId,
    onOpenHistory: (LocalDate?) -> Unit,
    modifier: Modifier = Modifier,
) {
    PkCard(modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.pet_recent_visits),
            style = PkTheme.typography.titleMedium,
            modifier = Modifier.semantics { heading() },
        )
        if (page.recentVisits.isEmpty()) {
            Text(
                stringResource(R.string.pet_recent_visits_empty),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            )
        }
        page.recentVisits.take(MAX_VISITS).forEach { visit ->
            VisitRow(visit, today, zone, onClick = { day -> onOpenHistory(day) })
        }
        PkButton(
            stringResource(R.string.pet_see_history),
            onClick = { onOpenHistory(null) },
            style = PkButtonStyle.Text,
            icon = PkIcons.History,
            modifier = Modifier.padding(top = PkTheme.spacing.xs),
        )
    }
}

private const val MAX_VISITS = 6

@Composable
private fun VisitRow(visit: PetVisit, today: LocalDate, zone: ZoneId, onClick: (LocalDate) -> Unit) {
    val colors = PkTheme.colors
    val locale = LocalConfiguration.current.locales[0]
    val item = visit.item
    val at = item.shownAt?.atZone(zone)
    val details = listOfNotNull(
        item.weightG?.let { formatWeightKg(it.toDouble(), locale) },
        item.durationSec?.let(::formatDurationMinSec),
    ).joinToString(" · ")
    val openLabel = stringResource(R.string.pet_visit_open)
    val day = at?.toLocalDate() ?: today
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PkTheme.spacing.xs)
            .clip(PkTheme.shapes.tile)
            .clickable(onClickLabel = openLabel, role = Role.Button) { onClick(day) }
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .padding(vertical = PkTheme.spacing.xxs)
            .testTag(PetsTestTags.visit(item.id)),
    ) {
        Column(Modifier.width(TIME_WIDTH * LocalDensity.current.fontScale.coerceAtLeast(1f))) {
            Text(
                item.shownAt?.let { formatTimeOfDay(it.toKotlinInstant(), zone, locale) }.orEmpty(),
                style = PkTheme.typography.labelLarge,
                color = colors.onSurface,
            )
            Text(dayLabel(day, today, locale), style = PkTheme.typography.labelMedium, color = colors.onSurfaceMuted)
        }
        Column(Modifier.padding(start = PkTheme.spacing.s).weight(1f)) {
            Text(
                visit.device?.modelName ?: stringResource(R.string.pet_unknown_device),
                style = PkTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (details.isNotEmpty()) {
                Text(details, style = PkTheme.typography.labelMedium, color = colors.onSurfaceMuted)
            }
        }
        Icon(
            PkIcons.ChevronRight,
            contentDescription = null,
            tint = colors.onSurfaceMuted,
            modifier = Modifier.size(20.dp),
        )
    }
}

private val TIME_WIDTH = 76.dp

/** One pet of the list: avatar, name, weight and today's visits, how it is recognised. */
@Composable
internal fun PetListCard(
    pet: Pet,
    colorIndex: Int,
    visitsToday: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
    val visits = pluralStringResource(R.plurals.pets_visits_today, visitsToday, visitsToday)
    val weight = pet.weightG?.let { formatWeightKg(it, locale) }
    val (how, _) = recognitionLines(pet)
    PkCard(modifier.fillMaxWidth().testTag(PetsTestTags.card(pet.id)), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PetAvatar(pet.name, pet.photoUrl, colorIndex, size = 56.dp)
            Column(Modifier.padding(horizontal = PkTheme.spacing.m).weight(1f)) {
                Text(
                    pet.name,
                    style = PkTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (weight != null) stringResource(R.string.pets_line, weight, visits) else visits,
                    style = PkTheme.typography.bodyMedium,
                    color = PkTheme.colors.onSurfaceMuted,
                )
                Text(how, style = PkTheme.typography.labelMedium, color = PkTheme.colors.onSurfaceMuted)
            }
            Icon(PkIcons.ChevronRight, contentDescription = null, tint = PkTheme.colors.onSurfaceMuted)
        }
    }
}
