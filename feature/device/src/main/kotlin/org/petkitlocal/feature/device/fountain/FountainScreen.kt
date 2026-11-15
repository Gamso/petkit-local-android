package org.petkitlocal.feature.device.fountain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.fountain.FountainPage
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.DayChartCard
import org.petkitlocal.feature.device.common.DayDot
import org.petkitlocal.feature.device.common.DeviceHeader
import org.petkitlocal.feature.device.common.DeviceStatusLine
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.RelayLink
import org.petkitlocal.feature.device.litter.DeviceHero
import org.petkitlocal.feature.device.litter.DevicePageScaffold
import org.petkitlocal.feature.device.litter.HotspotDialog
import org.petkitlocal.feature.device.litter.PageCommandCallbacks
import org.petkitlocal.feature.device.litter.PageTop
import org.petkitlocal.feature.device.litter.RefreshBanner
import org.petkitlocal.feature.device.litter.gutter
import org.petkitlocal.feature.device.litter.missingPage

/** What the user can do on the fountain page besides its commands, one callback per intent. */
data class FountainCallbacks(
    val onRefresh: () -> Unit = {},
    val onModeToggle: (String, Boolean) -> Unit = { _, _ -> },
    val onPetFilter: (Long?) -> Unit = {},
    val onHotspot: (DevicePart?) -> Unit = {},
    val commands: PageCommandCallbacks = PageCommandCallbacks(),
)

@Composable
internal fun FountainRoute(navigation: DeviceNavigation, viewModel: FountainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    FountainScreen(
        uiState = uiState,
        navigation = navigation,
        callbacks = FountainCallbacks(
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
 * The fountain page: header with the mode chips and the settings gear, the hero with the parts the
 * model has, the live status line (and, for a Bluetooth fountain, the device relaying it), today
 * against yesterday, the drinks' 24-hour chart on a fountain that records them, and the action
 * sheet every device screen shares.
 */
@Composable
internal fun FountainScreen(
    uiState: FountainUiState,
    navigation: DeviceNavigation,
    callbacks: FountainCallbacks,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    val page = uiState.page
    val tint = PkTheme.colors.heroFountain
    DevicePageScaffold(
        actions = page?.actions.orEmpty(),
        commands = uiState.commands,
        refreshing = uiState.refreshing && page != null,
        onRefresh = callbacks.onRefresh,
        callbacks = callbacks.commands,
        modifier = modifier,
        hotspots = page?.hotspots.orEmpty(),
        initiallyExpanded = initiallyExpanded,
    ) {
        if (page == null) {
            missingPage(uiState, tint, navigation.onBack, callbacks.onRefresh)
        } else {
            fountainContent(page, uiState, navigation, callbacks)
        }
    }
    uiState.openHotspot?.let { hotspot ->
        HotspotDialog(
            hotspot = hotspot,
            sending = uiState.commands.sending,
            onAction = callbacks.commands.onAction,
            onDismiss = { callbacks.onHotspot(null) },
        )
    }
}

private fun LazyListScope.fountainContent(
    page: FountainPage,
    uiState: FountainUiState,
    navigation: DeviceNavigation,
    callbacks: FountainCallbacks,
) {
    item(key = "top") {
        PageTop(PkTheme.colors.heroFountain) {
            DeviceHeader(
                title = page.device.model,
                onBack = navigation.onBack,
                onOpenSettings = { navigation.onOpenSettings(page.device.id) },
            )
            FountainModeChips(page.modes, uiState.commands.sending, callbacks.onModeToggle)
            DeviceHero(
                hotspots = page.hotspots,
                anchors = ::fountainAnchor,
                onHotspot = callbacks.onHotspot,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            ) { FountainIllustration(Modifier.fillMaxSize()) }
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
            page.relay?.let { relay -> RelayLink(relay.model, onClick = { navigation.onOpenDevice(relay.id) }) }
            RefreshBanner(uiState.offline, uiState.refreshError)
        }
    }
    if (page.recordsDrinks || page.pumpTodaySec != null) {
        item(key = "today") { FountainTodayCard(page, Modifier.gutter()) }
    }
    if (page.recordsDrinks) {
        item(key = "chart") {
            DayChartCard(
                title = stringResource(R.string.fountain_chart_title),
                emptyText = stringResource(R.string.fountain_chart_empty),
                description = R.plurals.fountain_chart_description,
                dots = page.drinks.map { DayDot(it.minuteOfDay, it.durationSec, it.petId) },
                pets = page.chartPets,
                colorIndexOf = page::colorIndexOf,
                petFilter = uiState.petFilter,
                onPetFilter = callbacks.onPetFilter,
                chartTag = FountainTestTags.CHART,
                modifier = Modifier.gutter(),
                minScaleMinutes = 1,
            )
        }
    }
}

/** Test tags of the fountain page's own nodes, for the UI and end-to-end tests. */
object FountainTestTags {
    const val CHART = "fountain:chart"
    const val TODAY = "fountain:today"

    fun pet(id: Long): String = "fountain:pet:$id"
}
