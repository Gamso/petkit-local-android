package org.petkitlocal.feature.device.feeder

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.feeder.FeedProblem
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.errorText

/** Adding a one-off feed: its day, its time and its portions (SPEC.md §5.6, "later"). */
@Composable
internal fun FeedFormDialog(
    draft: FeedDraft,
    today: LocalDate,
    saving: Boolean,
    onChange: (FeedDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        FeedForm(draft, today, saving, onChange, onSave, onDismiss)
    }
}

/** The form's card, outside any window: what tests render. */
@Composable
internal fun FeedForm(
    draft: FeedDraft,
    today: LocalDate,
    saving: Boolean,
    onChange: (FeedDraft) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pickingDay by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    val locale = locale()
    Surface(
        shape = PkTheme.shapes.dialog,
        color = PkTheme.colors.surface,
        modifier = modifier.testTag(FeederTestTags.FEED_FORM),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier.padding(PkTheme.spacing.xl),
        ) {
            Text(stringResource(R.string.feeder_feed_title), style = PkTheme.typography.titleLarge)
            FieldRow(stringResource(R.string.feeder_feed_day)) {
                PkButton(
                    DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale).format(draft.date),
                    onClick = { pickingDay = true },
                    style = PkButtonStyle.Secondary,
                    icon = PkIcons.Calendar,
                )
            }
            FieldRow(stringResource(R.string.feeder_feed_time)) {
                PkButton(
                    clockText(draft.time, locale),
                    onClick = { pickingTime = true },
                    style = PkButtonStyle.Secondary,
                    icon = PkIcons.Clock,
                )
            }
            if (FeedProblem.InThePast in draft.shownProblems) ProblemLine(stringResource(R.string.feeder_problem_past))
            PortionFields(draft, onChange)
            if (FeedProblem.NothingServed in
                draft.shownProblems
            ) {
                ProblemLine(stringResource(R.string.feeder_nothing_chosen))
            }
            draft.refusal?.let { error ->
                ProblemLine(errorText(error))
            }
            FormButtons(saving, onSave, onDismiss)
        }
    }
    if (pickingDay) {
        DayPickerSheet(draft.date, from = today, onPick = { onChange(draft.copy(date = it)) }, onDismiss = {
            pickingDay =
                false
        })
    }
    if (pickingTime) {
        TimePickerSheet(draft.time, onPick = { onChange(draft.copy(time = it)) }, onDismiss = { pickingTime = false })
    }
}

/** Cancel, and Add: never greyed out for a mistake, only while the feed is being sent. */
@Composable
private fun FormButtons(saving: Boolean, onSave: () -> Unit, onDismiss: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PkButton(stringResource(R.string.feeder_cancel), onClick = onDismiss, style = PkButtonStyle.Text)
        PkButton(
            stringResource(R.string.feeder_feed_add),
            onClick = onSave,
            enabled = !saving,
            modifier = Modifier.testTag(FeederTestTags.SAVE),
        )
    }
}

@Composable
private fun PortionFields(draft: FeedDraft, onChange: (FeedDraft) -> Unit) {
    when (val portions = draft.portions) {
        is Portions.Single -> FieldRow(stringResource(R.string.feeder_portions_label)) {
            val label = stringResource(R.string.feeder_portions_label)
            PortionStepper(label, portions.amount, 0, Portions.MAX, {
                onChange(draft.copy(portions = portions.with(1, it)))
            })
        }

        is Portions.Dual -> listOf(1 to portions.hopper1, 2 to portions.hopper2).forEach { (hopper, count) ->
            val label = stringResource(R.string.feeder_hopper, hopper)
            FieldRow(label) {
                PortionStepper(label, count, 0, Portions.MAX, {
                    onChange(draft.copy(portions = portions.with(hopper, it)))
                })
            }
        }
    }
}

@Composable
private fun FieldRow(label: String, content: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(label, style = PkTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        content()
    }
}
