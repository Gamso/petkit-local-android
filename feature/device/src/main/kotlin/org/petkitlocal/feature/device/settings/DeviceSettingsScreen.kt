package org.petkitlocal.feature.device.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.ZoneId
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.device.SettingCatalog
import org.petkitlocal.core.domain.device.SettingCategory
import org.petkitlocal.core.domain.device.SettingGroup
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityComponent
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.asBoolean
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.ActionConfirmation
import org.petkitlocal.feature.device.common.CommandFeedbackBanner
import org.petkitlocal.feature.device.common.DeviceGoneView
import org.petkitlocal.feature.device.common.DeviceHeader
import org.petkitlocal.feature.device.common.DeviceSectionTitle
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.LocalQuickActions
import org.petkitlocal.feature.device.common.QuickActions
import org.petkitlocal.feature.device.common.entityValueText
import org.petkitlocal.feature.device.deviceFallbackTitle
import org.petkitlocal.feature.device.quickactions.quickActionsSetting

/** What the settings screen's user can do; the route wires each to the ViewModel. */
data class DeviceSettingsActions(
    val onBack: () -> Unit = {},
    val onToggle: (key: String, on: Boolean) -> Unit = { _, _ -> },
    val onEdit: (key: String) -> Unit = {},
    val onSave: (key: String, value: EntityValue) -> Unit = { _, _ -> },
    val onDismissEditor: () -> Unit = {},
    val onDismissFeedback: () -> Unit = {},
    /** A consumable's reset; a destructive one comes back through [onConfirmAction]. */
    val onAction: (DeviceAction) -> Unit = {},
    val onConfirmAction: () -> Unit = {},
    val onDismissConfirmation: () -> Unit = {},
    /** The serial number is on the clipboard. */
    val onSerialCopied: () -> Unit = {},
)

@Composable
internal fun DeviceSettingsRoute(onBack: () -> Unit, viewModel: DeviceSettingsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    DeviceSettingsScreen(
        uiState,
        DeviceSettingsActions(
            onBack = onBack,
            onToggle = viewModel::onToggle,
            onEdit = viewModel::onEdit,
            onSave = viewModel::onSave,
            onDismissEditor = viewModel::onDismissEditor,
            onDismissFeedback = viewModel::onDismissFeedback,
            onAction = viewModel::onAction,
            onConfirmAction = viewModel::onConfirmAction,
            onDismissConfirmation = viewModel::onDismissConfirmation,
            onSerialCopied = viewModel::onSerialCopied,
        ),
    )
}

/**
 * A device's identity card and consumables, then every setting of it, grouped, each with the
 * control its component takes.
 */
@Composable
internal fun DeviceSettingsScreen(
    uiState: DeviceSettingsUiState,
    actions: DeviceSettingsActions,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val gutter = PkTheme.spacing.gutter
    val labels = rememberServerLabels()
    val quick = LocalQuickActions.current
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .testTag(DeviceTestTags.SETTINGS_SCREEN),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(direction) + gutter,
                end = insets.calculateEndPadding(direction) + gutter,
                top = insets.calculateTopPadding() + gutter,
                bottom = PkTheme.spacing.xl,
            ),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag(DeviceTestTags.SETTINGS_LIST),
        ) {
            item(key = "header") {
                DeviceHeader(
                    title = stringResource(R.string.device_settings_title),
                    subtitle = uiState.device?.modelName ?: deviceFallbackTitle(),
                    onBack = actions.onBack,
                )
            }
            if (uiState.offline) item(key = "offline") { PkOfflineBanner() }
            settingsContent(uiState, labels, actions, quick)
        }
        uiState.commands.feedback?.let { feedback ->
            CommandFeedbackBanner(
                feedback,
                onDismiss = actions.onDismissFeedback,
                modifier = Modifier.padding(horizontal = gutter, vertical = PkTheme.spacing.xs),
            )
        }
        Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
    uiState.editor?.let { editor ->
        SettingEditorDialog(
            editor,
            onSave = { value -> actions.onSave(editor.entity.key, value) },
            onDismiss = actions.onDismissEditor,
        )
    }
    uiState.commands.confirming?.let { action ->
        ActionConfirmation(action, onConfirm = actions.onConfirmAction, onDismiss = actions.onDismissConfirmation)
    }
}

private fun LazyListScope.settingsContent(
    uiState: DeviceSettingsUiState,
    labels: ServerLabels,
    actions: DeviceSettingsActions,
    quick: QuickActions,
) {
    when {
        !uiState.loaded -> item(key = "loading") { PkLoadingView(cards = 2) }

        uiState.device == null -> item(key = "gone") { DeviceGoneView() }

        else -> {
            overview(uiState, actions)
            groups(uiState, labels, actions, quick)
        }
    }
}

private fun LazyListScope.groups(
    uiState: DeviceSettingsUiState,
    labels: ServerLabels,
    actions: DeviceSettingsActions,
    quick: QuickActions,
) {
    val device = uiState.device ?: return
    when {
        uiState.groups.isEmpty() -> {
            item(key = "none") {
                Text(
                    stringResource(R.string.device_settings_none),
                    style = PkTheme.typography.bodyLarge,
                    color = PkTheme.colors.onSurfaceMuted,
                )
            }
            quickActionsSetting(device, quick)
        }

        else -> uiState.groups.forEachIndexed { index, group ->
            val family = device.family
            // This phone's own setting, after the device's and before what is folded away.
            val firstFolded = uiState.groups.indexOfFirst { it.category.folded }
            if (index == firstFolded) quickActionsSetting(device, quick)
            if (group.category.folded) {
                item(key = "group-${group.category}") { FoldedGroup(group, family, uiState, labels, actions) }
            } else {
                item(key = "title-${group.category}") { DeviceSectionTitle(categoryTitle(group.category)) }
                item(key = "group-${group.category}") { GroupCard(group, family, uiState, labels, actions) }
            }
            if (index == uiState.groups.lastIndex && firstFolded < 0) quickActionsSetting(device, quick)
        }
    }
}

@Composable
internal fun categoryTitle(category: SettingCategory): String = stringResource(CATEGORY_TITLES.getValue(category))

private val CATEGORY_TITLES: Map<SettingCategory, Int> = mapOf(
    SettingCategory.Cleaning to R.string.device_setting_category_cleaning,
    SettingCategory.Deodorizing to R.string.device_setting_category_deodorizing,
    SettingCategory.Litter to R.string.device_setting_category_litter,
    SettingCategory.Feeding to R.string.device_setting_category_feeding,
    SettingCategory.WaterFlow to R.string.device_setting_category_water_flow,
    SettingCategory.WaterCare to R.string.device_setting_category_water_care,
    SettingCategory.ComfortAndSafety to R.string.device_setting_category_comfort_and_safety,
    SettingCategory.Detection to R.string.device_setting_category_detection,
    SettingCategory.Camera to R.string.device_setting_category_camera,
    SettingCategory.Recordings to R.string.device_setting_category_recordings,
    SettingCategory.Sound to R.string.device_setting_category_sound,
    SettingCategory.DisplayAndQuiet to R.string.device_setting_category_display_and_quiet,
    SettingCategory.Other to R.string.device_setting_category_other,
    SettingCategory.Advanced to R.string.device_setting_category_advanced,
    SettingCategory.Diagnostics to R.string.device_setting_category_diagnostics,
)

/** The one line on what [entity] does, when the app knows it. */
@Composable
internal fun settingHelp(entity: Entity, family: DeviceFamily): String? =
    SettingCatalog.explanationKey(entity, family)?.let(SettingHelp::get)?.let { stringResource(it) }

@Composable
private fun GroupCard(
    group: SettingGroup,
    family: DeviceFamily,
    uiState: DeviceSettingsUiState,
    labels: ServerLabels,
    actions: DeviceSettingsActions,
) {
    PkCard(Modifier.fillMaxWidth()) {
        Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
            group.entities.forEach { entity ->
                val sending = entity.key in uiState.commands.sending
                val help = settingHelp(entity, family)
                if (entity.component == EntityComponent.Switch) {
                    SwitchRow(entity, help, sending, labels, onToggle = { on -> actions.onToggle(entity.key, on) })
                } else {
                    EditRow(entity, help, labels, uiState.zone, onEdit = { actions.onEdit(entity.key) })
                }
            }
        }
    }
}

/** Advanced and Diagnostics: folded away under their title until asked for. */
@Composable
private fun FoldedGroup(
    group: SettingGroup,
    family: DeviceFamily,
    uiState: DeviceSettingsUiState,
    labels: ServerLabels,
    actions: DeviceSettingsActions,
) {
    var open by rememberSaveable(group.category) { mutableStateOf(false) }
    val state = stringResource(if (open) R.string.device_actions_expanded else R.string.device_actions_collapsed)
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
        PkButton(
            text = "${categoryTitle(group.category)} (${group.entities.size})",
            onClick = { open = !open },
            style = PkButtonStyle.Text,
            icon = if (open) PkIcons.ExpandLess else PkIcons.ExpandMore,
            modifier = Modifier
                .semantics { stateDescription = state }
                .testTag(DeviceTestTags.folded(group.category.name)),
        )
        if (open) GroupCard(group, family, uiState, labels, actions)
    }
}

/** A setting's name, in a heavier weight, and the line on what it does under it. */
@Composable
private fun SettingTitle(name: String, help: String?, modifier: Modifier = Modifier) {
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

/**
 * A switch, written when flipped. It shows the cached value, so it moves once the server
 * answered, not on the tap (ADR 0002); while the write is in flight it cannot be flipped again.
 */
@Composable
private fun SwitchRow(
    entity: Entity,
    help: String?,
    sending: Boolean,
    labels: ServerLabels,
    onToggle: (Boolean) -> Unit,
) {
    val on = entity.value.asBoolean == true
    val colors = PkTheme.colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .toggleable(value = on, enabled = !sending, role = Role.Switch, onValueChange = onToggle)
            .padding(vertical = PkTheme.spacing.xxs)
            .testTag(DeviceTestTags.entity(entity.key)),
    ) {
        SettingTitle(labels.entity(entity.key, entity.name), help, Modifier.weight(1f))
        Spacer(Modifier.width(PkTheme.spacing.s))
        if (sending) {
            CircularProgressIndicator(color = colors.accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(PkTheme.spacing.xs))
        }
        Switch(
            checked = on,
            // The row is the control: one touch target, one TalkBack node.
            onCheckedChange = null,
            enabled = !sending,
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

/** Any other setting: its current value at the end, and a tap opens its editor. */
@Composable
internal fun EditRow(entity: Entity, help: String?, labels: ServerLabels, zone: ZoneId, onEdit: () -> Unit) {
    val name = labels.entity(entity.key, entity.name)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = PkTheme.spacing.minTouchTarget)
            .clickable(onClickLabel = name, role = Role.Button, onClick = onEdit)
            .padding(vertical = PkTheme.spacing.xxs)
            .testTag(DeviceTestTags.entity(entity.key)),
    ) {
        SettingTitle(name, help, Modifier.weight(1f))
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            entityValueText(entity, labels, zone),
            style = PkTheme.typography.labelLarge,
            color = PkTheme.colors.accent,
            textAlign = TextAlign.End,
            maxLines = 2,
            modifier = Modifier.widthIn(max = VALUE_MAX_WIDTH),
        )
        Spacer(Modifier.width(PkTheme.spacing.xxs))
        Icon(
            PkIcons.Edit,
            contentDescription = null,
            tint = PkTheme.colors.onSurfaceMuted,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val VALUE_MAX_WIDTH = 120.dp
