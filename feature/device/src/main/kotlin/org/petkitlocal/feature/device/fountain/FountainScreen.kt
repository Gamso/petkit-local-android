package org.petkitlocal.feature.device.fountain

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.Instant
import kotlinx.coroutines.delay
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.fountain.FountainPage
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.ActivityCard
import org.petkitlocal.feature.device.common.DayChartCard
import org.petkitlocal.feature.device.common.DayDot
import org.petkitlocal.feature.device.common.DeviceHeader
import org.petkitlocal.feature.device.common.DeviceStatusLine
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.petkitlocal.feature.device.common.UntimedWords
import org.petkitlocal.feature.device.common.recentClips
import org.petkitlocal.feature.device.common.rememberRecentClips
import org.petkitlocal.feature.device.litter.DeviceHero
import org.petkitlocal.feature.device.litter.DevicePageScaffold
import org.petkitlocal.feature.device.litter.HotspotDialog
import org.petkitlocal.feature.device.litter.PageCommandCallbacks
import org.petkitlocal.feature.device.litter.PageTop
import org.petkitlocal.feature.device.litter.RefreshBanner
import org.petkitlocal.feature.device.litter.gutter
import org.petkitlocal.feature.device.litter.missingPage
import org.petkitlocal.feature.device.liveOf

/** What the user can do on the fountain page besides its commands, one callback per intent. */
data class FountainCallbacks(
    val onRefresh: () -> Unit = {},
    /** An option of the bottom bar's mode selector. */
    val onMode: (String) -> Unit = {},
    val onPetFilter: (Long?) -> Unit = {},
    val onHotspot: (DevicePart?) -> Unit = {},
    val commands: PageCommandCallbacks = PageCommandCallbacks(),
)

@Composable
internal fun FountainRoute(navigation: DeviceNavigation, viewModel: FountainViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clips = rememberRecentClips(navigation)
    // "Seen 2 min ago" moves on while the page is shown.
    LaunchedEffect(viewModel) {
        while (true) {
            delay(TICK_MS)
            viewModel.onTick()
        }
    }
    FountainScreen(
        uiState = uiState,
        navigation = navigation,
        clips = clips,
        callbacks = FountainCallbacks(
            onRefresh = {
                viewModel.onRefresh()
                clips.onRefresh()
            },
            onMode = viewModel::onMode,
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

private const val TICK_MS = 60_000L

/**
 * The fountain page: header with the settings gear; under it the battery and the features that
 * are on, and for a Bluetooth fountain when it was seen and through which device (online or not);
 * the hero with the parts the model has, the live status line, today against yesterday, the
 * drinks' 24-hour chart and the day's activity on a fountain that records them, a camera's recent
 * clips, and the action sheet every device screen shares. When the server lets the fountain's
 * mode be set, the sheet's collapsed row is the mode selector, every action behind More.
 */
@Composable
internal fun FountainScreen(
    uiState: FountainUiState,
    navigation: DeviceNavigation,
    callbacks: FountainCallbacks,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    /** A camera's recent clips; null shows none. */
    clips: RecentClipsUi? = null,
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
        sheetRow = page?.modeBar?.let { bar ->
            {
                FountainModeSelector(
                    bar = bar,
                    pending = uiState.pendingMode,
                    sending = bar.key in uiState.commands.sending,
                    onMode = callbacks.onMode,
                )
            }
        },
    ) {
        if (page == null) {
            missingPage(uiState, tint, navigation.onBack, callbacks.onRefresh)
        } else {
            fountainContent(page, uiState, navigation, callbacks)
            recentClips(page.device, clips, Modifier.gutter())
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
    item(key = "top") { FountainTop(page, uiState.now, navigation, callbacks.onHotspot) }
    item(key = "status") {
        Column(Modifier.gutter(), verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
            DeviceStatusLine(
                page.device,
                page.status,
                rememberServerLabels(),
                uiState.zone,
                Modifier.testTag(DeviceTestTags.STATUS),
                // The relay's line at the top says when it was seen.
                showLastSeen = page.relay == null,
            )
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
                untimed = UntimedWords(R.plurals.fountain_chart_untimed, R.string.fountain_chart_no_durations),
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
    // A Bluetooth fountain records no drink: an empty list there would only say so twice.
    if (page.recordsDrinks || page.activity.isNotEmpty()) {
        item(key = "activity") {
            ActivityCard(page.activity, uiState.zone, { navigation.onOpenHistory(page.device.id) }, Modifier.gutter())
        }
    }
}

/**
 * The page's top, on its tint: the header, the battery and the features that are on, a Bluetooth
 * fountain's freshness through its relay, and the hero.
 */
@Composable
private fun FountainTop(
    page: FountainPage,
    now: Instant,
    navigation: DeviceNavigation,
    onHotspot: (DevicePart?) -> Unit,
) {
    PageTop(PkTheme.colors.heroFountain) {
        DeviceHeader(
            title = page.device.model,
            onBack = navigation.onBack,
            onOpenSettings = { navigation.onOpenSettings(page.device.id) },
            onOpenLive = navigation.liveOf(page.device),
        )
        FountainStatusChips(page.battery, page.modes, Modifier.padding(top = PkTheme.spacing.xs))
        page.relay?.let { relay ->
            RelayFreshness(
                relay = relay,
                lastSeen = page.device.lastSeen,
                now = now,
                onOpen = { navigation.onOpenDevice(relay.id) },
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
            )
        }
        DeviceHero(
            hotspots = page.hotspots,
            anchors = ::fountainAnchor,
            onHotspot = onHotspot,
            modifier = Modifier.padding(top = PkTheme.spacing.xs),
        ) { FountainIllustration(Modifier.fillMaxSize()) }
    }
}

/** Test tags of the fountain page's own nodes, for the UI and end-to-end tests. */
object FountainTestTags {
    const val CHART = "fountain:chart"
    const val TODAY = "fountain:today"
    const val BATTERY = "fountain:battery"
    const val MODE_BAR = "fountain:mode-bar"

    /** An option of the mode selector, by its value (`smart`, `continuous`...). */
    fun mode(option: String): String = "fountain:mode:$option"

    fun pet(id: Long): String = "fountain:pet:$id"
}
