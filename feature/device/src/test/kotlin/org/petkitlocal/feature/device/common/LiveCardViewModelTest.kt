package org.petkitlocal.feature.device.common

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
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
import org.petkitlocal.core.domain.device.ObserveDeviceUseCase
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.model.TimelineItem
import org.petkitlocal.core.domain.repository.SnapshotRepository
import org.petkitlocal.core.domain.talk.OpenTalkUseCase
import org.petkitlocal.core.domain.video.LatestStill
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.domain.video.ObserveLatestStillUseCase
import org.petkitlocal.core.domain.video.SaveSnapshotUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeMicrophoneAccess
import org.petkitlocal.core.testing.FakeTalkRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.core.ui.live.LivePlayback
import org.petkitlocal.core.ui.live.LivePlayerFactory
import org.petkitlocal.core.ui.live.SnapshotResult
import org.petkitlocal.core.ui.talk.TalkNotice
import org.petkitlocal.core.ui.talk.TalkPhase
import org.petkitlocal.feature.device.DEVICE_ID_KEY

@OptIn(ExperimentalCoroutinesApi::class)
class LiveCardViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension(StandardTestDispatcher())

    private val camera = Household.cameraLitterBox.id
    private val timeline = FakeTimelineRepository()
    private val devices = FakeDeviceRepository(Household.devices)
    private val talks = FakeTalkRepository()
    private val microphone = FakeMicrophoneAccess(granted = true)
    private val snapshots = FakeSnapshots()

    private fun viewModel() = LiveCardViewModel(
        SavedStateHandle(mapOf(DEVICE_ID_KEY to camera)),
        ObserveLatestStillUseCase(timeline),
        ObserveDeviceUseCase(devices),
        OpenTalkUseCase(talks),
        microphone,
        SaveSnapshotUseCase(snapshots, Household.clock),
        Household.clock,
        NoPlayers,
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

    @Test
    fun `nothing plays until the card is shown, then it plays on the WebSocket, muted`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        advanceUntilIdle()
        assertNull(viewModel.playback.value, "the page opened, the card not shown yet")
        assertEquals(LivePhase.Idle, viewModel.state.value.session.phase)

        viewModel.onVisible()
        runCurrent()
        assertEquals(LivePlayback(camera, LiveTransport.WebSocket, attempt = 1), viewModel.playback.value)
        assertEquals(LivePhase.Starting, viewModel.state.value.session.phase)
        assertTrue(viewModel.state.value.muted)
    }

    @Test
    fun `hidden (page left, app in the background), the player goes and with it the viewer slot`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            viewModel.onVisible()
            viewModel.onPlaying(1)
            runCurrent()
            assertEquals(LivePhase.Playing, viewModel.state.value.session.phase)

            viewModel.onHidden()
            runCurrent()
            assertNull(viewModel.playback.value)
            assertEquals(LivePhase.Idle, viewModel.state.value.session.phase)

            // Shown again: a new attempt, a new player; the old one's late reports change nothing.
            viewModel.onVisible()
            viewModel.onPlayerFailed(1, LiveFailure.StreamEnded)
            runCurrent()
            assertEquals(LivePlayback(camera, LiveTransport.WebSocket, attempt = 2), viewModel.playback.value)
        }

    @Test
    fun `hidden while waiting to start again, it does not start behind the page's back`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onVisible()
        viewModel.onPlayerFailed(1, LiveFailure.Network())
        viewModel.onHidden()
        advanceUntilIdle()

        assertNull(viewModel.playback.value)
        assertEquals(LivePhase.Idle, viewModel.state.value.session.phase)
    }

    @Test
    fun `a slow first open is progress, then plays`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onVisible()
        advanceTimeBy(LiveSession.SLOW_START + 1.milliseconds)
        assertTrue(viewModel.state.value.session.startingSlowly)
        assertEquals(LivePhase.Starting, viewModel.state.value.session.phase)

        viewModel.onPlaying(1)
        runCurrent()
        assertEquals(LivePhase.Playing, viewModel.state.value.session.phase)
        assertFalse(viewModel.state.value.session.startingSlowly)
    }

    @Test
    fun `stopped for good, only the user's retry starts it again`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onVisible()
        viewModel.onPlayerFailed(1, LiveFailure.StreamEnded)
        runCurrent()
        assertEquals(LivePhase.Stopped(LiveFailure.StreamEnded), viewModel.state.value.session.phase)
        assertNull(viewModel.playback.value)

        viewModel.onRetry()
        runCurrent()
        assertEquals(LivePlayback(camera, LiveTransport.WebSocket, attempt = 2), viewModel.playback.value)
    }

    @Test
    fun `the sound is the user's to turn on and off`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        runCurrent()
        assertTrue(viewModel.state.value.muted)
        viewModel.onToggleMute()
        runCurrent()
        assertFalse(viewModel.state.value.muted)
        viewModel.onToggleMute()
        runCurrent()
        assertTrue(viewModel.state.value.muted)
    }

    @Test
    fun `full screen is the same session, no new attempt, and it plays on while the card leaves`() =
        runTest(main.dispatcher) {
            val viewModel = viewModel()
            collect(viewModel)
            viewModel.onVisible()
            viewModel.onPlaying(1)
            runCurrent()

            viewModel.onFullscreen(true)
            // The full screen shows it too; turning to landscape may take the card off the list.
            viewModel.onVisible()
            viewModel.onHidden()
            runCurrent()
            assertTrue(viewModel.state.value.fullscreen)
            assertEquals(LivePlayback(camera, LiveTransport.WebSocket, attempt = 1), viewModel.playback.value)
            assertEquals(LivePhase.Playing, viewModel.state.value.session.phase)

            viewModel.onFullscreen(false)
            runCurrent()
            assertFalse(viewModel.state.value.fullscreen)
            assertEquals(LivePlayback(camera, LiveTransport.WebSocket, attempt = 1), viewModel.playback.value)
        }

    @Test
    fun `in the background, neither the card nor its full screen keeps the viewer`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onVisible()
        viewModel.onFullscreen(true)
        viewModel.onVisible()
        runCurrent()

        // ON_STOP reaches both.
        viewModel.onHidden()
        viewModel.onHidden()
        runCurrent()
        assertNull(viewModel.playback.value)
        assertEquals(LivePhase.Idle, viewModel.state.value.session.phase)
    }

    @Test
    fun `Talk on the card talks through the camera, and its sound is off meanwhile`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onVisible()
        runCurrent()
        assertEquals(TalkAvailability.Available, viewModel.state.value.talk.availability)

        viewModel.onToggleMute()
        viewModel.onTalkPressed()
        runCurrent()
        assertEquals(TalkPhase.Connecting, viewModel.state.value.talk.phase)
        assertTrue(viewModel.state.value.soundMuted, "the camera's own sound is off while talking")
        assertFalse(viewModel.state.value.muted, "the user's choice is kept")

        viewModel.onToggleMute()
        runCurrent()
        assertEquals(TalkNotice.SoundOffWhileTalking, viewModel.state.value.talk.notice)

        // Leaving the page ends the talk with the video.
        viewModel.onHidden()
        runCurrent()
        assertEquals(TalkPhase.Idle, viewModel.state.value.talk.phase)
    }

    @Test
    fun `a camera that cannot talk keeps Talk pressable and says why`() = runTest(main.dispatcher) {
        devices.devices.value = Household.devices.map {
            if (it.id == camera) it.copy(talk = TalkAvailability.NeedsPatcher) else it
        }
        val viewModel = viewModel()
        collect(viewModel)
        runCurrent()

        viewModel.onTalkPressed()
        runCurrent()
        assertEquals(TalkNotice.Unavailable(TalkAvailability.NeedsPatcher), viewModel.state.value.talk.notice)
        assertEquals(TalkPhase.Idle, viewModel.state.value.talk.phase)
    }

    @Test
    fun `the microphone is asked for at the first press`() = runTest(main.dispatcher) {
        microphone.granted = false
        val viewModel = viewModel()
        collect(viewModel)
        runCurrent()
        val asked = mutableListOf<Unit>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.requestMicrophone.collect {
                asked +=
                    it
            }
        }

        viewModel.onTalkPressed()
        runCurrent()
        assertEquals(1, asked.size)
        assertEquals(TalkNotice.MicrophoneReason, viewModel.state.value.talk.notice)
        viewModel.onMicrophoneResult(false)
        runCurrent()
        assertEquals(TalkNotice.MicrophoneDenied, viewModel.state.value.talk.notice)
    }

    @Test
    fun `a snapshot of the full screen is saved under the camera's name`() = runTest(main.dispatcher) {
        val viewModel = viewModel()
        collect(viewModel)
        viewModel.onSnapshot(byteArrayOf(1, 2, 3))
        runCurrent()
        assertEquals(SnapshotResult.Saved, viewModel.state.value.snapshot)
        assertEquals(1, snapshots.saved.size)

        viewModel.onSnapshot(null)
        runCurrent()
        assertEquals(SnapshotResult.NoPicture, viewModel.state.value.snapshot)
    }

    private class FakeSnapshots : SnapshotRepository {
        val saved = mutableListOf<String>()

        override suspend fun save(jpeg: ByteArray, fileName: String): Boolean {
            saved += fileName
            return true
        }
    }

    /** The ViewModel only hands the factory to the page; no test here builds a player. */
    private object NoPlayers : LivePlayerFactory {
        override fun create(context: Context, deviceId: Long, transport: LiveTransport): Player =
            error("no player here")

        override fun failureOf(error: PlaybackException): LiveFailure = LiveFailure.Playback()
    }
}
