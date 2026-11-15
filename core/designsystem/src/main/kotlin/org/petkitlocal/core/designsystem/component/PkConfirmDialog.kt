package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.window.Dialog
import org.petkitlocal.core.designsystem.R
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * Asks before something that cannot be undone. The message says what will happen; the confirm
 * button is danger-coloured and names the action ("Empty the bin"), never just "OK".
 */
@Composable
fun PkConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = stringResource(R.string.pk_cancel),
) {
    Dialog(onDismissRequest = onDismiss) {
        PkConfirmDialogContent(title, message, confirmLabel, onConfirm, onDismiss, dismissLabel = dismissLabel)
    }
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
                PkButton(confirmLabel, onClick = onConfirm, style = PkButtonStyle.Danger)
            }
        }
    }
}
