package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.R
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/** One button of an [PkActionSheet]. [key] is what [PkActionSheet]'s callback receives. */
data class PkSheetAction(val key: String, val label: String, val icon: ImageVector, val primary: Boolean = false)

private const val ACTIONS_PER_ROW = 4

/**
 * The bottom action sheet of a device page: a pull handle, then the device's actions four per
 * row, the primary one filled. It is a surface the screen anchors at its bottom, not a modal.
 */
@Composable
fun PkActionSheet(actions: List<PkSheetAction>, onAction: (String) -> Unit, modifier: Modifier = Modifier) {
    val title = stringResource(R.string.pk_action_sheet)
    Surface(
        shape = PkTheme.shapes.sheet,
        color = PkTheme.colors.surface,
        contentColor = PkTheme.colors.onSurface,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .semantics { paneTitle = title },
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = PkTheme.spacing.m, end = PkTheme.spacing.m, bottom = PkTheme.spacing.l),
        ) {
            Box(
                Modifier
                    .padding(vertical = PkTheme.spacing.s)
                    .size(width = 36.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(PkTheme.colors.outline),
            )
            actions.chunked(ACTIONS_PER_ROW).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = PkTheme.spacing.xs)) {
                    row.forEach { action ->
                        SheetButton(action, onClick = { onAction(action.key) }, modifier = Modifier.weight(1f))
                    }
                    // Keep a short last row aligned with the columns above it.
                    repeat(ACTIONS_PER_ROW - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun SheetButton(action: PkSheetAction, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clip(PkTheme.shapes.tile)
            .clickable(onClick = onClick, role = Role.Button)
            .padding(vertical = PkTheme.spacing.xs),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(56.dp)
                .clip(PkTheme.shapes.tile)
                .background(if (action.primary) colors.accent else colors.surfaceMuted),
        ) {
            Icon(
                action.icon,
                contentDescription = null,
                tint = if (action.primary) colors.onAccent else colors.onSurface,
            )
        }
        Spacer(Modifier.height(PkTheme.spacing.xs))
        Text(
            action.label,
            style = PkTheme.typography.labelMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            modifier = Modifier.width(76.dp),
        )
    }
}
