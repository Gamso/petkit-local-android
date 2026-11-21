package org.petkitlocal.feature.device.quickactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.ActionSwitches
import org.petkitlocal.core.domain.device.DeviceActionOrder
import org.petkitlocal.core.domain.fountain.FountainRules
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DeviceSectionTitle
import org.petkitlocal.feature.device.common.QuickActions
import org.petkitlocal.feature.device.common.QuickActionsTestTags

/**
 * The settings screen's way to the quick actions editor: a setting of this phone, not of the
 * device, so under its own title. Only for a device with actions, where the editor can open, and
 * not for a fountain whose sheet shows its mode instead of quick actions.
 */
internal fun LazyListScope.quickActionsSetting(device: Device, quick: QuickActions) {
    val onEdit = quick.onEdit ?: return
    if (device.actions.isEmpty() || FountainRules.modeBar(device) != null) return
    item(key = "quick-actions-title") { DeviceSectionTitle(stringResource(R.string.quick_actions_settings_section)) }
    item(key = "quick-actions") { QuickActionsSettingCard(device, quick.pinned, onEdit) }
}

@Composable
private fun QuickActionsSettingCard(device: Device, pinned: List<String>?, onEdit: () -> Unit) {
    val labels = rememberServerLabels()
    val title = stringResource(R.string.quick_actions_title)
    val row = DeviceActionOrder.layout(ActionSwitches.of(device), pinned).main
    val summary = if (row.isEmpty()) {
        stringResource(R.string.quick_actions_none)
    } else {
        row.joinToString(", ") { labels.action(it.key, it.name) }
    }
    PkCard(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
                .clickable(onClickLabel = title, role = Role.Button, onClick = onEdit)
                .padding(vertical = PkTheme.spacing.xxs)
                .testTag(QuickActionsTestTags.SETTINGS_ROW),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = PkTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = PkTheme.colors.onSurface,
                )
                Text(summary, style = PkTheme.typography.bodyMedium, color = PkTheme.colors.onSurfaceMuted)
                if (pinned == null) {
                    Text(
                        stringResource(R.string.quick_actions_settings_by_app),
                        style = PkTheme.typography.bodyMedium,
                        color = PkTheme.colors.onSurfaceMuted,
                    )
                }
            }
            Spacer(Modifier.width(PkTheme.spacing.s))
            Icon(
                PkIcons.Edit,
                contentDescription = null,
                tint = PkTheme.colors.onSurfaceMuted,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
