package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import kotlinx.coroutines.delay
import org.petkitlocal.core.designsystem.R
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * Asks before something that cannot be undone. The message says what will happen; the confirm
 * button is danger-coloured and names the action ("Empty the bin"), never just "OK".
 *
 * With [armDelayMillis], the confirm button ignores taps for that long after the dialog opens:
 * a second tap meant for the button that opened it (a double tap, or a tap repeated because the
 * first seemed lost) would otherwise land on the confirm button, which can sit where that button
 * was, and confirm what the user never read. It looks the same meanwhile; its test tag says when
 * it takes taps ([PkConfirmTestTags]).
 */
@Composable
fun PkConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.pk_cancel),
    armDelayMillis: Long = 0L,
) {
    var armed by remember { mutableStateOf(armDelayMillis <= 0L) }
    if (!armed) {
        LaunchedEffect(Unit) {
            delay(armDelayMillis)
            armed = true
        }
    }
    Dialog(onDismissRequest = onDismiss) {
        PkConfirmDialogContent(
            title,
            message,
            confirmLabel,
            onConfirm = { if (armed) onConfirm() },
            onDismiss = onDismiss,
            dismissLabel = dismissLabel,
            confirmArmed = armed,
        )
    }
}

/** Test tags of the confirm button: [CONFIRM] once it takes taps, [CONFIRM_ARMING] before. */
object PkConfirmTestTags {
    const val CONFIRM = "pk:confirm"
    const val CONFIRM_ARMING = "pk:confirm:arming"
}

/** The dialog's card, outside any window: what screenshot tests and previews render. */
@Composable
fun PkConfirmDialogContent(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    dismissLabel: String = stringResource(R.string.pk_cancel),
    confirmArmed: Boolean = true,
) {
    Surface(
        shape = PkTheme.shapes.dialog,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s),
            modifier = Modifier.padding(PkTheme.spacing.xl),
        ) {
            Text(title, style = PkTheme.typography.titleLarge)
            Text(message, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
            Row(
                horizontalArrangement = Arrangement.spacedBy(
                    PkTheme.spacing.xs,
                    alignment = androidx.compose.ui.Alignment.End,
                ),
                modifier = Modifier.fillMaxWidth().padding(top = PkTheme.spacing.s),
            ) {
                PkButton(dismissLabel, onClick = onDismiss, style = PkButtonStyle.Text)
                PkButton(
                    confirmLabel,
                    onClick = onConfirm,
                    style = PkButtonStyle.Danger,
                    modifier = Modifier.testTag(
                        if (confirmArmed) PkConfirmTestTags.CONFIRM else PkConfirmTestTags.CONFIRM_ARMING,
                    ),
                )
            }
        }
    }
}
