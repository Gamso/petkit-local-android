package org.petkitlocal.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.R
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/**
 * A single-select filter chip (History filters). Selected is filled AND carries a check mark, so
 * the state never rests on colour alone.
 */
@Composable
fun PkFilterChip(label: String, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.chip)
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            .background(if (selected) colors.accent else colors.surface)
            .border(1.dp, if (selected) colors.accent else colors.outline, PkTheme.shapes.chip)
            .padding(horizontal = PkTheme.spacing.m),
    ) {
        if (selected) {
            Icon(PkIcons.Check, contentDescription = null, tint = colors.onAccent, modifier = Modifier.size(16.dp))
            Box(Modifier.size(PkTheme.spacing.xxs + 2.dp))
        }
        Text(label, style = PkTheme.typography.labelLarge, color = if (selected) colors.onAccent else colors.onSurface)
    }
}

/**
 * Shows an on/off mode from a switch entity (device header: auto clean, auto deodorize). The
 * state is written out ("On"/"Off") next to the dot. [onToggle] null makes it read-only.
 */
@Composable
fun PkModeChip(label: String, on: Boolean, modifier: Modifier = Modifier, onToggle: ((Boolean) -> Unit)? = null) {
    val colors = PkTheme.colors
    val state = stringResource(if (on) R.string.pk_mode_on else R.string.pk_mode_off)
    val interactive = if (onToggle != null) {
        Modifier.toggleable(value = on, onValueChange = onToggle, role = Role.Switch)
    } else {
        Modifier
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clip(PkTheme.shapes.chip)
            .then(interactive)
            .background(if (on) colors.accentContainer else colors.surfaceMuted)
            .padding(horizontal = PkTheme.spacing.m),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (on) colors.accent else colors.onSurfaceMuted),
        )
        Box(Modifier.size(PkTheme.spacing.xs))
        Text(
            "$label · $state",
            style = PkTheme.typography.labelLarge,
            color = if (on) colors.onAccentContainer else colors.onSurfaceMuted,
        )
    }
}
