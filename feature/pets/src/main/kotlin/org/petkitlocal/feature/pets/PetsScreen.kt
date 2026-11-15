package org.petkitlocal.feature.pets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkEmptyView
import org.petkitlocal.core.designsystem.component.PkErrorView
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError

@Composable
internal fun PetsRoute(navigation: PetsNavigation, viewModel: PetsViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    PetsScreen(uiState, onRefresh = viewModel::onRefresh, navigation = navigation)
}

/** The pets list (SPEC.md §5.5): each pet with its weight and today's visits; a tap opens its page. */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox
@Composable
internal fun PetsScreen(
    uiState: PetsUiState,
    onRefresh: () -> Unit,
    navigation: PetsNavigation,
    modifier: Modifier = Modifier,
) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val gutter = PkTheme.spacing.gutter
    PullToRefreshBox(
        // The first load shows skeletons instead; the spinner is for refreshing what is shown.
        isRefreshing = uiState.refreshing && !uiState.pets.isNullOrEmpty(),
        onRefresh = onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .testTag(PetsTestTags.LIST_SCREEN),
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
                .testTag(PetsTestTags.LIST),
        ) {
            item(key = "header") { ScreenHeader(stringResource(R.string.pets_title), navigation.onBack) }
            petsBody(uiState, onRefresh, navigation)
        }
    }
}

private fun LazyListScope.petsBody(uiState: PetsUiState, onRefresh: () -> Unit, navigation: PetsNavigation) {
    val pets = uiState.pets
    val error = uiState.refreshError
    when {
        pets == null || (pets.isEmpty() && uiState.refreshing) -> item(key = "loading") { PkLoadingView(cards = 2) }

        pets.isEmpty() && error != null -> item(key = "error") {
            PkErrorView(loadErrorMessage(error), onRetry = onRefresh)
        }

        pets.isEmpty() -> item(key = "empty") { PkEmptyView(stringResource(R.string.pets_empty)) }

        else -> {
            if (error != null) {
                item(key = "refresh-error") {
                    if (uiState.offline) {
                        PkOfflineBanner()
                    } else {
                        PkOfflineBanner(message = stringResource(R.string.pets_refresh_failed))
                    }
                }
            }
            items(pets, key = { it.pet.id }) { pet ->
                PetListCard(pet.pet, pet.colorIndex, pet.visitsToday, onClick = { navigation.onOpenPet(pet.pet.id) })
            }
        }
    }
}

/** The way back and the screen's title. */
@Composable
internal fun ScreenHeader(title: String, onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        PkBackButton(onClick = onBack)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Text(
            title,
            style = PkTheme.typography.titleLarge,
            color = PkTheme.colors.onSurface,
            modifier = Modifier.semantics { heading() },
        )
    }
}

/** Why there is nothing to show: nothing was ever read, so no "last known data" either. */
@Composable
internal fun loadErrorMessage(error: DomainError): String = stringResource(
    when (error) {
        is DomainError.Network -> R.string.pets_unreachable
        is DomainError.SessionEnded -> R.string.pets_session_ended
        else -> R.string.pets_server_error
    },
)
