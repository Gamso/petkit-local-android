package org.petkitlocal.feature.device.litter

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.domain.litter.LitterBoxPage
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DayChartCard
import org.petkitlocal.feature.device.common.DayDot

/**
 * The litter box's 24-hour chart (docs/DESIGN.md, DayScatterChart): one dot per visit, at its time
 * across and its duration up, in its pet's colour, with a single-select pet filter. TalkBack reads
 * the count; the activity list below says the same visits in words.
 */
@Composable
internal fun VisitsChartCard(
    page: LitterBoxPage,
    petFilter: Long?,
    onPetFilter: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    DayChartCard(
        title = stringResource(R.string.litter_chart_title),
        emptyText = stringResource(R.string.litter_chart_empty),
        description = R.plurals.litter_chart_description,
        dots = page.visits.map { DayDot(it.minuteOfDay, it.durationSec, it.petId) },
        pets = page.chartPets,
        colorIndexOf = page::colorIndexOf,
        petFilter = petFilter,
        onPetFilter = onPetFilter,
        chartTag = LitterTestTags.CHART,
        modifier = modifier,
    )
}

/** Test tags of the litter box page. */
object LitterTestTags {
    const val CHART = "litter:chart"
}
