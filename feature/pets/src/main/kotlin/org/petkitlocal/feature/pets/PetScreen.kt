package org.petkitlocal.feature.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkEmptyView
import org.petkitlocal.core.designsystem.component.PkErrorView
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.pets.PetPage
import org.petkitlocal.core.domain.pets.WeightRange

/** What a pet's page lets its user do; the route wires each to the ViewModel or the navigation. */
data class PetCallbacks(
    val onBack: () -> Unit = {},
    val onSelectPet: (Long) -> Unit = {},
    val onRange: (WeightRange) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onRetryWeights: () -> Unit = {},
    /** History narrowed to the pet, on a visit's day or (null) on today. */
    val onOpenHistory: (LocalDate?) -> Unit = {},
)

@Composable
internal fun PetRoute(navigation: PetsNavigation, viewModel: PetViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PetScreen(
        uiState,
        PetCallbacks(
            onBack = navigation.onBack,
            onSelectPet = viewModel::onSelectPet,
            onRange = viewModel::onRange,
            onRefresh = viewModel::onRefresh,
            onRetry = viewModel::onRetry,
            onRetryWeights = viewModel::onRetryWeights,
            onOpenHistory = { day -> navigation.onOpenHistory(uiState.petId, day) },
        ),
    )
}

/**
 * A pet's page (SPEC.md §5.5): the switcher to the other pets, its avatar, name and how it is
 * recognised; its weight, today's visits and last visit; its weight over 30 days or 6 months;
 * its recent visits, each leading to History narrowed to it.
 */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox
@Composable
internal fun PetScreen(uiState: PetUiState, callbacks: PetCallbacks, modifier: Modifier = Modifier) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val gutter = PkTheme.spacing.gutter
    PullToRefreshBox(
        isRefreshing = uiState.refreshing,
        onRefresh = callbacks.onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .testTag(PetsTestTags.SCREEN),
    ) {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
            contentPadding = PaddingValues(bottom = insets.calculateBottomPadding() + PkTheme.spacing.xl),
            modifier = Modifier
                .fillMaxSize()
                .testTag(PetsTestTags.PAGE),
        ) {
            item(key = "top") { PetTop(uiState, callbacks) }
            val page = uiState.page
            if (page != null) {
                petContent(page, uiState, callbacks, Modifier.padding(horizontal = gutter))
            } else {
                item(key = "missing") {
                    Column(Modifier.padding(horizontal = PkTheme.spacing.gutter)) {
                        when {
                            uiState.skeleton -> PkLoadingView(cards = 3)

                            uiState.failedEmpty -> PkErrorView(
                                loadErrorMessage(checkNotNull(uiState.error)),
                                onRetry = callbacks.onRetry,
                            )

                            else -> PkEmptyView(stringResource(R.string.pet_gone))
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.petContent(page: PetPage, uiState: PetUiState, callbacks: PetCallbacks, gutter: Modifier) {
    if (uiState.offline || uiState.error != null) {
        item(key = "banner") {
            if (uiState.offline) {
                PkOfflineBanner(gutter)
            } else {
                PkOfflineBanner(gutter, message = stringResource(R.string.pets_refresh_failed))
            }
        }
    }
    item(key = "tiles") { PetTiles(page, uiState.today, uiState.zone, gutter) }
    item(key = "weight") {
        WeightCard(
            trend = page.trend,
            colorIndex = page.colorIndex,
            loading = uiState.weightsLoading,
            error = uiState.weightsError,
            today = uiState.today,
            onRange = callbacks.onRange,
            onRetry = callbacks.onRetryWeights,
            modifier = gutter,
        )
    }
    item(key = "visits") {
        RecentVisitsCard(page, uiState.today, uiState.zone, onOpenHistory = callbacks.onOpenHistory, modifier = gutter)
    }
}

/**
 * The page's top, on the pet tint fading into the background: the way back, the switcher when
 * there is another pet, then who this pet is.
 */
@Composable
private fun PetTop(uiState: PetUiState, callbacks: PetCallbacks) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    Column(
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.m),
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(PkTheme.colors.heroPet, PkTheme.colors.background)))
            .padding(top = insets.calculateTopPadding() + PkTheme.spacing.s)
            .padding(horizontal = PkTheme.spacing.gutter),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PkBackButton(onClick = callbacks.onBack)
            if (uiState.pets.size > 1) {
                Spacer(Modifier.width(PkTheme.spacing.s))
                PetSwitcher(uiState.pets, uiState.petId, callbacks.onSelectPet, Modifier.weight(1f))
            }
        }
        uiState.page?.let { PetProfile(it) }
    }
}
