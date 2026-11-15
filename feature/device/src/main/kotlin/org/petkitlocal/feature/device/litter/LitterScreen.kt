package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.litter.LitterBoxPage
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.DeviceHeader
import org.petkitlocal.feature.device.common.DeviceStatusLine
import org.petkitlocal.feature.device.common.DeviceTestTags

/** What the user can do on the litter box page besides its commands, one callback per intent. */
data class LitterCallbacks(
    val onRefresh: () -> Unit = {},
    val onModeToggle: (String, Boolean) -> Unit = { _, _ -> },
    val onPetFilter: (Long?) -> Unit = {},
    val onHotspot: (DevicePart?) -> Unit = {},
    val commands: PageCommandCallbacks = PageCommandCallbacks(),
)

@Composable
internal fun LitterRoute(navigation: DeviceNavigation, viewModel: LitterViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    LitterScreen(
        uiState = uiState,
        navigation = navigation,
        callbacks = LitterCallbacks(
            onRefresh = viewModel::onRefresh,
            onModeToggle = viewModel::onModeToggle,
            onPetFilter = viewModel::onPetFilter,
            onHotspot = viewModel::onHotspot,
            commands = PageCommandCallbacks(
                onAction = viewModel::onAction,
                onConfirmAction = viewModel::onConfirmAction,
                onDismissConfirmation = viewModel::onDismissConfirmation,
                onDismissFeedback = viewModel::onDismissFeedback,
            ),
        ),
    )
}

/**
 * The litter box page (SPEC.md §5.3): header with the mode chips and the settings gear, the hero
 * with its hotspots, the live status line, today against yesterday, the 24-hour chart, the day's
 * activity, and the action sheet every device screen shares.
 */
@Composable
internal fun LitterScreen(
    uiState: LitterUiState,
    navigation: DeviceNavigation,
    callbacks: LitterCallbacks,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val page = uiState.page
    val tint = PkTheme.colors.heroLitter
    DevicePageScaffold(
        actions = page?.actions.orEmpty(),
        commands = uiState.commands,
        refreshing = uiState.refreshing && page != null,
        onRefresh = callbacks.onRefresh,
        callbacks = callbacks.commands,
        modifier = modifier,
        initiallyExpanded = initiallyExpanded,
    ) {
        if (page == null) {
            missingPage(uiState, tint, navigation.onBack, callbacks.onRefresh)
        } else {
            litterContent(page, uiState, navigation, callbacks)
        }
    }
    uiState.openHotspot?.let { hotspot ->
        HotspotDialog(
            hotspot = hotspot,
            sending = uiState.commands.sending,
            onAction = callbacks.commands.onAction,
            onOpenSettings = {
                callbacks.onHotspot(null)
                page?.let { navigation.onOpenSettings(it.device.id) }
            },
            onDismiss = { callbacks.onHotspot(null) },
        )
    }
}

private fun LazyListScope.litterContent(
    page: LitterBoxPage,
    uiState: LitterUiState,
    navigation: DeviceNavigation,
    callbacks: LitterCallbacks,
) {
    item(key = "top") {
        PageTop(PkTheme.colors.heroLitter) {
            DeviceHeader(
                title = page.device.model,
                onBack = navigation.onBack,
                onOpenSettings = { navigation.onOpenSettings(page.device.id) },
            )
            ModeChips(page.modes, uiState.commands.sending, callbacks.onModeToggle)
            DeviceHero(
                hotspots = page.hotspots,
                anchors = ::litterAnchor,
                onHotspot = callbacks.onHotspot,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            ) { LitterBoxIllustration(Modifier.fillMaxSize()) }
        }
    }
    item(key = "status") {
        Column(Modifier.gutter(), verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
            DeviceStatusLine(
                page.device,
                page.status,
                rememberServerLabels(),
                uiState.zone,
                Modifier.testTag(DeviceTestTags.STATUS),
            )
            RefreshBanner(uiState.offline, uiState.refreshError)
        }
    }
    item(key = "today") { TodayCard(page, Modifier.gutter()) }
    item(key = "chart") { VisitsChartCard(page, uiState.petFilter, callbacks.onPetFilter, Modifier.gutter()) }
    item(key = "activity") { ActivityCard(page, uiState.zone, navigation.onOpenHistory, Modifier.gutter()) }
}
