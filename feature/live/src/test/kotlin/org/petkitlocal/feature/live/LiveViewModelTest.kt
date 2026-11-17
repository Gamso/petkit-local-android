package org.petkitlocal.feature.live

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
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
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.repository.SnapshotRepository
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.domain.video.ObserveRecentClipsUseCase
import org.petkitlocal.core.domain.video.RefreshRecentClipsUseCase
import org.petkitlocal.core.domain.video.SaveSnapshotUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension

@OptIn(ExperimentalCoroutinesApi::class)
class LiveViewModelTest {
    // Unconfined: every event reaches uiState at once; the waits still run on virtual time.
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val devices = FakeDeviceRepository(Household.devices)
    private val snapshots = FakeSnapshots()
    private val timeline = FakeTimelineRepository().apply { clips.value = mapOf(CAMERA to Household.cameraClipItems) }

    private fun viewModel(deviceId: Long = CAMERA) = LiveViewModel(
        SavedStateHandle(mapOf(LiveViewModel.DEVICE_ID_KEY to deviceId)),
        ObserveDeviceUseCase(devices),
        SaveSnapshotUseCase(snapshots, Household.clock),
        ObserveRecentClipsUseCase(timeline),
        RefreshRecentClipsUseCase(timeline),
        Household.clock,
        NoPlayers,
    )

    private fun TestScope.collect(viewModel: LiveViewModel) {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { viewModel.uiState.collect {} }
    }

    private fun TestScope.started(): LiveViewModel = viewModel().also {
        collect(it)
        it.onVisible()
        runCurrent()
    }

    @Test
    fun `a camera plays once the screen is visible, on the WebSocket, muted`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        assertEquals(LiveAvailability.Loading, viewModel.uiState.value.availability)
        collect(viewModel)
        runCurrent()

        val shown = viewModel.uiState.value
        assertEquals(LiveAvailability.Camera, shown.availability)
        assertEquals("Purobot Max Pro 2", shown.deviceName)
        assertTrue(shown.muted)
        assertNull(shown.playback, "nothing plays before the screen is visible")

        viewModel.onVisible()
        runCurrent()
        assertEquals(LivePlayback(CAMERA, LiveTransport.WebSocket, attempt = 1), viewModel.uiState.value.playback)

        viewModel.onPlaying(1)
        assertEquals(LivePhase.Playing, viewModel.uiState.value.session.phase)
        assertEquals(1, viewModel.uiState.value.playback?.attempt, "the same player keeps playing")
    }

    @Test
    fun `a device without a camera, or gone, gets no player`() = runTest(main.dispatcher) {
        val feeder = viewModel(Household.feeder.id).also { collect(it) }
        feeder.onVisible()
        runCurrent()
        assertEquals(LiveAvailability.NotACamera, feeder.uiState.value.availability)
        assertNull(feeder.uiState.value.playback)

        val gone = viewModel(GONE).also { collect(it) }
        gone.onVisible()
        runCurrent()
        assertEquals(LiveAvailability.Gone, gone.uiState.value.availability)
        assertNull(gone.uiState.value.playback)
    }

    @Test
    fun `close code 1013 moves to HLS at once, with a new player`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onPlaying(1)

        viewModel.onPlayerFailed(1, LiveFailure.TooSlow)

        assertEquals(LivePlayback(CAMERA, LiveTransport.Hls, attempt = 2), viewModel.uiState.value.playback)
    }

    @Test
    fun `a failure waits, then starts again by itself, but not while hidden`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onPlayerFailed(1, LiveFailure.Network("reset"))
        assertEquals(
            LivePhase.Waiting(LiveFailure.Network("reset"), LiveSession.RETRY_DELAY),
            viewModel.uiState.value.session.phase,
        )
        assertNull(viewModel.uiState.value.playback)

        advanceTimeBy(LiveSession.RETRY_DELAY + 1.milliseconds)
        assertEquals(LivePlayback(CAMERA, LiveTransport.WebSocket, attempt = 2), viewModel.uiState.value.playback)

        viewModel.onPlayerFailed(2, LiveFailure.StreamError("busy"))
        viewModel.onHidden()
        advanceUntilIdle()
        assertEquals(LivePhase.Idle, viewModel.uiState.value.session.phase)
        assertNull(viewModel.uiState.value.playback)

        viewModel.onVisible()
        runCurrent()
        // Two failed starts on the WebSocket: the next one is on HLS.
        assertEquals(LivePlayback(CAMERA, LiveTransport.Hls, attempt = 3), viewModel.uiState.value.playback)
    }

    @Test
    fun `a stopped stream waits for the user, whose retry starts on the WebSocket again`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onPlayerFailed(1, LiveFailure.TooSlow)
        viewModel.onPlayerFailed(2, LiveFailure.StreamEnded)
        advanceUntilIdle()
        assertEquals(LivePhase.Stopped(LiveFailure.StreamEnded), viewModel.uiState.value.session.phase)
        assertNull(viewModel.uiState.value.playback)

        viewModel.onRetry()
        assertEquals(LivePlayback(CAMERA, LiveTransport.WebSocket, attempt = 3), viewModel.uiState.value.playback)
    }

    @Test
    fun `the delay is the playing player's own, and goes with it`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onDelay(1, 300.milliseconds)
        assertNull(viewModel.uiState.value.delay, "not playing yet")

        viewModel.onPlaying(1)
        viewModel.onDelay(1, 300.milliseconds)
        viewModel.onDelay(0, 9_000.milliseconds)
        assertEquals(300.milliseconds, viewModel.uiState.value.delay)

        viewModel.onPlayerFailed(1, LiveFailure.TooSlow)
        assertNull(viewModel.uiState.value.delay)
        viewModel.onPlaying(1)
        assertEquals(LivePhase.Starting, viewModel.uiState.value.session.phase, "an old player's frame changes nothing")
    }

    @Test
    fun `mute and full screen follow the user`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onToggleMute()
        viewModel.onFullscreen(true)
        assertFalse(viewModel.uiState.value.muted)
        assertTrue(viewModel.uiState.value.fullscreen)

        viewModel.onToggleMute()
        viewModel.onFullscreen(false)
        assertTrue(viewModel.uiState.value.muted)
        assertFalse(viewModel.uiState.value.fullscreen)
    }

    @Test
    fun `a snapshot is saved under the device's name, and said for a few seconds`() = runTest(main.dispatcher) {
        val viewModel = started()
        val gate = CompletableDeferred<Unit>().also { snapshots.gate = it }
        viewModel.onSnapshot(byteArrayOf(1, 2))
        assertTrue(viewModel.uiState.value.savingSnapshot)
        viewModel.onSnapshot(byteArrayOf(3))
        gate.complete(Unit)
        runCurrent()

        // One at a time: the second one, taken while the first was being saved, is dropped.
        assertEquals(listOf("Purobot Max Pro 2"), snapshots.saved.map { it.substringBefore(" 20") })
        assertFalse(viewModel.uiState.value.savingSnapshot)
        assertEquals(SnapshotResult.Saved, viewModel.uiState.value.snapshot)

        advanceUntilIdle()
        assertNull(viewModel.uiState.value.snapshot)
    }

    @Test
    fun `no frame, or storage refusing it, is said too`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onSnapshot(null)
        assertEquals(SnapshotResult.NoPicture, viewModel.uiState.value.snapshot)
        assertTrue(snapshots.saved.isEmpty())

        snapshots.accept = false
        viewModel.onSnapshot(byteArrayOf(1))
        runCurrent()
        assertEquals(SnapshotResult.Failed, viewModel.uiState.value.snapshot)
    }

    @Test
    fun `the camera's recent clips show from the cache and are asked for once on opening`() = runTest(main.dispatcher) {
        val gate = CompletableDeferred<Unit>().also { timeline.clipsGate = it }
        val viewModel = viewModel()
        collect(viewModel)
        runCurrent()

        with(viewModel.uiState.value.clips) {
            assertEquals(Household.cameraClips, clips)
            assertTrue(refreshing)
            assertEquals(Household.today, today)
            assertEquals(Household.zone, zone)
        }
        viewModel.onRefreshClips()
        gate.complete(Unit)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.clips.refreshed)
        assertFalse(viewModel.uiState.value.clips.refreshing)
        assertEquals(listOf(CAMERA), timeline.refreshedClips)
    }

    @Test
    fun `a failed refresh of the clips is shown until a retry answers, the video untouched`() =
        runTest(main.dispatcher) {
            timeline.clips.value = emptyMap()
            timeline.clipsResult = Outcome.Failure(DomainError.Network("down"))
            val viewModel = started()
            advanceUntilIdle()
            assertTrue(viewModel.uiState.value.clips.failedEmpty)
            assertEquals(LivePhase.Starting, viewModel.uiState.value.session.phase)

            timeline.clipsResult = Outcome.Success(Unit)
            viewModel.onRefreshClips()
            advanceUntilIdle()

            assertTrue(viewModel.uiState.value.clips.empty)
            assertEquals(listOf(CAMERA, CAMERA), timeline.refreshedClips)
        }

    @Test
    fun `a device without a camera asks for no clips`() = runTest(main.dispatcher) {
        val viewModel = viewModel(deviceId = Household.feeder.id)
        collect(viewModel)
        advanceUntilIdle()

        assertEquals(emptyList<Long>(), timeline.refreshedClips)
    }

    private class FakeSnapshots : SnapshotRepository {
        val saved = mutableListOf<String>()
        var accept = true
        var gate: CompletableDeferred<Unit>? = null

        override suspend fun save(jpeg: ByteArray, fileName: String): Boolean {
            gate?.await()
            if (accept) saved += fileName
            return accept
        }
    }

    /** The ViewModel only hands the factory to the screen. */
    private object NoPlayers : LivePlayerFactory {
        override fun create(context: Context, deviceId: Long, transport: LiveTransport): Player =
            error("no player here")

        override fun failureOf(error: PlaybackException): LiveFailure = LiveFailure.Playback()
    }

    private companion object {
        val CAMERA = Household.cameraLitterBox.id
        const val GONE = 9_999L
    }
}
