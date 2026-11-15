package org.petkitlocal.feature.device.fountain

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
import org.petkitlocal.core.domain.device.ObserveLiveUpdatesUseCase
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.domain.devicepage.PartReading
import org.petkitlocal.core.domain.devicepage.RefreshDevicePageUseCase
import org.petkitlocal.core.domain.fountain.ObserveFountainUseCase
import org.petkitlocal.core.domain.home.TodayUseCase
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
class FountainViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val devices =
        FakeDeviceRepository(listOf(FountainFixtures.w7h, FountainFixtures.w5, FountainFixtures.relay))
    private val pets = FakePetRepository(Household.pets)
    private val timeline = FakeTimelineRepository().apply {
        days.value = mapOf(
            Household.today to FountainFixtures.todayItems,
            Household.today.minusDays(1) to FountainFixtures.yesterdayItems,
        )
    }
    private val live = FakeLiveUpdatesRepository()
    private val commands = FakeDeviceCommandRepository().apply { result = Outcome.Success(CommandDelivery.Mqtt) }

    private fun viewModel(id: Long = 3001L) = FountainViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to id)),
        ObserveFountainUseCase(devices, pets, timeline, Household.clock),
        ObserveLiveUpdatesUseCase(live),
        RefreshDevicePageUseCase(devices, pets, timeline),
        RunDeviceActionUseCase(commands),
        SetEntityValueUseCase(commands),
        TodayUseCase(Household.clock),
        Household.clock,
    )

    private fun TestScope.collect(viewModel: FountainViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    @Test
    fun `shows the cached fountain at once and refreshes it, today and yesterday, on opening`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { devices.gate = it }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            val state = viewModel.uiState.value
            assertTrue(state.loaded)
            assertTrue(state.refreshing)
            assertEquals("EverSweet Ultra AI", state.page?.device?.model)
            assertEquals(5, state.page?.today?.drinks)
            assertEquals(3, state.page?.yesterday?.drinks)
            // Shown, so the live socket is kept open.
            assertEquals(1, live.collectors.value)

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.uiState.value.refreshing)
            assertEquals(setOf(Household.today, Household.today.minusDays(1)), timeline.refreshedDays.toSet())
        }

    @Test
    fun `a Bluetooth fountain names its relay, and follows a report the relay brings`() = runTest(main.dispatcher) {
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()

        val page = viewModel.uiState.value.page!!
        assertEquals("YumShare Dual-Hopper", page.relay?.model)
        assertFalse(page.recordsDrinks)
        assertEquals(14_400, page.pumpTodaySec)

        val reset = FountainFixtures.w5.copy(
            entities = FountainFixtures.w5.entities.map {
                if (it.key == "w5_filter") it.copy(value = EntityValue.Number(100.0)) else it
            },
        )
        devices.devices.value = listOf(FountainFixtures.w7h, reset, FountainFixtures.relay)
        runCurrent()
        val filter = viewModel.uiState.value.page!!.hotspots.single { it.part == DevicePart.Filter }
        assertEquals(PartReading.Percent(100), filter.reading)
    }

    @Test
    fun `the filter reset waits for confirmation, then sends confirm`() = runTest(main.dispatcher) {
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()
        val reset = viewModel.uiState.value.page!!.actions.single()

        viewModel.onHotspot(DevicePart.Filter)
        viewModel.onAction(reset)
        runCurrent()
        // The explanation closes; the confirmation opens; nothing is sent yet.
        assertNull(viewModel.uiState.value.openPart)
        assertEquals("w5_reset_filter", viewModel.uiState.value.commands.confirming?.key)
        assertTrue(commands.actions.isEmpty())

        viewModel.onConfirmAction()
        runCurrent()
        assertEquals(
            listOf(FakeDeviceCommandRepository.Action(3002, "w5_reset_filter", confirm = true)),
            commands.actions,
        )
        assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.uiState.value.commands.feedback?.result)
    }

    @Test
    fun `a plain action is sent at once and changes nothing until the device reports`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onAction(FountainFixtures.actionsW7h.first { it.key == "fountain_refill" })
        runCurrent()

        assertEquals(
            listOf(FakeDeviceCommandRepository.Action(3001, "fountain_refill", confirm = false)),
            commands.actions,
        )
        assertEquals(CommandResult.Sent(CommandDelivery.Mqtt), viewModel.uiState.value.commands.feedback?.result)
    }

    @Test
    fun `mode chips write a switch, or the smart or normal option of the mode`() = runTest(main.dispatcher) {
        val viewModel = viewModel(3002L)
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onModeToggle("w5_mode", true)
        runCurrent()
        viewModel.onModeToggle("w5_dnd", true)
        runCurrent()
        // A key that is not one of the page's chips writes nothing.
        viewModel.onModeToggle("w5_light", false)
        runCurrent()

        assertEquals(
            listOf(
                FakeDeviceCommandRepository.Write(3002, "w5_mode", EntityValue.Text("smart")),
                FakeDeviceCommandRepository.Write(3002, "w5_dnd", EntityValue.Bool(true)),
            ),
            commands.writes,
        )
        // No optimistic UI: smart mode still reads off until the fountain reports it.
        assertEquals(false, viewModel.uiState.value.page?.modes?.first()?.on)
    }

    @Test
    fun `the pet filter is the page's own state, and an unreachable server is said`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onPetFilter(2)
        live.current.value = LiveUpdatesStatus.Waiting(DomainError.Network("down"), 1.seconds)
        runCurrent()
        assertEquals(2L, viewModel.uiState.value.petFilter)
        assertTrue(viewModel.uiState.value.offline)
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
}
