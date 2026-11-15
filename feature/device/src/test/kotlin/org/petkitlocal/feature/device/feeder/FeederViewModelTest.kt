package org.petkitlocal.feature.device.feeder

import androidx.lifecycle.SavedStateHandle
import java.time.DayOfWeek
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.devicepage.RefreshDevicePageUseCase
import org.petkitlocal.core.domain.feeder.FeedProblem
import org.petkitlocal.core.domain.feeder.MealState
import org.petkitlocal.core.domain.feeder.ObserveFeederUseCase
import org.petkitlocal.core.domain.feeder.PlanProblem
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.model.Portions
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.testing.FakeDeviceCommandRepository
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeFeedingRepository
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.common.CommandResult
import org.petkitlocal.feature.device.common.CommandSubject

@OptIn(ExperimentalCoroutinesApi::class)
class FeederViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val devices = FakeDeviceRepository(listOf(FeederFixtures.solo, FeederFixtures.dual))
    private val timeline = FakeTimelineRepository().apply {
        days.value = mapOf(Household.today to FeederFixtures.todayItems)
    }
    private val feeding = FakeFeedingRepository().apply {
        serverPlans[2001] = FeederFixtures.soloPlan
        serverPlans[2004] = FeederFixtures.dualPlan
        serverFeeds[2001] = listOf(FeederFixtures.tomorrowFeed)
        serverFeeds[2004] = emptyList()
    }
    private val live = FakeLiveUpdatesRepository()
    private val commands = FakeDeviceCommandRepository().apply { result = Outcome.Success(CommandDelivery.Queued) }

    private fun viewModel(id: Long) = FeederViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to id)),
        ObserveFeederUseCase(devices, feeding, timeline, Household.clock),
        ObserveLiveUpdatesUseCase(live),
        RefreshDevicePageUseCase(devices, FakePetRepository(), timeline),
        feeding,
        RunDeviceActionUseCase(commands),
        SetEntityValueUseCase(commands),
        TodayUseCase(Household.clock),
        Household.clock,
    )

    private val FeederViewModel.feedback get() = uiState.value.commands.feedback

    private fun TestScope.open(id: Long): FeederViewModel = viewModel(id).also { viewModel ->
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
        advanceUntilIdle()
    }

    @Test
    fun `opening reads the devices, the day and the plan, and shows today's meals`() = runTest(main.dispatcher) {
        val viewModel = open(2001)

        val page = viewModel.uiState.value.page!!
        assertEquals(listOf(2001L), feeding.refreshed)
        assertEquals(1, devices.refreshCount)
        assertEquals(listOf(MealState.Served, MealState.Upcoming, MealState.Upcoming), page.today.map { it.state })
        assertEquals(LocalTime.of(12, 0), page.nextMeal?.at?.atZone(Household.zone)?.toLocalTime())
        assertEquals(listOf("d_1"), page.deferredFeeds?.map { it.id })
        assertFalse(viewModel.uiState.value.refreshing)
    }

    @Test
    fun `a change of the stored schedule, from anywhere, reads the plan again`() = runTest(main.dispatcher) {
        open(2001)
        val changed = FeederFixtures.solo.copy(
            entities = FeederFixtures.solo.entities.map {
                if (it.key == "feeding_schedule") it.copy(value = EntityValue.Text("v2")) else it
            },
        )

        devices.devices.value = listOf(changed, FeederFixtures.dual)
        advanceUntilIdle()

        assertEquals(listOf(2001L, 2001L), feeding.refreshed)
    }

    @Test
    fun `feed now on a single hopper presses feed and reports the delivery`() = runTest(main.dispatcher) {
        val viewModel = open(2001)

        viewModel.onFeedNow()
        runCurrent()

        assertEquals(CommandSubject.Action("feed", "Feed"), viewModel.feedback?.subject)
        assertEquals(CommandResult.Sent(CommandDelivery.Queued), viewModel.feedback?.result)
        assertEquals(listOf(FakeDeviceCommandRepository.Action(2001, "feed", false)), commands.actions)
        assertTrue(commands.writes.isEmpty())
    }

    @Test
    fun `feed now on a dual hopper writes the changed portions, then the hopper's button`() = runTest(main.dispatcher) {
        val viewModel = open(2004)

        viewModel.onPortions(0, 0)
        advanceUntilIdle()
        viewModel.onPortions(1, 3)
        advanceUntilIdle()
        viewModel.onPortions(1, 99) // past the setting's max: 10
        advanceUntilIdle()
        assertEquals(listOf(0, 10), viewModel.uiState.value.portions)
        viewModel.onPortions(1, 3)
        advanceUntilIdle()
        viewModel.onFeedNow()
        advanceUntilIdle()

        assertEquals(
            listOf(FakeDeviceCommandRepository.Write(2004, "hopper2_portions", EntityValue.Number(3.0))),
            commands.writes,
        )
        assertEquals(listOf("feed_hopper_2"), commands.actions.map { it.key })
        // Sent: the page follows the feeder's settings again.
        assertNull(viewModel.uiState.value.chosenPortions)
    }

    @Test
    fun `feed now with nothing chosen says so and sends nothing`() = runTest(main.dispatcher) {
        val viewModel = open(2004)

        viewModel.onPortions(0, 0)
        advanceUntilIdle()
        viewModel.onPortions(1, 0)
        advanceUntilIdle()
        viewModel.onFeedNow()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.nothingChosen)
        assertTrue(commands.actions.isEmpty())
        viewModel.onPortions(0, 1)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.nothingChosen)
    }

    @Test
    fun `a refused portion setting stops before the feed button`() = runTest(main.dispatcher) {
        commands.result = Outcome.Failure(DomainError.Validation(null, "refused", "out of bounds"))
        val viewModel = open(2004)

        viewModel.onPortions(0, 2)
        runCurrent()
        viewModel.onFeedNow()
        runCurrent()

        assertEquals(CommandSubject.Setting("hopper1_portions", "hopper1_portions"), viewModel.feedback?.subject)
        assertTrue(viewModel.feedback?.result is CommandResult.Failed)
        assertTrue(commands.actions.isEmpty())
    }

    @Test
    fun `pausing goes through the plan and reports it`() = runTest(main.dispatcher) {
        val viewModel = open(2001)

        viewModel.onPlanActive(false)
        runCurrent()

        assertEquals(FeederViewModel.PLAN_PAUSED, viewModel.feedback?.subject)
        assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.feedback?.result)
        advanceUntilIdle()
        assertEquals(listOf(false), feeding.activeSet)
        assertEquals(false, viewModel.uiState.value.page?.plan?.active)
    }

    @Test
    fun `the plan editor shows its problems once saving was tried, then saves`() = runTest(main.dispatcher) {
        val viewModel = open(2001)

        viewModel.onEditPlan()
        advanceUntilIdle()
        viewModel.onPlanEdit(PlanEdit.AddGroup) // Every day is taken: a group with no day.
        advanceUntilIdle()
        assertEquals(emptyList<PlanProblem>(), viewModel.uiState.value.planDraft?.shownProblems)
        viewModel.onSavePlan()
        advanceUntilIdle()
        assertEquals(listOf(PlanProblem.NoDays(1)), viewModel.uiState.value.planDraft?.shownProblems)
        assertTrue(feeding.saved.isEmpty())

        viewModel.onPlanEdit(PlanEdit.RemoveGroup(1))
        advanceUntilIdle()
        viewModel.onPlanEdit(PlanEdit.ToggleDay(0, DayOfWeek.SUNDAY))
        advanceUntilIdle()
        viewModel.onPlanEdit(PlanEdit.MealPortions(0, 2, hopper = 1, portions = 3))
        advanceUntilIdle()
        viewModel.onSavePlan()
        runCurrent()
        assertEquals(FeederViewModel.PLAN_SAVED, viewModel.feedback?.subject)

        advanceUntilIdle()
        assertNull(viewModel.uiState.value.planDraft)
        val sent = feeding.saved.single().single()
        assertFalse(DayOfWeek.SUNDAY in sent.days)
        assertEquals(Portions.Single(3), sent.meals[2].portions)
    }

    @Test
    fun `a plan the server refuses stays open with its reason`() = runTest(main.dispatcher) {
        feeding.writeResult =
            Outcome.Failure(DomainError.Validation(null, "invalid_schedule", "not a valid feeding schedule"))
        val viewModel = open(2001)

        viewModel.onEditPlan()
        advanceUntilIdle()
        viewModel.onSavePlan()
        advanceUntilIdle()

        val draft = viewModel.uiState.value.planDraft
        assertNotNull(draft)
        assertEquals("invalid_schedule", (draft!!.refusal as DomainError.Validation).code)
    }

    @Test
    fun `a one-off feed is checked, added, then cancelled`() = runTest(main.dispatcher) {
        val viewModel = open(2004)

        viewModel.onAddFeed()
        advanceUntilIdle()
        val draft = viewModel.uiState.value.feedDraft!!
        // 10:00 now: in an hour, on the quarter hour, one portion from the first hopper.
        assertEquals(LocalTime.of(11, 0), draft.time)
        assertEquals(Portions.Dual(1, 0), draft.portions)

        viewModel.onFeedDraft(draft.copy(time = LocalTime.of(9, 0), portions = Portions.Dual(0, 0)))
        advanceUntilIdle()
        viewModel.onSaveFeed()
        advanceUntilIdle()
        assertEquals(
            setOf(FeedProblem.InThePast, FeedProblem.NothingServed),
            viewModel.uiState.value.feedDraft?.shownProblems,
        )
        assertTrue(feeding.added.isEmpty())

        viewModel.onFeedDraft(draft)
        advanceUntilIdle()
        viewModel.onSaveFeed()
        advanceUntilIdle()
        assertNull(viewModel.uiState.value.feedDraft)
        assertEquals(listOf(draft.request), feeding.added)
        val id = viewModel.uiState.value.page!!.deferredFeeds!!.single().id

        viewModel.onCancelFeed(id)
        advanceUntilIdle()
        assertEquals(listOf(id), feeding.cancelled)
        assertEquals(emptyList<Any>(), viewModel.uiState.value.page!!.deferredFeeds)
    }

    @Test
    fun `a plan not read yet is read again rather than edited blind`() = runTest(main.dispatcher) {
        feeding.serverPlans.remove(2001)
        val viewModel = open(2001)

        viewModel.onEditPlan()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.planDraft)
        assertEquals(2, feeding.refreshed.size)
    }
}
