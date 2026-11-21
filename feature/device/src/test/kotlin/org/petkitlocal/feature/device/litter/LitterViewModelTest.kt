package org.petkitlocal.feature.device.litter

import androidx.lifecycle.SavedStateHandle
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CompletableDeferred
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
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.activity.ActivityAvatar
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.RefreshDevicePageUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
import org.petkitlocal.core.domain.litter.ObserveLitterBoxUseCase
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery
import org.petkitlocal.core.domain.repository.LiveUpdatesStatus
import org.petkitlocal.core.testing.FakeDeviceCommandRepository
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeLiveUpdatesRepository
import org.petkitlocal.core.testing.FakePetRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY
import org.petkitlocal.feature.device.common.CommandResult

@OptIn(ExperimentalCoroutinesApi::class)
class LitterViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val devices = FakeDeviceRepository(listOf(LitterFixtures.pura))
    private val pets = FakePetRepository(Household.pets)
    private val timeline = FakeTimelineRepository().apply {
        days.value = mapOf(
            Household.today to LitterFixtures.todayItems,
            Household.today.minusDays(1) to LitterFixtures.yesterdayItems,
        )
    }
    private val live = FakeLiveUpdatesRepository()
    private val commands = FakeDeviceCommandRepository().apply { result = Outcome.Success(CommandDelivery.Queued) }

    private fun viewModel() = LitterViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to 1002L)),
        ObserveLitterBoxUseCase(devices, pets, timeline, Household.clock),
        ObserveLiveUpdatesUseCase(live),
        RefreshDevicePageUseCase(devices, pets, timeline),
        RunDeviceActionUseCase(commands),
        SetEntityValueUseCase(commands),
        TodayUseCase(Household.clock),
        Household.clock,
    )

    private fun TestScope.collect(viewModel: LitterViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun action(key: String) = LitterFixtures.actionsT4.first { it.key == key }

    @Test
    fun `shows the cached box at once and refreshes it, today and yesterday, on opening`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>().also { devices.gate = it }
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.loaded)
        assertTrue(state.refreshing)
        assertEquals("Pura Max", state.page?.device?.modelName)
        assertEquals(4, state.page?.today?.visits)
        assertEquals(Household.zone, state.zone)
        // Shown, so the live socket is kept open.
        assertEquals(1, live.collectors.value)

        gate.complete(Unit)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.refreshing)
        assertEquals(setOf(Household.today, Household.today.minusDays(1)), timeline.refreshedDays.toSet())
    }

    @Test
    fun `the day's activity is one row per visit, its follow-ups under it, the pet's photo when it has one`() =
        runTest(main.dispatcher) {
            pets.pets.value = LitterFixtures.pets
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            val activity = viewModel.uiState.value.page!!.activity
            assertEquals(listOf(16L, 14L, 13L, 11L, 10L, 9L), activity.map { it.item.id })
            // The weight check and the mechanism's start are left out; the cleaning and spray stay.
            assertEquals(listOf(163L, 164L), activity[0].steps.map { it.id })
            assertEquals(listOf(false, true), activity[1].steps.map { it.failed })
            assertEquals(
                listOf(
                    ActivityAvatar.Pet("Mochi", LitterFixtures.pets[0].photoUrl, 0),
                    ActivityAvatar.Pet("Tigrou", null, 1),
                    null,
                    null,
                    ActivityAvatar.Pet("Tigrou", null, 1),
                    ActivityAvatar.Unknown,
                ),
                activity.map { it.avatar },
            )
        }

    @Test
    fun `a failed refresh or an unreachable socket keeps the last known box and says so`() = runTest(main.dispatcher) {
        devices.refreshResult = Outcome.Failure(DomainError.Network("timeout"))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.offline)
        assertEquals(1002L, viewModel.uiState.value.page?.device?.id)

        devices.refreshResult = Outcome.Success(Unit)
        viewModel.onRefresh()
        live.current.value = LiveUpdatesStatus.Waiting(DomainError.Network("down"), 1.seconds)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.offline)
    }

    @Test
    fun `a plain action is sent at once and its banner says how it went out`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(action("cleaning_start"))
        runCurrent()

        assertEquals(
            listOf(FakeDeviceCommandRepository.Action(1002, "cleaning_start", confirm = false)),
            commands.actions,
        )
        assertEquals(CommandResult.Sent(CommandDelivery.Queued), viewModel.uiState.value.commands.feedback?.result)
        // No optimistic UI: the box still reads idle until its next report.
        assertEquals("idle", viewModel.uiState.value.page?.device?.entity("device_status")?.display)
    }

    @Test
    fun `a destructive action waits for confirmation, then sends confirm`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(action("dump_litter"))
        runCurrent()
        assertEquals("dump_litter", viewModel.uiState.value.commands.confirming?.key)
        assertTrue(commands.actions.isEmpty())

        viewModel.onDismissConfirmation()
        runCurrent()
        assertNull(viewModel.uiState.value.commands.confirming)
        assertTrue(commands.actions.isEmpty())

        viewModel.onAction(action("dump_litter"))
        viewModel.onConfirmAction()
        runCurrent()
        assertEquals(listOf(FakeDeviceCommandRepository.Action(1002, "dump_litter", confirm = true)), commands.actions)
    }

    @Test
    fun `a refusal carries the server's message`() = runTest(main.dispatcher) {
        commands.result = Outcome.Failure(DomainError.Validation(null, "refused", "box is busy"))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(action("deodorize"))
        runCurrent()

        val result = viewModel.uiState.value.commands.feedback?.result as CommandResult.Failed
        assertEquals("box is busy", (result.error as DomainError.Validation).message)
    }

    @Test
    fun `a mode chip writes its switch and changes nothing until the device reports`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onModeToggle("auto_work", false)
        runCurrent()

        assertEquals(
            listOf(FakeDeviceCommandRepository.Write(1002, "auto_work", EntityValue.Bool(false))),
            commands.writes,
        )
        assertEquals(true, viewModel.uiState.value.page?.modes?.single()?.on)
    }

    @Test
    fun `the pet filter and a hotspot's explanation are the page's own state`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onPetFilter(2)
        viewModel.onHotspot(DevicePart.Litter)
        runCurrent()
        val state = viewModel.uiState.value
        assertEquals(2L, state.petFilter)
        assertEquals("litter_percent", state.openHotspot?.entityKey)

        // An action from the explanation closes it.
        viewModel.onAction(action("level_litter"))
        runCurrent()
        assertNull(viewModel.uiState.value.openPart)
        assertEquals("level_litter", commands.actions.single().key)
    }

    @Test
    fun `a device the server no longer has shows as gone`() = runTest(main.dispatcher) {
        devices.devices.value = emptyList()
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.loaded)
        assertNull(viewModel.uiState.value.page)
    }

    @Test
    fun `the Pura Air light writes the opposite of what the box reports, and changes only with it`() =
        runTest(main.dispatcher) {
            devices.devices.value = listOf(LitterFixtures.puraWithK3)
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()
            val light = viewModel.uiState.value.page!!.actions.first { it.key == "k3_light" }
            assertEquals(true, light.switchedOn)

            viewModel.onAction(light)
            runCurrent()
            assertEquals(
                listOf(FakeDeviceCommandRepository.Write(1002, "k3_light", EntityValue.Bool(false))),
                commands.writes,
            )
            assertTrue(commands.actions.isEmpty(), "a switch is no action the server runs")
            // No optimistic UI: still on until the box reports it off.
            assertEquals(true, viewModel.uiState.value.page!!.actions.first { it.key == "k3_light" }.switchedOn)

            // The K3 turns its light off (by itself, or as asked): the box's report says so.
            devices.devices.value = listOf(
                LitterFixtures.puraWithK3.copy(
                    entities = LitterFixtures.puraWithK3.entities.map {
                        if (it.key == "k3_light") it.copy(value = EntityValue.Bool(false)) else it
                    },
                ),
            )
            advanceUntilIdle()
            assertEquals(false, viewModel.uiState.value.page!!.actions.first { it.key == "k3_light" }.switchedOn)
        }

    @Test
    fun `a refused Pura Air light says the server's reason`() = runTest(main.dispatcher) {
        devices.devices.value = listOf(LitterFixtures.puraWithK3)
        commands.result = Outcome.Failure(DomainError.Server(409, "accessory_unreachable", "the K3 is out of range"))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(viewModel.uiState.value.page!!.actions.first { it.key == "k3_light" })
        runCurrent()
        val result = viewModel.uiState.value.commands.feedback?.result
        assertEquals(
            CommandResult.Failed(DomainError.Server(409, "accessory_unreachable", "the K3 is out of range")),
            result,
        )
    }

    @Test
    fun `a box without a linked K3 offers no Pura Air light`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.page!!.actions.none { it.key == "k3_light" })
    }
}
