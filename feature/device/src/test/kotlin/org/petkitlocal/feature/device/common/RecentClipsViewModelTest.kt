package org.petkitlocal.feature.device.common

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.video.ObserveRecentClipsUseCase
import org.petkitlocal.core.domain.video.RefreshRecentClipsUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY

@OptIn(ExperimentalCoroutinesApi::class)
class RecentClipsViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val camera = Household.cameraLitterBox.id
    private val devices = FakeDeviceRepository(Household.devices)
    private val timeline = FakeTimelineRepository().apply { clips.value = mapOf(camera to Household.cameraClipItems) }

    private fun viewModel(deviceId: Long = camera) = RecentClipsViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to deviceId)),
        ObserveDeviceUseCase(devices),
        ObserveRecentClipsUseCase(timeline),
        RefreshRecentClipsUseCase(timeline),
        Household.clock,
    )

    private fun TestScope.collect(viewModel: RecentClipsViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    @Test
    fun `a camera's page shows its cached clips at once and asks the server for the newest`() =
        runTest(main.dispatcher) {
            val gate = CompletableDeferred<Unit>().also { timeline.clipsGate = it }
            val viewModel = viewModel()
            collect(viewModel)
            advanceUntilIdle()

            with(viewModel.state.value) {
                assertEquals(Household.cameraClips, clips)
                assertTrue(refreshing)
                assertEquals(Household.zone, zone)
                assertEquals(Household.today, today)
            }

            gate.complete(Unit)
            advanceUntilIdle()
            assertFalse(viewModel.state.value.refreshing)
            assertTrue(viewModel.state.value.refreshed)
            assertEquals(listOf(camera), timeline.refreshedClips)
        }

    @Test
    fun `a device without a camera never asks for clips`() = runTest(main.dispatcher) {
        val viewModel = viewModel(deviceId = Household.litterBox.id)
        collect(viewModel)
        advanceUntilIdle()
        viewModel.onRefresh()
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), timeline.refreshedClips)
        assertFalse(viewModel.state.value.refreshed)
    }

    @Test
    fun `with nothing cached, a failed refresh is shown until a retry answers`() = runTest(main.dispatcher) {
        timeline.clips.value = emptyMap()
        timeline.clipsResult = Outcome.Failure(DomainError.Network("down"))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.failedEmpty)

        timeline.clipsResult = Outcome.Success(Unit)
        val gate = CompletableDeferred<Unit>().also { timeline.clipsGate = it }
        viewModel.onRefresh()
        advanceUntilIdle()
        // Retrying: the skeleton again, the error gone.
        assertTrue(viewModel.state.value.loading)

        gate.complete(Unit)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.empty)
        assertEquals(listOf(camera, camera), timeline.refreshedClips)
    }

    @Test
    fun `one refresh at a time`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>().also { timeline.clipsGate = it }
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        viewModel.onRefresh()
        viewModel.onRefresh()
        gate.complete(Unit)
        advanceUntilIdle()

        assertEquals(listOf(camera), timeline.refreshedClips)
    }

    @Test
    fun `a failure after a refresh that answered keeps the server's word`() = runTest(main.dispatcher) {
        timeline.clips.value = emptyMap()
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        timeline.clipsResult = Outcome.Failure(DomainError.Network("down"))

        viewModel.onRefresh()
        advanceUntilIdle()

        // Still "no clip yet", not an error over a camera known to have none.
        assertTrue(viewModel.state.value.empty)
        assertFalse(viewModel.state.value.failedEmpty)
    }
}
