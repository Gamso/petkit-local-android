package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.R
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/*
 * Every base component in its typical states, with translated copy. Debug-only: the previews
 * below and the Roborazzi tests (src/test) render these; the release build never sees them.
 */

@Composable
fun PkButtonSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
        PkButton(stringResource(R.string.sample_clean_now), onClick = {}, icon = PkIcons.Clean)
        PkButton(stringResource(R.string.sample_feeding_plan), onClick = {}, style = PkButtonStyle.Secondary)
        PkButton(stringResource(R.string.sample_sign_out), onClick = {}, style = PkButtonStyle.Danger)
        PkButton(stringResource(R.string.sample_cancel), onClick = {}, style = PkButtonStyle.Text)
        PkButton(stringResource(R.string.sample_unavailable), onClick = {}, enabled = false)
    }
}

@Composable
fun PkIconButtonSamples() {
    Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
        PkBackButton(onClick = {})
        PkIconButton(PkIcons.History, stringResource(R.string.sample_action_history), onClick = {})
        PkIconButton(PkIcons.Settings, stringResource(R.string.sample_settings), onClick = {})
    }
}

@Composable
fun PkCardSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap)) {
        PkCard(Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.sample_today), style = PkTheme.typography.titleMedium)
            Text("6", style = PkTheme.typography.headlineLarge)
            Text(
                stringResource(R.string.sample_visits_compare),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
            )
        }
        PkCard(Modifier.fillMaxWidth(), onClick = {}) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.sample_device_name),
                    style = PkTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Icon(PkIcons.ChevronRight, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
fun PkChipSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.s)) {
        Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
            PkFilterChip(stringResource(R.string.sample_filter_all), selected = true, onClick = {})
            PkFilterChip(stringResource(R.string.sample_filter_visits), selected = false, onClick = {})
            PkFilterChip(stringResource(R.string.sample_filter_meals), selected = false, onClick = {})
        }
        PkModeChip(stringResource(R.string.sample_mode_auto_clean), on = true, onToggle = {})
        PkModeChip(stringResource(R.string.sample_mode_deodorize), on = false)
    }
}

@Composable
fun PkStateViewSamples() {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m)) {
        PkOfflineBanner()
        PkLoadingView(cards = 2)
        PkEmptyView(stringResource(R.string.sample_empty))
        PkErrorView(stringResource(R.string.sample_error), onRetry = {})
    }
}

@Composable
fun PkActionSheetSamples() {
    PkActionSheet(
        actions = listOf(
            PkSheetAction("clean", stringResource(R.string.sample_action_clean), PkIcons.Clean, primary = true),
            PkSheetAction("level", stringResource(R.string.sample_action_level), PkIcons.Level),
            PkSheetAction("deodorize", stringResource(R.string.sample_action_deodorize), PkIcons.Deodorize),
            PkSheetAction("history", stringResource(R.string.sample_action_history), PkIcons.History),
            PkSheetAction("later", stringResource(R.string.sample_action_later), PkIcons.Clock),
        ),
        onAction = {},
    )
}

@Composable
fun PkConfirmDialogSamples() {
    PkConfirmDialogContent(
        title = stringResource(R.string.sample_confirm_title),
        message = stringResource(R.string.sample_confirm_message),
        confirmLabel = stringResource(R.string.sample_confirm_action),
        onConfirm = {},
        onDismiss = {},
    )
}

@PkPreviews
@Composable
private fun PkButtonPreview() = PkPreviewSurface { PkButtonSamples() }

@PkPreviews
@Composable
private fun PkCardPreview() = PkPreviewSurface { PkCardSamples() }

@PkPreviews
@Composable
private fun PkChipPreview() = PkPreviewSurface { PkChipSamples() }

@PkPreviews
@Composable
private fun PkStateViewsPreview() = PkPreviewSurface { PkStateViewSamples() }

@PkPreviews
@Composable
private fun PkActionSheetPreview() = PkPreviewSurface { PkActionSheetSamples() }

@PkPreviews
@Composable
private fun PkConfirmDialogPreview() = PkPreviewSurface { PkConfirmDialogSamples() }
