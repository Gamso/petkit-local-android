package org.petkitlocal.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkButton
import org.petkitlocal.core.designsystem.component.PkButtonStyle
import org.petkitlocal.core.designsystem.component.PkEmptyView
import org.petkitlocal.core.designsystem.component.PkErrorView
import org.petkitlocal.core.designsystem.component.PkIconButton
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.icon.PkIcons
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.home.HomeOverview

/** Where Home leads; the app wires each to a destination. */
data class HomeNavigation(
    val onDeviceClick: (deviceId: Long) -> Unit = {},
    val onOpenPets: () -> Unit = {},
    val onOpenPet: (petId: Long) -> Unit = {},
    val onOpenHistory: () -> Unit = {},
    val onOpenSettings: () -> Unit = {},
)

@Composable
internal fun HomeRoute(navigation: HomeNavigation, viewModel: HomeViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HomeScreen(uiState, onRefresh = viewModel::onRefresh, navigation = navigation)
}

/** Home (SPEC.md §5.2): alert banners, the two-column device grid, the pets row. */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox
@Composable
internal fun HomeScreen(
    uiState: HomeUiState,
    onRefresh: () -> Unit,
    navigation: HomeNavigation,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val gutter = PkTheme.spacing.gutter
    PullToRefreshBox(
        // The first load shows skeletons instead; the spinner is for refreshing what is shown.
        isRefreshing = uiState.refreshing && uiState.overview?.isEmpty == false,
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
            contentPadding = PaddingValues(
                start = insets.calculateStartPadding(direction) + gutter,
                end = insets.calculateEndPadding(direction) + gutter,
                top = insets.calculateTopPadding() + gutter,
                bottom = insets.calculateBottomPadding() + PkTheme.spacing.xl,
            ),
            modifier = Modifier
                .fillMaxSize()
                .testTag(HomeTestTags.LIST),
        ) {
            item(key = "header") { HomeHeader(navigation) }
            homeBody(uiState, onRefresh, navigation)
        }
    }
}

private fun LazyListScope.homeBody(uiState: HomeUiState, onRefresh: () -> Unit, navigation: HomeNavigation) {
    val overview = uiState.overview
    val error = uiState.refreshError
    when {
        overview == null || (overview.isEmpty && uiState.refreshing) ->
            item(key = "loading") { PkLoadingView(cards = 3) }

        overview.isEmpty && error != null ->
            item(key = "error") { PkErrorView(refreshErrorMessage(error), onRetry = onRefresh) }

        overview.isEmpty -> item(key = "empty") { PkEmptyView(stringResource(R.string.home_no_devices)) }

        else -> {
            if (error != null) item(key = "refresh-error") { RefreshBanner(uiState) }
            homeContent(overview, navigation)
        }
    }
}

@Composable
private fun RefreshBanner(uiState: HomeUiState) {
    if (uiState.offline) {
        PkOfflineBanner()
    } else {
        PkOfflineBanner(message = stringResource(R.string.home_refresh_failed))
    }
}

private fun LazyListScope.homeContent(overview: HomeOverview, navigation: HomeNavigation) {
    if (overview.alerts.isNotEmpty()) {
        item(key = "alerts") { AlertList(overview, navigation) }
    }
    if (overview.devices.isNotEmpty()) {
        item(key = "devices-title") { SectionTitle(stringResource(R.string.home_devices)) }
        val petColors = overview.pets.associate { it.pet.id to it.colorIndex }
        items(overview.devices.chunked(2), key = { row -> "devices-${row.first().device.id}" }) { row ->
            // One row of the grid: both cards as tall as the taller one.
            Row(
                horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            ) {
                row.forEach { device ->
                    DeviceCard(
                        overview = device,
                        petColors = petColors,
                        onClick = { navigation.onDeviceClick(device.device.id) },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .testTag(HomeTestTags.device(device.device.id)),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
    if (overview.pets.isNotEmpty()) {
        item(key = "pets-title") {
            SectionTitle(stringResource(R.string.home_pets)) {
                PkButton(
                    stringResource(R.string.home_pets_see_all),
                    onClick = navigation.onOpenPets,
                    style = PkButtonStyle.Text,
                )
            }
        }
        item(key = "pets") { PetsRow(overview, navigation) }
    }
}

/** The first [VISIBLE_ALERTS] banners, and the rest behind a toggle so the grid stays in view. */
@Composable
private fun AlertList(overview: HomeOverview, navigation: HomeNavigation) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val alerts = overview.alerts
    val shown = if (expanded) alerts else alerts.take(VISIBLE_ALERTS)
    Column(verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.xs)) {
        shown.forEach { alert ->
            AlertBanner(alert, onClick = { navigation.onDeviceClick(alert.deviceId) })
        }
        if (alerts.size > VISIBLE_ALERTS) {
            PkButton(
                if (expanded) {
                    stringResource(R.string.home_fewer_alerts)
                } else {
                    pluralStringResource(
                        R.plurals.home_more_alerts,
                        alerts.size - VISIBLE_ALERTS,
                        alerts.size - VISIBLE_ALERTS,
                    )
                },
                onClick = { expanded = !expanded },
                style = PkButtonStyle.Text,
            )
        }
    }
}

private const val VISIBLE_ALERTS = 3

@Composable
private fun PetsRow(overview: HomeOverview, navigation: HomeNavigation) {
    val pets = overview.pets
    if (pets.size <= 2) {
        // Two pets share the width, as on the mockup.
        Row(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap)) {
            pets.forEach { pet ->
                PetChip(
                    pet,
                    onClick = { navigation.onOpenPet(pet.pet.id) },
                    modifier = Modifier.weight(1f).testTag(HomeTestTags.pet(pet.pet.id)),
                )
            }
            if (pets.size == 1) Spacer(Modifier.weight(1f))
        }
    } else {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap)) {
            items(pets, key = { it.pet.id }) { pet ->
                PetChip(
                    pet,
                    onClick = { navigation.onOpenPet(pet.pet.id) },
                    modifier = Modifier.width(PET_CHIP_WIDTH).testTag(HomeTestTags.pet(pet.pet.id)),
                )
            }
        }
    }
}

private val PET_CHIP_WIDTH = 176.dp

@Composable
private fun HomeHeader(navigation: HomeNavigation) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            stringResource(R.string.home_title),
            style = PkTheme.typography.display,
            color = PkTheme.colors.onSurface,
            modifier = Modifier
                .weight(1f)
                .semantics { heading() },
        )
        PkIconButton(PkIcons.History, stringResource(R.string.home_open_history), navigation.onOpenHistory)
        Spacer(Modifier.size(PkTheme.spacing.xs))
        PkIconButton(PkIcons.Settings, stringResource(R.string.home_open_settings), navigation.onOpenSettings)
    }
}

/** Test tags of Home's nodes, for the UI and end-to-end tests. */
object HomeTestTags {
    const val LIST = "home:list"

    fun device(id: Long): String = "home:device:$id"

    fun pet(id: Long): String = "home:pet:$id"
}
