package org.petkitlocal.feature.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme

/* The pieces every settings row is made of, as on a device's settings screen: a name in a heavier
 * weight with the one line on what it does under it, then its value or its control. */

/** A titled group of settings in one card. */
@Composable
internal fun SettingsSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), modifier = modifier) {
        Text(
            title,
            style = PkTheme.typography.titleMedium,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
        PkCard(Modifier.fillMaxWidth()) {
            Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs), content = content)
        }
    }
}

/** A setting's name and the line on what it does under it. */
@Composable
internal fun SettingTitle(name: String, help: String?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            name,
            style = PkTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = PkTheme.colors.onSurface,
        )
        if (help != null) {
            Text(help, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
        }
    }
}

/** A read-only value: what it is, the line on it, and the value at the end. */
@Composable
internal fun InfoRow(name: String, help: String?, value: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .semantics(mergeDescendants = true) {}
            .padding(vertical = PkTheme.spacing.xxs),
    ) {
        SettingTitle(name, help, Modifier.weight(1f))
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            value,
            style = PkTheme.typography.labelLarge,
            color = PkTheme.colors.accent,
            textAlign = TextAlign.End,
            modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH),
        )
    }
}

/** A row that opens something else: the whole row is the button, a chevron at its end. */
@Composable
internal fun LinkRow(name: String, help: String?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = PkTheme.spacing.xxs),
    ) {
        SettingTitle(name, help, Modifier.weight(1f))
        Spacer(Modifier.width(PkTheme.spacing.s))
        Icon(PkIcons.ChevronRight, contentDescription = null, tint = PkTheme.colors.onSurfaceMuted)
    }
}

/**
 * A switch. It shows what the server holds, so it moves once the server answered, not on the
 * tap (ADR 0002); while a write is in flight it cannot be flipped, and the one being written
 * shows a small indicator.
 */
@Suppress("LongParameterList") // A row's name, line, state and callback, each its own.
@Composable
internal fun SwitchRow(
    name: String,
    help: String?,
    checked: Boolean,
    enabled: Boolean,
    saving: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = PkTheme.spacing.xxs),
    ) {
        SettingTitle(name, help, Modifier.weight(1f))
        Spacer(Modifier.width(PkTheme.spacing.s))
        if (saving) {
            SavingIndicator()
            Spacer(Modifier.width(PkTheme.spacing.xs))
        }
        Switch(
            checked = checked,
            // The row is the control: one touch target, one TalkBack node.
            onCheckedChange = null,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = colors.onAccent,
                checkedTrackColor = colors.accent,
                checkedBorderColor = colors.accent,
                uncheckedThumbColor = colors.onSurfaceMuted,
                uncheckedTrackColor = colors.surfaceMuted,
                uncheckedBorderColor = colors.outline,
            ),
        )
    }
}

/**
 * The small spinner of a write in flight. Still in inspection mode (previews, screenshot tests):
 * animated, it never lets Robolectric's looper go idle and the capture hangs.
 */
@Composable
private fun SavingIndicator() {
    val modifier = Modifier.size(18.dp)
    if (LocalInspectionMode.current) {
        CircularProgressIndicator(
            progress = { STILL_PROGRESS },
            color = PkTheme.colors.accent,
            strokeWidth = 2.dp,
            modifier = modifier,
        )
    } else {
        CircularProgressIndicator(color = PkTheme.colors.accent, strokeWidth = 2.dp, modifier = modifier)
    }
}

/** A line under a section's rows: a refusal, or what a group of rows is for. */
@Composable
internal fun SectionNote(text: String, modifier: Modifier = Modifier, error: Boolean = false) {
    Text(
        text,
        style = PkTheme.typography.bodyMedium,
        color = if (error) PkTheme.colors.danger else PkTheme.colors.onSurfaceMuted,
        modifier = modifier,
    )
}

private const val STILL_PROGRESS = 0.25f
private val VALUE_MAX_WIDTH = 160.dp
