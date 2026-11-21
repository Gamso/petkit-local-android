package org.petkitlocal.feature.device.feeder

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
import org.petkitlocal.core.domain.feeder.FeederDayFilter
import org.petkitlocal.core.domain.feeder.FeederPage
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.ui.rememberServerLabels
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.CameraLiveCard
import org.petkitlocal.feature.device.common.DeviceHeader
import org.petkitlocal.feature.device.common.DeviceStatusLine
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.LiveCardUi
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.petkitlocal.feature.device.common.recentClips
import org.petkitlocal.feature.device.common.rememberLiveCard
import org.petkitlocal.feature.device.common.rememberRecentClips
import org.petkitlocal.feature.device.litter.DeviceHero
import org.petkitlocal.feature.device.litter.DevicePageScaffold
import org.petkitlocal.feature.device.litter.HotspotDialog
import org.petkitlocal.feature.device.litter.PageCommandCallbacks
import org.petkitlocal.feature.device.litter.PageTop
import org.petkitlocal.feature.device.litter.RefreshBanner
import org.petkitlocal.feature.device.litter.gutter
import org.petkitlocal.feature.device.litter.heroPhoto
import org.petkitlocal.feature.device.litter.missingPage

/** What the user can do on the feeder page besides its device commands, one callback per intent. */
data class FeederCallbacks(
    val onRefresh: () -> Unit = {},
    val onHotspot: (DevicePart?) -> Unit = {},
    val onPortions: (hopperIndex: Int, portions: Int) -> Unit = { _, _ -> },
    val onFeedNow: () -> Unit = {},
    val onPlanActive: (Boolean) -> Unit = {},
    val onEditPlan: () -> Unit = {},
    val onPlanEdit: (PlanEdit) -> Unit = {},
    val onSavePlan: () -> Unit = {},
    val onDismissPlan: () -> Unit = {},
    val onAddFeed: () -> Unit = {},
    val onFeedDraft: (FeedDraft) -> Unit = {},
    val onSaveFeed: () -> Unit = {},
    val onDismissFeed: () -> Unit = {},
    val onCancelFeed: (String) -> Unit = {},
    val onDayFilter: (FeederDayFilter) -> Unit = {},
    /** A pet of the meals-eaten chart's filter; null shows every pet. */
    val onPetFilter: (Long?) -> Unit = {},
    val commands: PageCommandCallbacks = PageCommandCallbacks(),
)

@Composable
internal fun FeederRoute(navigation: DeviceNavigation, viewModel: FeederViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clips = rememberRecentClips(navigation)
    FeederScreen(
        uiState = uiState,
        navigation = navigation,
        clips = clips,
        live = rememberLiveCard(),
        callbacks = FeederCallbacks(
            onRefresh = {
                viewModel.onRefresh()
                clips.onRefresh()
            },
            onHotspot = viewModel::onHotspot,
            onPortions = viewModel::onPortions,
            onFeedNow = viewModel::onFeedNow,
            onPlanActive = viewModel::onPlanActive,
            onEditPlan = viewModel::onEditPlan,
            onPlanEdit = viewModel::onPlanEdit,
            onSavePlan = viewModel::onSavePlan,
            onDismissPlan = viewModel::onDismissPlan,
            onAddFeed = viewModel::onAddFeed,
            onFeedDraft = viewModel::onFeedDraft,
            onSaveFeed = viewModel::onSaveFeed,
            onDismissFeed = viewModel::onDismissFeed,
            onCancelFeed = viewModel::onCancelFeed,
            onDayFilter = viewModel::onDayFilter,
            onPetFilter = viewModel::onPetFilter,
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
 * The feeder page (SPEC.md §5.6): header, a camera's live card, the hero with the food, bowl and
 * desiccant hotspots, the live status line, the next meal with feed now, today's plan with its pause switch and the
 * weekly plan's editor, the day's meals and what the camera saw (on a camera feeder, the pets'
 * meals eaten against yesterday, and their chart), the one-off feeds, a camera's recent clips,
 * and the action sheet every device screen shares: the feeder's buttons (feed is feed now), then
 * the plan, a one-off feed and History behind More.
 */
@Composable
internal fun FeederScreen(
    uiState: FeederUiState,
    navigation: DeviceNavigation,
    callbacks: FeederCallbacks,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    /** A camera's recent clips; null shows none. */
    clips: RecentClipsUi? = null,
    /** A camera's live card: what it shows, and its player. */
    live: LiveCardUi = LiveCardUi(),
) {
    val page = uiState.page
    val tint = PkTheme.colors.heroFeeder
    val pageActions = pageActions()
    DevicePageScaffold(
        actions = page?.device?.actions.orEmpty() + pageActions,
        commands = uiState.commands,
        refreshing = uiState.refreshing && page != null,
        onRefresh = callbacks.onRefresh,
        callbacks = callbacks.commands.copy(
            onAction = { action -> onSheetAction(action, page?.device?.id, navigation, callbacks) },
        ),
        modifier = modifier,
        hotspots = page?.hotspots.orEmpty(),
        initiallyExpanded = initiallyExpanded,
    ) {
        if (page == null) {
            missingPage(uiState, tint, navigation.onBack, callbacks.onRefresh)
        } else {
            feederContent(page, uiState, navigation, callbacks, live)
            recentClips(page.device, clips, Modifier.gutter())
        }
    }
    FeederDialogs(uiState, callbacks)
}

private fun LazyListScope.feederContent(
    page: FeederPage,
    uiState: FeederUiState,
    navigation: DeviceNavigation,
    callbacks: FeederCallbacks,
    live: LiveCardUi,
) {
    item(key = "top") {
        PageTop(PkTheme.colors.heroFeeder) {
            DeviceHeader(
                title = page.device.modelName,
                onBack = navigation.onBack,
                onOpenSettings = { navigation.onOpenSettings(page.device.id) },
            )
            CameraLiveCard(page.device, live, navigation, Modifier.padding(top = PkTheme.spacing.xs))
            DeviceHero(
                hotspots = page.hotspots,
                anchors = ::feederAnchor,
                onHotspot = callbacks.onHotspot,
                modifier = Modifier.padding(top = PkTheme.spacing.xs),
                photo = heroPhoto(page.device),
            ) { FeederIllustration(page.dualHopper, Modifier.fillMaxSize()) }
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
    item(key = "next") { NextMealCard(uiState, page, callbacks, Modifier.gutter()) }
    item(key = "plan") { PlanCard(uiState, page, callbacks, Modifier.gutter()) }
    item(key = "day") {
        FeederDayCard(
            day = page.day,
            filter = uiState.shownDayFilter,
            zone = uiState.zone,
            onFilter = callbacks.onDayFilter,
            onOpenClip = navigation.onOpenClip,
            onOpenHistory = { navigation.onOpenHistory(page.device.id) },
            modifier = Modifier.gutter(),
            eating = page.eating,
            petFilter = uiState.petFilter,
            onPetFilter = callbacks.onPetFilter,
        )
    }
    item(key = "later") { OneOffCard(uiState, page, callbacks, Modifier.gutter()) }
}

/**
 * The page's own entries of the action sheet (SPEC.md §5.6: plan, later, history). They rank
 * after the feeder's everyday buttons, so they sit behind More, before maintenance.
 */
@Composable
private fun pageActions(): List<DeviceAction> = listOf(
    DeviceAction(SHEET_PLAN, stringResource(R.string.feeder_sheet_plan), destructive = false),
    DeviceAction(SHEET_LATER, stringResource(R.string.feeder_sheet_later), destructive = false),
    DeviceAction(SHEET_HISTORY, stringResource(R.string.feeder_sheet_history), destructive = false),
)

private fun onSheetAction(
    action: DeviceAction,
    deviceId: Long?,
    navigation: DeviceNavigation,
    callbacks: FeederCallbacks,
) {
    when (action.key) {
        SHEET_PLAN -> callbacks.onEditPlan()
        SHEET_LATER -> callbacks.onAddFeed()
        SHEET_HISTORY -> deviceId?.let(navigation.onOpenHistory)
        else -> callbacks.commands.onAction(action)
    }
}

private const val SHEET_PLAN = "feeder:plan"
private const val SHEET_LATER = "feeder:later"
private const val SHEET_HISTORY = "feeder:history"

@Composable
private fun FeederDialogs(uiState: FeederUiState, callbacks: FeederCallbacks) {
    uiState.openHotspot?.let { hotspot ->
        HotspotDialog(
            hotspot = hotspot,
            sending = uiState.commands.sending,
            onAction = callbacks.commands.onAction,
            onDismiss = { callbacks.onHotspot(null) },
        )
    }
    uiState.planDraft?.let { draft ->
        PlanEditorDialog(
            draft = draft,
            saving = uiState.sending(FeederViewModel.PLAN_SAVE_KEY),
            onEdit = callbacks.onPlanEdit,
            onSave = callbacks.onSavePlan,
            onClose = callbacks.onDismissPlan,
        )
    }
    uiState.feedDraft?.let { draft ->
        FeedFormDialog(
            draft = draft,
            today = uiState.today,
            saving = uiState.sending(FeederViewModel.FEED_ADD_KEY),
            onChange = callbacks.onFeedDraft,
            onSave = callbacks.onSaveFeed,
            onDismiss = callbacks.onDismissFeed,
        )
    }
}
