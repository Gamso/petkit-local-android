package org.petkitlocal.feature.device.litter

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import org.petkitlocal.core.designsystem.component.PkEmptyView
import org.petkitlocal.core.designsystem.component.PkErrorView
import org.petkitlocal.core.designsystem.component.PkLoadingView
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.ActionConfirmation
import org.petkitlocal.feature.device.common.CommandFeedbackBanner
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceActionScaffold
import org.petkitlocal.feature.device.common.DeviceHeader
import org.petkitlocal.feature.device.common.DeviceTestTags

/** The command callbacks every device page shares (the action sheet, its confirmation, the banner). */
data class PageCommandCallbacks(
    val onAction: (DeviceAction) -> Unit = {},
    val onConfirmAction: () -> Unit = {},
    val onDismissConfirmation: () -> Unit = {},
    val onDismissFeedback: () -> Unit = {},
)

/**
 * A litter box or feeder page in the frame every device screen shares: the action sheet pinned
 * at the bottom (collapsed to one row, the rest behind More), the command feedback banner above
 * it, the confirmation of a destructive action, and pull to refresh over [content].
 */
@OptIn(ExperimentalMaterial3Api::class) // PullToRefreshBox
@Composable
internal fun DevicePageScaffold(
    actions: List<DeviceAction>,
    commands: CommandState,
    refreshing: Boolean,
    onRefresh: () -> Unit,
    callbacks: PageCommandCallbacks,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: LazyListScope.() -> Unit,
) {
    DeviceActionScaffold(
        actions = actions,
        onAction = callbacks.onAction,
        initiallyExpanded = initiallyExpanded,
        banner = {
            commands.feedback?.let { feedback ->
                CommandFeedbackBanner(
                    feedback,
                    onDismiss = callbacks.onDismissFeedback,
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
        PullToRefreshBox(isRefreshing = refreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(PkTheme.spacing.cardGap),
                contentPadding = PaddingValues(bottom = bottomPadding + PkTheme.spacing.m),
                modifier = Modifier.fillMaxSize().testTag(PageTestTags.LIST),
                content = content,
            )
        }
    }
    commands.confirming?.let { action ->
        ActionConfirmation(action, onConfirm = callbacks.onConfirmAction, onDismiss = callbacks.onDismissConfirmation)
    }
}

/** No page to show: loading, failed before anything was cached, or the device is gone. */
internal fun LazyListScope.missingPage(state: PageLoadState, tint: Color, onBack: () -> Unit, onRefresh: () -> Unit) {
    item(key = "header") {
        PageTop(tint) { DeviceHeader(title = "", onBack = onBack) }
    }
    val error = state.refreshError
    item(key = "missing") {
        Box(Modifier.gutter()) {
            when {
                !state.loaded || state.refreshing -> PkLoadingView(cards = 3)
                error != null -> PkErrorView(loadFailedMessage(error), onRetry = onRefresh)
                else -> PkEmptyView(stringResource(R.string.page_not_found))
            }
        }
    }
}

/** Why there is nothing to show: nothing was ever read, so no "last known data" either. */
@Composable
private fun loadFailedMessage(error: DomainError): String = stringResource(
    when (error) {
        is DomainError.Network -> R.string.page_unreachable
        is DomainError.SessionEnded -> R.string.page_session_ended
        else -> R.string.page_load_failed
    },
)

/** What [missingPage] reads of a page's state, the same on both pages. */
internal interface PageLoadState {
    val loaded: Boolean
    val refreshing: Boolean
    val refreshError: DomainError?
}
