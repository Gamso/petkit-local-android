package org.petkitlocal.feature.device.feeder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.RefreshDevicePageUseCase
import org.petkitlocal.core.domain.feeder.FeedNow
import org.petkitlocal.core.domain.feeder.FeederPage
import org.petkitlocal.core.domain.feeder.FeederRules
import org.petkitlocal.core.domain.feeder.FeedingPlanRules
import org.petkitlocal.core.domain.feeder.ObserveFeederUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.FeedingRepository
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.R
import org.petkitlocal.feature.device.common.CommandSubject
import org.petkitlocal.feature.device.common.DeviceCommandController
import org.petkitlocal.feature.device.generic.isUnreachable

/**
 * The feeder page (SPEC.md §5.6). It observes the caches only (ADR 0002), keeps the live socket
 * open while shown, and refreshes when it opens; the plan and one-off feeds are read again
 * whenever the feeder's stored schedule changes (its `feeding_schedule` entity, which live
 * updates patch), so a change made elsewhere shows. Every command, the plan's and the feeds'
 * included, goes through the device screens' shared [DeviceCommandController], which says how
 * it went out; a form's refusal stays in the form.
 */
@Suppress("TooManyFunctions") // One function per user intent (ARCHITECTURE.md §3).
@OptIn(ExperimentalCoroutinesApi::class) // flatMapLatest
@HiltViewModel
class FeederViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    observeFeeder: ObserveFeederUseCase,
    observeLiveUpdates: ObserveLiveUpdatesUseCase,
    private val refreshPage: RefreshDevicePageUseCase,
    private val feeding: FeedingRepository,
    runAction: RunDeviceActionUseCase,
    setEntityValue: SetEntityValueUseCase,
    private val today: TodayUseCase,
    private val clock: Clock,
) : ViewModel() {
    val deviceId: Long = checkNotNull(savedStateHandle[DEVICE_ID_KEY]) { "no $DEVICE_ID_KEY argument" }

    private val commands = DeviceCommandController(viewModelScope, deviceId, runAction, setEntityValue)
    private val day = MutableStateFlow(today())
    private val local = MutableStateFlow(FeederUiState(zone = clock.zone, today = day.value))

    private val pages = day.flatMapLatest { observeFeeder(deviceId, it) }
        .shareIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), replay = 1)

    val uiState: StateFlow<FeederUiState> = combine(
        pages,
        observeLiveUpdates(),
        commands.state,
        local,
    ) { page, live, commands, state ->
        state.copy(page = page, loaded = true, liveUnreachable = live.isUnreachable(), commands = commands)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), local.value)

    init {
        onRefresh()
        viewModelScope.launch {
            pages
                .map { page -> page?.device?.let { it.entity(SCHEDULE_KEY)?.value to it.entity(ACTIVE_KEY)?.value } }
                .filterNotNull()
                .distinctUntilChanged()
                .drop(1)
                .collect { feeding.refresh(deviceId) }
        }
    }

    fun onRefresh() {
        if (local.value.refreshing) return
        local.update { it.copy(refreshing = true) }
        viewModelScope.launch {
            day.value = today()
            val result = coroutineScope {
                val page = async { refreshPage(day.value) }
                val plan = async { feeding.refresh(deviceId) }
                listOf(page.await(), plan.await()).firstOrNull { it is Outcome.Failure }
            }
            local.update {
                it.copy(refreshing = false, today = day.value, refreshError = (result as? Outcome.Failure)?.error)
            }
        }
    }

    /**
     * A button of the action sheet or of a hotspot's explanation (which it closes). `feed` is
     * feed now, with the portions chosen on the page.
     */
    fun onAction(action: DeviceAction) {
        local.update { it.copy(openPart = null) }
        if (action.key == FEED_KEY) onFeedNow() else commands.onAction(action)
    }

    fun onConfirmAction() = commands.onConfirm()

    fun onDismissConfirmation() = commands.onDismissConfirmation()

    fun onDismissFeedback() = commands.onDismissFeedback()

    fun onHotspot(part: DevicePart?) = local.update { it.copy(openPart = part) }

    /** A hopper's feed-now portions, within its setting's bounds. */
    fun onPortions(hopperIndex: Int, portions: Int) {
        val hoppers = page()?.manualFeed?.hoppers ?: return
        val hopper = hoppers.getOrNull(hopperIndex) ?: return
        local.update { state ->
            val chosen = (state.chosenPortions ?: hoppers.map { it.portions }).toMutableList()
            chosen[hopperIndex] = portions.coerceIn(hopper.min, hopper.max)
            state.copy(chosenPortions = chosen, nothingChosen = false)
        }
    }

    /** Feeds now: the hopper settings that changed first, then the feed button (FeederRules.feedNow). */
    fun onFeedNow() {
        val page = page() ?: return
        if (FEED_KEY in commands.state.value.sending) return
        val chosen = local.value.chosenPortions ?: page.manualFeed.hoppers.map { it.portions }
        when (val plan = FeederRules.feedNow(page.manualFeed, chosen)) {
            FeedNow.NothingChosen -> local.update { it.copy(nothingChosen = true) }

            FeedNow.NoFeedAction -> Unit

            is FeedNow.Steps -> viewModelScope.launch {
                plan.settings.forEach { (hopper, count) ->
                    val entity = page.device.entity(hopper.entityKey) ?: return@launch
                    val written = commands.write(entity, EntityValue.Number(count.toDouble()), reportFailure = true)
                    if (written is Outcome.Failure) return@launch
                }
                local.update { it.copy(chosenPortions = null) }
                commands.onAction(plan.action)
            }
        }
    }

    /** Pauses or resumes the plan; the switch follows the server's answer. */
    fun onPlanActive(active: Boolean) {
        if (PLAN_ACTIVE_KEY in commands.state.value.sending) return
        val subject = if (active) PLAN_RESUMED else PLAN_PAUSED
        viewModelScope.launch {
            commands.report(subject, reportFailure = true) { feeding.setPlanActive(deviceId, active) }
        }
    }

    /** Opens the weekly plan's editor, once the plan has been read. */
    fun onEditPlan() {
        val plan = page()?.plan
        if (plan == null) {
            viewModelScope.launch { feeding.refresh(deviceId) }
            return
        }
        local.update { it.copy(planDraft = PlanDraft(plan.groups, plan.dualHopper), openPart = null) }
    }

    fun onPlanEdit(edit: PlanEdit) = local.update { state -> state.copy(planDraft = state.planDraft?.apply(edit)) }

    fun onDismissPlan() = local.update { it.copy(planDraft = null) }

    /** Saves the plan being edited, unless it has something to fix, which it then shows. */
    fun onSavePlan() {
        val draft = local.value.planDraft ?: return
        if (draft.problems.isNotEmpty()) {
            local.update { it.copy(planDraft = draft.copy(checked = true)) }
            return
        }
        submit(
            subject = PLAN_SAVED,
            write = { feeding.savePlan(deviceId, draft.groups) },
            onSaved = { local.update { it.copy(planDraft = null) } },
            onRefused = { error -> local.update { it.copy(planDraft = it.planDraft?.copy(refusal = error)) } },
        )
    }

    /** Opens the one-off feed form: in an hour, on the quarter hour, one portion. */
    fun onAddFeed() {
        val page = page() ?: return
        val inAnHour = LocalDateTime.now(clock).plusHours(1)
        val start = inAnHour.truncatedTo(ChronoUnit.HOURS)
            .plusMinutes(QUARTER * ((inAnHour.minute + QUARTER - 1) / QUARTER).toLong())
        val portions = if (page.dualHopper) Portions.Dual(1, 0) else Portions.Single(1)
        local.update {
            it.copy(feedDraft = FeedDraft(start.toLocalDate(), start.toLocalTime(), portions), openPart = null)
        }
    }

    fun onFeedDraft(draft: FeedDraft) = local.update { state ->
        val checked = draft.copy(problems = FeedingPlanRules.feedProblems(draft.request, clock.instant(), clock.zone))
        state.copy(feedDraft = checked.copy(refusal = null))
    }

    fun onDismissFeed() = local.update { it.copy(feedDraft = null) }

    /** Adds the one-off feed being edited, unless it has something to fix, which it then shows. */
    fun onSaveFeed() {
        val draft = local.value.feedDraft ?: return
        val problems = FeedingPlanRules.feedProblems(draft.request, clock.instant(), clock.zone)
        if (problems.isNotEmpty()) {
            local.update { it.copy(feedDraft = draft.copy(checked = true, problems = problems)) }
            return
        }
        submit(
            subject = FEED_ADDED,
            write = { feeding.addDeferredFeed(deviceId, draft.request) },
            onSaved = { local.update { it.copy(feedDraft = null) } },
            onRefused = { error -> local.update { it.copy(feedDraft = it.feedDraft?.copy(refusal = error)) } },
        )
    }

    fun onCancelFeed(feedId: String) {
        val subject = CommandSubject.Labelled(
            "$FEED_CANCEL_KEY:$feedId",
            "Cancel one-off feed",
            R.string.feeder_change_feed_cancelled,
        )
        if (subject.key in commands.state.value.sending) return
        viewModelScope.launch {
            commands.report(subject, reportFailure = true) { feeding.cancelDeferredFeed(deviceId, feedId) }
        }
    }

    private fun page(): FeederPage? = uiState.value.page

    /** A write from a form: success closes it and says so in the banner; a refusal stays in the form. */
    private fun submit(
        subject: CommandSubject,
        write: suspend () -> Outcome<CommandDelivery>,
        onSaved: () -> Unit,
        onRefused: (DomainError) -> Unit,
    ) {
        if (subject.key in commands.state.value.sending) return
        viewModelScope.launch {
            when (val result = commands.report(subject, reportFailure = false, request = write)) {
                is Outcome.Success -> onSaved()
                is Outcome.Failure -> onRefused(result.error)
            }
        }
    }

    companion object {
        private const val STOP_TIMEOUT_MS = 5_000L
        private const val QUARTER = 15

        /** The stored schedule and its pause switch: a change re-reads the plan and the feeds. */
        private const val SCHEDULE_KEY = "feeding_schedule"
        private const val ACTIVE_KEY = "feeding_schedule_active"

        const val FEED_KEY = "feed"
        const val PLAN_ACTIVE_KEY = "page:plan_active"
        const val PLAN_SAVE_KEY = "page:plan_save"
        const val FEED_ADD_KEY = "page:feed_add"
        const val FEED_CANCEL_KEY = "page:feed_cancel"

        val PLAN_PAUSED = CommandSubject.Labelled(PLAN_ACTIVE_KEY, "Plan paused", R.string.feeder_change_plan_paused)
        val PLAN_RESUMED = CommandSubject.Labelled(PLAN_ACTIVE_KEY, "Plan resumed", R.string.feeder_change_plan_resumed)
        val PLAN_SAVED = CommandSubject.Labelled(PLAN_SAVE_KEY, "Feeding plan", R.string.feeder_change_plan_saved)
        val FEED_ADDED = CommandSubject.Labelled(FEED_ADD_KEY, "One-off feed", R.string.feeder_change_feed_added)
    }
}
