package org.petkitlocal.feature.device.feeder

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.DayOfWeek
import java.time.temporal.WeekFields
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.feeder.PlanProblem
import org.petkitlocal.core.domain.model.FeedingGroup
import org.petkitlocal.core.domain.model.Meal
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.errorText

/** The weekly plan's editor, full screen (SPEC.md §5.6: an edit sheet for the weekly plan). */
@Composable
internal fun PlanEditorDialog(
    draft: PlanDraft,
    saving: Boolean,
    onEdit: (PlanEdit) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        PlanEditor(draft, saving, onEdit, onSave, onClose)
    }
}

/**
 * The editor itself, outside any window (what tests render): one card per group of days, each
 * with its days and its meals (time, portions, remove), and Save, which is never greyed out for
 * a mistake: a plan with something to fix says so where it is, once Save was pressed.
 */
@Composable
internal fun PlanEditor(
    draft: PlanDraft,
    saving: Boolean,
    onEdit: (PlanEdit) -> Unit,
    onSave: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var picking by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    Column(
        modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag(FeederTestTags.PLAN_EDITOR),
    ) {
        EditorBar(saving, onSave, onClose)
        draft.refusal?.let { error ->
            Text(
                errorText(error),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.warning,
                modifier = Modifier.padding(horizontal = PkTheme.spacing.gutter),
            )
        }
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
            contentPadding = PaddingValues(PkTheme.spacing.gutter),
            modifier = Modifier.weight(1f),
        ) {
            if (draft.groups.isEmpty()) {
                item(key = "empty") { EmptyPlan() }
            }
            itemsIndexed(draft.groups) { index, group ->
                GroupCard(index, group, draft, onEdit, onPickTime = { meal -> picking = index to meal })
            }
            item(key = "add") {
                PkButton(
                    stringResource(R.string.feeder_editor_add_group),
                    onClick = { onEdit(PlanEdit.AddGroup) },
                    style = PkButtonStyle.Secondary,
                    icon = PkIcons.Add,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
    picking?.let { (group, meal) ->
        val current = draft.groups.getOrNull(group)?.meals?.getOrNull(meal)
        if (current != null) {
            TimePickerSheet(current.time, onPick = { onEdit(PlanEdit.MealTime(group, meal, it)) }, onDismiss = {
                picking =
                    null
            })
        }
    }
}

/** Close without saving, the title, and Save. */
@Composable
private fun EditorBar(saving: Boolean, onSave: () -> Unit, onClose: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(PkTheme.spacing.gutter),
    ) {
        PkIconButton(PkIcons.Close, stringResource(R.string.feeder_editor_close), onClose)
        Text(
            stringResource(R.string.feeder_editor_title),
            style = PkTheme.typography.titleLarge,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = PkTheme.spacing.s)
                .semantics { heading() },
        )
        PkButton(
            stringResource(R.string.feeder_editor_save),
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.testTag(FeederTestTags.SAVE),
        )
    }
}

@Composable
private fun EmptyPlan() {
    Text(
        stringResource(R.string.feeder_editor_empty),
        style = PkTheme.typography.bodyLarge,
        color = PkTheme.colors.onSurfaceMuted,
    )
}

@Composable
private fun GroupCard(
    index: Int,
    group: FeedingGroup,
    draft: PlanDraft,
    onEdit: (PlanEdit) -> Unit,
    onPickTime: (meal: Int) -> Unit,
) {
    val problems = draft.shownProblems.filter { it.group == index }
    PkCard(Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.feeder_editor_days),
            style = PkTheme.typography.labelLarge,
            color = PkTheme.colors.onSurfaceMuted,
        )
        DayChips(group.days) { onEdit(PlanEdit.ToggleDay(index, it)) }
        problems.filter { it is PlanProblem.NoDays || it is PlanProblem.DayTwice || it is PlanProblem.TooManyMeals }
            .forEach { ProblemLine(problemText(it)) }
        group.meals.forEachIndexed { mealIndex, meal ->
            MealEditor(
                meal = meal,
                problem = problems.firstOrNull {
                    (it is PlanProblem.NothingServed && it.meal == mealIndex) ||
                        (it is PlanProblem.SameTime && it.time == meal.time)
                },
                onPickTime = { onPickTime(mealIndex) },
                onPortions = { hopper, count -> onEdit(PlanEdit.MealPortions(index, mealIndex, hopper, count)) },
                onRemove = { onEdit(PlanEdit.RemoveMeal(index, mealIndex)) },
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(top = PkTheme.spacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            PkButton(
                stringResource(R.string.feeder_editor_add_meal),
                onClick = { onEdit(PlanEdit.AddMeal(index)) },
                style = PkButtonStyle.Text,
                icon = PkIcons.Add,
            )
            PkButton(
                stringResource(R.string.feeder_editor_remove_group),
                onClick = { onEdit(PlanEdit.RemoveGroup(index)) },
                style = PkButtonStyle.Text,
            )
        }
    }
}

/** The seven days, the week starting where the locale starts it; a chosen day is filled. */
@OptIn(ExperimentalLayoutApi::class) // FlowRow
@Composable
private fun DayChips(days: Set<DayOfWeek>, onToggle: (DayOfWeek) -> Unit) {
    val locale = locale()
    val first = WeekFields.of(locale).firstDayOfWeek
    val colors = PkTheme.colors
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xxs),
        modifier = Modifier.padding(top = PkTheme.spacing.xs),
    ) {
        List(DAYS_PER_WEEK) { first.plus(it.toLong()) }.forEach { day ->
            val on = day in days
            val name = dayName(day, locale)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(PkTheme.spacing.minTouchTarget)
                    .clip(CircleShape)
                    .toggleable(value = on, role = Role.Checkbox, onValueChange = { onToggle(day) })
                    .background(if (on) colors.accent else colors.surface)
                    .border(1.dp, if (on) colors.accent else colors.outline, CircleShape)
                    .semantics { contentDescription = name },
            ) {
                Text(
                    dayName(day, locale, short = true).take(SHORT_DAY),
                    style = PkTheme.typography.labelLarge,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Normal,
                    color = if (on) colors.onAccent else colors.onSurface,
                )
            }
        }
    }
}

@Composable
private fun MealEditor(
    meal: Meal,
    problem: PlanProblem?,
    onPickTime: () -> Unit,
    onPortions: (hopper: Int, count: Int) -> Unit,
    onRemove: () -> Unit,
) {
    val time = clockText(meal.time, locale())
    val timeDescription = stringResource(R.string.feeder_editor_meal_time, time)
    Column(Modifier.fillMaxWidth().padding(top = PkTheme.spacing.s)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PkButton(
                time,
                onClick = onPickTime,
                style = PkButtonStyle.Secondary,
                icon = PkIcons.Clock,
                modifier = Modifier.semantics { contentDescription = timeDescription },
            )
            Box(Modifier.weight(1f))
            PkIconButton(PkIcons.Close, stringResource(R.string.feeder_editor_remove_meal, time), onRemove)
        }
        when (val portions = meal.portions) {
            is Portions.Single -> PortionLine(stringResource(R.string.feeder_portions_label), portions.amount) {
                onPortions(1, it)
            }

            is Portions.Dual -> {
                PortionLine(stringResource(R.string.feeder_hopper, 1), portions.hopper1) { onPortions(1, it) }
                PortionLine(stringResource(R.string.feeder_hopper, 2), portions.hopper2) { onPortions(2, it) }
            }
        }
        problem?.let { ProblemLine(problemText(it)) }
    }
}

@Composable
private fun PortionLine(label: String, value: Int, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            style = PkTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        PortionStepper(label, value, 0, Portions.MAX, onChange)
    }
}

@Composable
internal fun ProblemLine(text: String) {
    Text(
        text,
        style = PkTheme.typography.bodyMedium,
        color = PkTheme.colors.warning,
        modifier = Modifier.padding(top = PkTheme.spacing.xxs),
    )
}

private const val DAYS_PER_WEEK = 7
private const val SHORT_DAY = 3
