package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.litter.LitterBoxPage
import org.petkitlocal.core.ui.formatDurationMinSec
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.StatCompareTile
import org.petkitlocal.feature.device.common.rememberDayChangeFormatter

/** A card's title. */
@Composable
internal fun CardTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = PkTheme.typography.titleMedium, modifier = modifier.semantics { heading() })
}

/**
 * Today's visits and average visit, each with the change since yesterday (SPEC.md §5.3). A day
 * with no visit yesterday has nothing to compare with: zero there is as likely a box that was off.
 */
@Composable
internal fun TodayCard(page: LitterBoxPage, modifier: Modifier = Modifier) {
    val changes = rememberDayChangeFormatter()
    PkCard(modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.litter_today))
        Row(
            horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier
                .padding(top = PkTheme.spacing.s)
                .height(IntrinsicSize.Min),
        ) {
            StatCompareTile(
                label = stringResource(R.string.litter_visits),
                figure = page.today.visits.toString(),
                change = changes.count(page.today.visits, page.yesterday.visits.takeIf { it > 0 }),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
            val average = page.today.averageDurationSec
            StatCompareTile(
                label = stringResource(R.string.litter_average_visit),
                figure = average?.let(::formatDurationMinSec) ?: stringResource(R.string.litter_no_average),
                change = average?.let { changes.duration(it, page.yesterday.averageDurationSec) },
                spokenFigure = average?.let(changes::spokenDuration) ?: stringResource(R.string.litter_no_average),
                modifier = Modifier.weight(1f).fillMaxHeight(),
            )
        }
    }
}
