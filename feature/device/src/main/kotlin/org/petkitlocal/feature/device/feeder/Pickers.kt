package org.petkitlocal.feature.device.feeder

import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.feature.device.R

/** Material 3's clock face, in the phone's 12- or 24-hour format. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerSheet(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val state = rememberTimePickerState(initial.hour, initial.minute, DateFormat.is24HourFormat(LocalContext.current))
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = PkTheme.shapes.dialog, color = PkTheme.colors.surface) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(PkTheme.spacing.xl),
            ) {
                TimePicker(state)
                PickerButtons(onDismiss) { onPick(LocalTime.of(state.hour, state.minute)) }
            }
        }
    }
}

/** Material 3's calendar, from [from] on: a one-off feed cannot be in the past. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DayPickerSheet(initial: LocalDate, from: LocalDate, onPick: (LocalDate) -> Unit, onDismiss: () -> Unit) {
    val first = from.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= first

            override fun isSelectableYear(year: Int): Boolean = year >= from.year
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            PkButton(stringResource(R.string.feeder_ok), style = PkButtonStyle.Text, onClick = {
                state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                onDismiss()
            })
        },
        dismissButton = {
            PkButton(stringResource(R.string.feeder_cancel), onClick = onDismiss, style = PkButtonStyle.Text)
        },
    ) {
        DatePicker(state)
    }
}

@Composable
private fun PickerButtons(onDismiss: () -> Unit, onConfirm: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs, Alignment.End),
        modifier = Modifier.fillMaxWidth(),
    ) {
        PkButton(stringResource(R.string.feeder_cancel), onClick = onDismiss, style = PkButtonStyle.Text)
        PkButton(stringResource(R.string.feeder_ok), onClick = {
            onConfirm()
            onDismiss()
        }, style = PkButtonStyle.Text)
    }
}
