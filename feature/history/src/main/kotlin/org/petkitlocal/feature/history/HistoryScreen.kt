package org.petkitlocal.feature.history

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.petkitlocal.core.designsystem.component.PkBackButton
import org.petkitlocal.core.designsystem.component.PkEmptyView
import org.petkitlocal.core.designsystem.component.PkErrorView
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.component.PkOfflineBanner
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.history.HistoryEntry
import org.petkitlocal.core.domain.model.MediaFile
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.ui.formatDate
import org.petkitlocal.core.ui.rememberServerLabels

/** What History's user can do; the route wires each to the ViewModel. */
data class HistoryCallbacks(
    val onBack: () -> Unit = {},
    val onSelectDay: (LocalDate) -> Unit = {},
    val onShiftWeek: (Long) -> Unit = {},
    val onCategory: (TimelineCategory?) -> Unit = {},
    val onDevice: (Long?) -> Unit = {},
    val onPet: (Long?) -> Unit = {},
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onOpenEntry: (HistoryEntry) -> Unit = {},
    val onRetryEvent: () -> Unit = {},
    val onCloseSheet: () -> Unit = {},
    val onOpenPhoto: (MediaFile) -> Unit = {},
    val onOpenClip: (MediaFile) -> Unit = {},
    val onCloseViewer: () -> Unit = {},
)

@Composable
internal fun HistoryRoute(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    HistoryScreen(
        uiState,
        HistoryCallbacks(
            onBack = onBack,
            onSelectDay = viewModel::onSelectDay,
            onShiftWeek = viewModel::onShiftWeek,
            onCategory = viewModel::onCategory,
            onDevice = viewModel::onDevice,
            onPet = viewModel::onPet,
            onRefresh = viewModel::onRefresh,
            onLoadMore = viewModel::onLoadMore,
            onRetry = viewModel::onRetry,
            onOpenEntry = viewModel::onOpenEntry,
            onRetryEvent = viewModel::onRetryEvent,
            onCloseSheet = viewModel::onCloseSheet,
            onOpenPhoto = viewModel::onOpenPhoto,
            onOpenClip = viewModel::onOpenClip,
            onCloseViewer = viewModel::onCloseViewer,
        ),
        playerFactory = viewModel.clipPlayerFactory,
    )
}

/**
 * History (SPEC.md §5.4): the week strip, the filters, then the day's cards newest first, paged
 * from the server as the list nears its end. A card opens its detail sheet; a photo opens full
 * screen and a clip plays in-app.
 */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox
@Composable
internal fun HistoryScreen(
    uiState: HistoryUiState,
    callbacks: HistoryCallbacks,
    modifier: Modifier = Modifier,
    playerFactory: ClipPlayerFactory? = null,
) {
    PullToRefreshBox(
        isRefreshing = uiState.refreshing,
        onRefresh = callbacks.onRefresh,
        modifier = modifier
            .fillMaxSize()
            .background(PkTheme.colors.background)
            .testTag(HistoryTestTags.SCREEN),
    ) {
        HistoryList(uiState, callbacks)
    }
    uiState.sheet?.let { sheet ->
        EventSheet(sheet, uiState.zone, callbacks)
    }
    when (val viewer = uiState.viewer) {
        is MediaViewerState.Photos -> PhotoViewer(viewer, onClose = callbacks.onCloseViewer)
        is MediaViewerState.Clip -> ClipPlayer(viewer.clip, playerFactory, onClose = callbacks.onCloseViewer)
        null -> Unit
    }
}

@Composable
private fun HistoryList(uiState: HistoryUiState, callbacks: HistoryCallbacks) {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val direction = LocalLayoutDirection.current
    val gutter = PkTheme.spacing.gutter
    val listState = rememberLazyListState()
    LoadMoreWhenNearEnd(listState, uiState.paging.canLoadMore, callbacks.onLoadMore)
    LazyColumn(
        state = listState,
        verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
        contentPadding = PaddingValues(
            start = insets.calculateStartPadding(direction),
            end = insets.calculateEndPadding(direction),
            top = insets.calculateTopPadding() + gutter,
            bottom = insets.calculateBottomPadding() + PkTheme.spacing.xl,
        ),
        modifier = Modifier
            .fillMaxSize()
            .testTag(HistoryTestTags.LIST),
    ) {
        item(key = "header") { Header(uiState, callbacks.onBack, Modifier.padding(horizontal = gutter)) }
        item(key = "days") {
            DayStrip(
                week = uiState.week,
                selected = uiState.query.day,
                today = uiState.today,
                hasNextWeek = uiState.hasNextWeek,
                onSelect = callbacks.onSelectDay,
                onShiftWeek = callbacks.onShiftWeek,
                modifier = Modifier.padding(horizontal = PkTheme.spacing.xs),
            )
        }
        item(key = "filters") { FilterRow(uiState, callbacks, rememberServerLabels()) }
        // With nothing cached, the error view says it all: no banner about last known data.
        if (uiState.offline && !uiState.failedEmpty) {
            item(key = "offline") { PkOfflineBanner(Modifier.padding(horizontal = gutter)) }
        }
        historyBody(uiState, callbacks, gutter)
    }
}

/** The cards, or what stands for them: loading, the error with its retry, or nothing that day. */
private fun LazyListScope.historyBody(uiState: HistoryUiState, callbacks: HistoryCallbacks, gutter: Dp) {
    when {
        uiState.loading -> item(key = "loading") { PkLoadingView(Modifier.padding(horizontal = gutter), cards = 4) }

        uiState.failedEmpty -> item(key = "error") {
            PkErrorView(historyErrorMessage(uiState.paging.error), onRetry = callbacks.onRetry)
        }

        uiState.empty -> item(key = "empty") {
            val filtered =
                uiState.query.category != null || uiState.query.deviceId != null || uiState.query.petId != null
            PkEmptyView(
                stringResource(if (filtered) R.string.history_empty_filtered else R.string.history_empty_day),
            )
        }

        else -> {
            items(uiState.entries, key = { it.item.id }) { entry ->
                HistoryCard(
                    entry,
                    uiState.zone,
                    onClick = { callbacks.onOpenEntry(entry) },
                    modifier = Modifier.padding(horizontal = gutter),
                )
            }
            if (uiState.paging.loading || uiState.paging.error != null) {
                item(key = "footer") {
                    ListFooter(uiState.paging, callbacks.onRetry, Modifier.padding(horizontal = gutter))
                }
            }
        }
    }
}

/** Asks for the next page once the last cards come into view, and again after each page. */
@Composable
private fun LoadMoreWhenNearEnd(listState: LazyListState, canLoadMore: Boolean, onLoadMore: () -> Unit) {
    val load by rememberUpdatedState(onLoadMore)
    val enabled by rememberUpdatedState(canLoadMore)
    LaunchedEffect(listState, canLoadMore) {
        snapshotFlow {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            info.totalItemsCount > 0 && last >= info.totalItemsCount - LOAD_AHEAD
        }
            .distinctUntilChanged()
            .filter { it && enabled }
            .collect { load() }
    }
}

/** How many items before the end of the list the next page is asked for. */
private const val LOAD_AHEAD = 4

@Composable
private fun Header(uiState: HistoryUiState, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val locale = LocalConfiguration.current.locales[0]
    val day = if (uiState.query.day == uiState.today) {
        stringResource(R.string.history_today)
    } else {
        formatDate(uiState.query.day, locale)
    }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = modifier.fillMaxWidth()) {
        PkBackButton(onClick = onBack)
        Spacer(Modifier.width(PkTheme.spacing.s))
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.history_title),
                style = PkTheme.typography.titleLarge,
                color = PkTheme.colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                day,
                style = PkTheme.typography.bodyMedium,
                color = PkTheme.colors.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One line on why History could not be read. */
@Composable
internal fun historyErrorMessage(error: DomainError?): String = when (error) {
    is DomainError.Network -> stringResource(R.string.history_unreachable)
    is DomainError.SessionEnded, is DomainError.Unauthorized -> stringResource(R.string.history_session_ended)
    else -> stringResource(R.string.history_server_error)
}
