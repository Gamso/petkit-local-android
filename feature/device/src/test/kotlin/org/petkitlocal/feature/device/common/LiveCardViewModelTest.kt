package org.petkitlocal.feature.device.common

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.video.LatestStill
import org.petkitlocal.core.domain.video.ObserveLatestStillUseCase
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.feature.device.DEVICE_ID_KEY

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCardViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val camera = Household.cameraLitterBox.id
    private val timeline = FakeTimelineRepository()

    private fun viewModel() = LiveCardViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to camera)),
        ObserveLatestStillUseCase(timeline),
        Household.clock,
    )

    private fun TestScope.collect(viewModel: LiveCardViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.state.collect {} }
    }

    /** The still [item] gives the card: its poster's thumbnail, and when it happened. */
    private fun stillOf(item: TimelineItem) = LatestStill(item.media!!.files.first().thumbnailUrl!!, item.shownAt)

    @Test
    fun `the card shows the newest cached picture with when it was taken`() = runTest(main.dispatcher) {
        val (newest, older) = Household.cameraClipItems
        timeline.withMedia.value = mapOf(camera to listOf(newest, older))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        with(viewModel.state.value) {
            assertEquals(stillOf(newest), still)
            assertEquals(Household.zone, zone)
            assertEquals(Household.today, today)
        }
    }

    @Test
    fun `a camera with no picture yet has a card without a still`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()

        assertNull(viewModel.state.value.still)
        assertEquals(Household.today, viewModel.state.value.today)
    }

    @Test
    fun `a refresh that caches a newer picture moves the card to it`() = runTest(main.dispatcher) {
        val (newest, older) = Household.cameraClipItems
        timeline.withMedia.value = mapOf(camera to listOf(older))
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertEquals(stillOf(older), viewModel.state.value.still)

        // What the page's recent clips refresh, or a live update, writes into the cache.
        timeline.withMedia.value = mapOf(camera to listOf(newest, older))
        advanceUntilIdle()

        assertEquals(stillOf(newest), viewModel.state.value.still)
    }
}
