package org.petkitlocal.feature.device.generic

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkCard
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.ui.ServerLabels
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.ActionConfirmation
import org.petkitlocal.feature.device.common.CommandFeedbackBanner
import org.petkitlocal.feature.device.common.DeviceActionScaffold
import org.petkitlocal.feature.device.common.DeviceGoneView
import org.petkitlocal.feature.device.common.DeviceHeader
import org.petkitlocal.feature.device.common.DeviceIllustration
import org.petkitlocal.feature.device.common.DeviceSectionTitle
import org.petkitlocal.feature.device.common.DeviceStatusLine
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.EntityRow
import org.petkitlocal.feature.device.common.RelayLink
import org.petkitlocal.feature.device.deviceFallbackTitle

/** What the generic screen's user can do; the route wires each to the ViewModel or navigation. */
data class GenericDeviceActions(
    val onBack: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
    /** The live video; offered only for a device with a camera. */
    val onOpenLive: () -> Unit = {},
    /** Another device's screen: for an accessory, the device relaying it. */
    val onOpenDevice: (deviceId: Long) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onAction: (DeviceAction) -> Unit = {},
    val onConfirmAction: () -> Unit = {},
    val onDismissConfirmation: () -> Unit = {},
    val onDismissFeedback: () -> Unit = {},
)

@Composable
internal fun GenericDeviceRoute(navigation: DeviceNavigation, viewModel: GenericDeviceViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    GenericDeviceScreen(
        uiState,
        GenericDeviceActions(
            onBack = navigation.onBack,
            onOpenSettings = { uiState.device?.let { navigation.onOpenSettings(it.id) } },
            onOpenLive = { uiState.device?.let { navigation.onOpenLive(it.id) } },
            onOpenDevice = navigation.onOpenDevice,
            onRefresh = viewModel::onRefresh,
            onAction = viewModel::onAction,
            onConfirmAction = viewModel::onConfirmAction,
            onDismissConfirmation = viewModel::onDismissConfirmation,
            onDismissFeedback = viewModel::onDismissFeedback,
        ),
    )
}

/**
 * Purifiers and any family without a screen of its own (SPEC.md §4): what the device
 * is doing and everything it reports; its settings behind the header's gear, its buttons in the
 * collapsible bottom action sheet.
 */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox
@Composable
internal fun GenericDeviceScreen(
    uiState: GenericDeviceUiState,
    actions: GenericDeviceActions,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val device = uiState.device
    DeviceActionScaffold(
        actions = device?.actions.orEmpty(),
        onAction = actions.onAction,
        initiallyExpanded = initiallyExpanded,
        banner = {
            uiState.commands.feedback?.let { feedback ->
                CommandFeedbackBanner(
                    feedback,
                    onDismiss = actions.onDismissFeedback,
                    modifier = Modifier.padding(
                        start = PkTheme.spacing.gutter,
                        end = PkTheme.spacing.gutter,
                        bottom = PkTheme.spacing.xs,
                    ),
                )
            }
        },
        modifier = modifier
            .background(PkTheme.colors.background)
            .testTag(DeviceTestTags.SCREEN),
    ) { bottomPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.refreshing,
            onRefresh = actions.onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            DeviceList(uiState, actions, bottomPadding)
        }
    }
    uiState.commands.confirming?.let { action ->
        ActionConfirmation(action, onConfirm = actions.onConfirmAction, onDismiss = actions.onDismissConfirmation)
    }
}

@Composable
private fun DeviceList(uiState: GenericDeviceUiState, actions: GenericDeviceActions, bottomPadding: Dp) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val gutter = PkTheme.spacing.gutter
    val device = uiState.device
    val labels = rememberServerLabels()
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
        contentPadding = PaddingValues(
            start = insets.calculateStartPadding(direction) + gutter,
            end = insets.calculateEndPadding(direction) + gutter,
            top = insets.calculateTopPadding() + gutter,
            bottom = bottomPadding + PkTheme.spacing.xl,
        ),
        modifier = Modifier
            .fillMaxSize()
            .testTag(DeviceTestTags.LIST),
    ) {
        item(key = "header") {
            DeviceHeader(
                title = device?.model ?: deviceFallbackTitle(),
                subtitle = device?.serial,
                onBack = actions.onBack,
                // Settings also hold this phone's quick actions of a device that has actions.
                onOpenSettings = actions.onOpenSettings.takeIf {
                    (uiState.sections?.settingsCount ?: 0) > 0 || device?.actions.orEmpty().isNotEmpty()
                },
                onOpenLive = actions.onOpenLive.takeIf { device?.isCamera == true },
            )
        }
        if (uiState.offline) item(key = "offline") { PkOfflineBanner() }
        when {
            !uiState.loaded -> item(key = "loading") { PkLoadingView(cards = 2) }
            device == null -> item(key = "gone") { DeviceGoneView() }
            else -> deviceContent(uiState, device, labels, actions)
        }
    }
}

private fun LazyListScope.deviceContent(
    uiState: GenericDeviceUiState,
    device: Device,
    labels: ServerLabels,
    actions: GenericDeviceActions,
) {
    val sections = uiState.sections ?: return
    item(key = "hero") {
        PkCard(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceIllustration(device.family)
                Spacer(Modifier.width(PkTheme.spacing.m))
                uiState.status?.let { status ->
                    DeviceStatusLine(
                        device,
                        status,
                        labels,
                        uiState.zone,
                        modifier = Modifier
                            .weight(1f)
                            .testTag(DeviceTestTags.STATUS),
                    )
                }
            }
            uiState.relay?.let { relay ->
                Spacer(Modifier.height(PkTheme.spacing.xs))
                RelayLink(relay.model, onClick = { actions.onOpenDevice(relay.id) })
            }
        }
    }
    item(key = "readings-title") { DeviceSectionTitle(stringResource(R.string.device_section_status)) }
    item(key = "readings") { EntityCard(sections.readings, labels, uiState) }
    if (sections.diagnostics.isNotEmpty()) {
        item(key = "diagnostics") { Diagnostics(sections.diagnostics, labels, uiState) }
    }
}

@Composable
private fun EntityCard(entities: List<Entity>, labels: ServerLabels, uiState: GenericDeviceUiState) {
    PkCard(Modifier.fillMaxWidth()) {
        if (entities.isEmpty()) {
            Text(
                stringResource(R.string.device_nothing_reported),
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
            )
        }
        entities.forEach { entity ->
            EntityRow(entity, labels, uiState.zone, Modifier.testTag(DeviceTestTags.entity(entity.key)))
        }
    }
}

/** Troubleshooting values, folded away until asked for. */
@Composable
private fun Diagnostics(entities: List<Entity>, labels: ServerLabels, uiState: GenericDeviceUiState) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
        PkButton(
            text = if (expanded) {
                stringResource(R.string.device_hide_diagnostics)
            } else {
                stringResource(R.string.device_show_diagnostics, entities.size)
            },
            onClick = { expanded = !expanded },
            style = PkButtonStyle.Text,
            icon = if (expanded) PkIcons.ExpandLess else PkIcons.ExpandMore,
        )
        if (expanded) EntityCard(entities, labels, uiState)
    }
}
