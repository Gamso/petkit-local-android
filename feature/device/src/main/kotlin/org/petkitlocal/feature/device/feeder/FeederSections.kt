package org.petkitlocal.feature.device.feeder

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.text.NumberFormat
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.feeder.FeederDayFilter
import org.petkitlocal.core.domain.feeder.FeederPage
import org.petkitlocal.core.domain.feeder.MealState
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.litter.CardTitle

/**
 * The next meal, what was served today, and feed now (SPEC.md §5.6): per hopper on a Dual-Hopper,
 * the feeder's own portion on a single one. Feed now is never greyed out for want of portions:
 * pressed with none, it says so under the counts.
 */
@Composable
internal fun NextMealCard(
    state: FeederUiState,
    page: FeederPage,
    callbacks: FeederCallbacks,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    PkCard(modifier.fillMaxWidth()) {
        val next = page.nextMeal
        CardTitle(stringResource(if (next?.oneOff == true) R.string.feeder_next_one_off else R.string.feeder_next_meal))
        if (next != null) {
            Text(
                whenText(next.at, state.today, state.zone),
                style = PkTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = PkTheme.spacing.xxs),
            )
            Text(portionsText(next.portions), style = PkTheme.typography.bodyMedium, color = colors.onSurfaceMuted)
        } else {
            Text(
                stringResource(R.string.feeder_no_next_meal),
                style = PkTheme.typography.bodyLarge,
                modifier = Modifier.padding(top = PkTheme.spacing.xxs),
            )
        }
        if (page.plan?.active == false) {
            Text(
                stringResource(R.string.feeder_plan_paused_note),
                style = PkTheme.typography.bodyMedium,
                color = colors.warning,
                modifier = Modifier.padding(top = PkTheme.spacing.xxs),
            )
        }
        ServedToday(page)
        HorizontalDivider(color = colors.outline, modifier = Modifier.padding(vertical = PkTheme.spacing.s))
        FeedNow(state, page, callbacks)
    }
}

@Composable
private fun ServedToday(page: FeederPage) {
    val locale = locale()
    val meals = pluralStringResource(R.plurals.feeder_meals_today, page.mealsToday, page.mealsToday)
    val dispensed = page.dispensed?.let {
        "${NumberFormat.getNumberInstance(locale).format(it.value)} ${it.unit.orEmpty()}".trim()
    }
    Text(
        stringResource(R.string.feeder_served_today, listOfNotNull(dispensed, meals).joinToString(" · ")),
        style = PkTheme.typography.labelLarge,
        color = PkTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = PkTheme.spacing.xs),
    )
}

@Composable
private fun FeedNow(state: FeederUiState, page: FeederPage, callbacks: FeederCallbacks) {
    val hoppers = page.manualFeed.hoppers
    if (hoppers.isEmpty()) {
        Text(
            stringResource(R.string.feeder_feed_now_single),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.onSurfaceMuted,
        )
    }
    hoppers.forEachIndexed { index, hopper ->
        val label = stringResource(R.string.feeder_hopper, hopper.hopper)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(label, style = PkTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            PortionStepper(
                label = label,
                value = state.portions.getOrElse(index) { hopper.portions },
                min = hopper.min,
                max = hopper.max,
                onChange = { callbacks.onPortions(index, it) },
            )
        }
    }
    if (state.nothingChosen) {
        Text(
            stringResource(R.string.feeder_nothing_chosen),
            style = PkTheme.typography.bodyMedium,
            color = PkTheme.colors.warning,
            modifier = Modifier.padding(top = PkTheme.spacing.xxs),
        )
    }
    PkButton(
        stringResource(R.string.feeder_feed_now),
        onClick = callbacks.onFeedNow,
        icon = PkIcons.Feed,
        enabled = !state.sending(FeederViewModel.FEED_KEY),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PkTheme.spacing.s)
            .testTag(FeederTestTags.FEED_NOW),
    )
}

/** Today's meals with their state, the active/paused switch, and the way to the weekly plan. */
@Composable
internal fun PlanCard(
    state: FeederUiState,
    page: FeederPage,
    callbacks: FeederCallbacks,
    modifier: Modifier = Modifier,
) {
    val plan = page.plan
    PkCard(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(stringResource(R.string.feeder_plan), Modifier.weight(1f))
            if (plan !=
                null
            ) {
                ActiveSwitch(plan.active, state.sending(FeederViewModel.PLAN_ACTIVE_KEY), callbacks.onPlanActive)
            }
        }
        when {
            plan == null && state.refreshing -> Muted(stringResource(R.string.feeder_plan_loading))

            plan == null -> {
                Muted(stringResource(R.string.feeder_plan_failed))
                PkButton(
                    stringResource(R.string.feeder_retry),
                    onClick = callbacks.onRefresh,
                    style = PkButtonStyle.Text,
                )
            }

            page.today.isEmpty() -> Muted(stringResource(R.string.feeder_no_meal_today))

            else -> {
                Text(
                    stringResource(R.string.feeder_plan_today),
                    style = PkTheme.typography.labelLarge,
                    color = PkTheme.colors.onSurfaceMuted,
                    modifier = Modifier.padding(top = PkTheme.spacing.xs),
                )
                page.today.forEach { planned ->
                    MealRow(clockText(planned.meal.time, locale()), portionsText(planned.meal.portions), planned.state)
                }
            }
        }
        PkButton(
            stringResource(R.string.feeder_edit_plan),
            onClick = callbacks.onEditPlan,
            style = PkButtonStyle.Secondary,
            icon = PkIcons.Edit,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PkTheme.spacing.s)
                .testTag(FeederTestTags.EDIT_PLAN),
        )
    }
}

/** The plan's switch, with its state in words: "Active" or "Paused". */
@Composable
private fun ActiveSwitch(active: Boolean, pending: Boolean, onChange: (Boolean) -> Unit) {
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(PkTheme.shapes.chip)
            .toggleable(value = active, enabled = !pending, role = Role.Switch, onValueChange = onChange)
            .padding(start = PkTheme.spacing.s)
            .testTag(FeederTestTags.PLAN_ACTIVE),
    ) {
        Text(
            stringResource(if (active) R.string.feeder_plan_active else R.string.feeder_plan_paused),
            style = PkTheme.typography.labelLarge,
            color = if (active) colors.accent else colors.warning,
        )
        Switch(
            checked = active,
            onCheckedChange = null,
            enabled = !pending,
            colors = SwitchDefaults.colors(checkedTrackColor = colors.accent, checkedThumbColor = colors.onAccent),
            modifier = Modifier.padding(start = PkTheme.spacing.xs),
        )
    }
}

@Composable
private fun MealRow(time: String, portions: String, state: MealState) {
    val colors = PkTheme.colors
    val (container, content) = when (state) {
        MealState.Served -> colors.accentContainer to colors.onAccentContainer
        MealState.Skipped, MealState.Paused -> colors.warningContainer to colors.onWarningContainer
        MealState.Upcoming -> colors.surfaceMuted to colors.onSurface
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = PkTheme.spacing.xs)
            .semantics(mergeDescendants = true) {},
    ) {
        Column(Modifier.weight(1f)) {
            Text(time, style = PkTheme.typography.titleMedium)
            Text(portions, style = PkTheme.typography.bodyMedium, color = colors.onSurfaceMuted)
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(PkTheme.shapes.chip)
                .background(container)
                .padding(horizontal = PkTheme.spacing.s, vertical = PkTheme.spacing.xxs),
        ) {
            if (state == MealState.Served) {
                Icon(PkIcons.Check, contentDescription = null, tint = content, modifier = Modifier.size(14.dp))
            }
            Text(
                mealStateText(state),
                style = PkTheme.typography.labelMedium,
                color = content,
                modifier = Modifier.padding(start = if (state == MealState.Served) 4.dp else 0.dp),
            )
        }
    }
}

/** Pending one-off feeds, each with its cancel button, and the way to add one. */
@Composable
internal fun OneOffCard(
    state: FeederUiState,
    page: FeederPage,
    callbacks: FeederCallbacks,
    modifier: Modifier = Modifier,
) {
    PkCard(modifier.fillMaxWidth()) {
        CardTitle(stringResource(R.string.feeder_one_off))
        val feeds = page.deferredFeeds.orEmpty()
        if (feeds.isEmpty()) Muted(stringResource(R.string.feeder_no_one_off))
        Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs)) {
            feeds.forEach { feed ->
                val time = feed.fireAt?.let { whenText(it, state.today, state.zone) } ?: "–"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().testTag(FeederTestTags.oneOff(feed.id)),
                ) {
                    Column(Modifier.weight(1f).padding(top = PkTheme.spacing.xs)) {
                        Text(time, style = PkTheme.typography.titleMedium)
                        Text(
                            portionsText(feed.portions),
                            style = PkTheme.typography.bodyMedium,
                            color = PkTheme.colors.onSurfaceMuted,
                        )
                    }
                    CancelButton(stringResource(R.string.feeder_cancel_feed, time)) { callbacks.onCancelFeed(feed.id) }
                }
            }
        }
        PkButton(
            stringResource(R.string.feeder_add_one_off),
            onClick = callbacks.onAddFeed,
            style = PkButtonStyle.Secondary,
            icon = PkIcons.Add,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = PkTheme.spacing.s)
                .testTag(FeederTestTags.ADD_ONE_OFF),
        )
    }
}

@Composable
private fun CancelButton(description: String, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = PkTheme.colors.surfaceMuted,
        contentColor = PkTheme.colors.onSurface,
        modifier = Modifier.size(PkTheme.spacing.minTouchTarget),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(PkIcons.Close, contentDescription = description, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun Muted(text: String) {
    Text(
        text,
        style = PkTheme.typography.bodyMedium,
        color = PkTheme.colors.onSurfaceMuted,
        modifier = Modifier.padding(top = PkTheme.spacing.xs),
    )
}

/** Test tags of the feeder page. */
object FeederTestTags {
    const val FEED_NOW = "feeder:feed_now"
    const val PLAN_ACTIVE = "feeder:plan_active"
    const val EDIT_PLAN = "feeder:edit_plan"
    const val ADD_ONE_OFF = "feeder:add_one_off"
    const val PLAN_EDITOR = "feeder:plan_editor"
    const val FEED_FORM = "feeder:feed_form"
    const val SAVE = "feeder:save"

    /** The day's card, and its chips. */
    const val DAY = "feeder:day"

    /** A camera feeder's meals eaten, at the top of the day's card, and their chart. */
    const val EATING = "feeder:eating"
    const val EATING_CHART = "feeder:eating:chart"

    fun dayFilter(filter: FeederDayFilter): String = "feeder:day:${filter.name.lowercase()}"

    fun oneOff(id: String): String = "feeder:one_off:$id"

    fun stepper(label: String): String = "feeder:stepper:$label"
}
