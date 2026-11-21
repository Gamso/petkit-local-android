package org.petkitlocal.feature.live

import androidx.lifecycle.SavedStateHandle
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
import org.petkitlocal.core.domain.repository.SnapshotRepository
import org.petkitlocal.core.domain.talk.OpenTalkUseCase
import org.petkitlocal.core.domain.talk.TalkFailure
import org.petkitlocal.core.domain.video.ObserveRecentClipsUseCase
import org.petkitlocal.core.domain.video.RefreshRecentClipsUseCase
import org.petkitlocal.core.domain.video.SaveSnapshotUseCase
import org.petkitlocal.core.testing.FakeDeviceRepository
import org.petkitlocal.core.testing.FakeMicrophoneAccess
import org.petkitlocal.core.testing.FakeTalkRepository
import org.petkitlocal.core.testing.FakeTimelineRepository
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.testing.MainDispatcherExtension
import org.petkitlocal.core.ui.talk.TalkNotice
import org.petkitlocal.core.ui.talk.TalkPhase

/** Talk on the live screen: held to speak, idle -> connecting -> talking -> stopping -> idle. */
@OptIn(ExperimentalCoroutinesApi::class)
class LiveTalkViewModelTest {
    @JvmField
    @RegisterExtension
    val main = MainDispatcherExtension()

    private val devices = FakeDeviceRepository(Household.devices)
    private val talks = FakeTalkRepository()
    private val microphone = FakeMicrophoneAccess(granted = true)

    private fun viewModel(deviceId: Long = CAMERA) = LiveViewModel(
        SavedStateHandle(mapOf(LiveViewModel.DEVICE_ID_KEY to deviceId)),
        ObserveDeviceUseCase(devices),
        SaveSnapshotUseCase(NoSnapshots, Household.clock),
        ObserveRecentClipsUseCase(FakeTimelineRepository()),
        RefreshRecentClipsUseCase(FakeTimelineRepository()),
        OpenTalkUseCase(talks),
        microphone,
        Household.clock,
        LiveViewModelTest.NoPlayers,
    )

    private fun TestScope.started(deviceId: Long = CAMERA): LiveViewModel = viewModel(deviceId).also {
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { it.uiState.collect {} }
        it.onVisible()
        runCurrent()
    }

    private val LiveViewModel.talk get() = uiState.value.talk

    private fun withTalk(availability: TalkAvailability) {
        devices.devices.value = Household.devices.map {
            if (it.id == CAMERA) it.copy(talk = availability) else it
        }
    }

    @Test
    fun `holding Talk connects, talks once the camera accepted, and letting go stops`() = runTest(main.dispatcher) {
        val viewModel = started()
        assertEquals(TalkAvailability.Available, viewModel.talk.availability)
        assertEquals(TalkPhase.Idle, viewModel.talk.phase)

        viewModel.onTalkPressed()
        assertEquals(TalkPhase.Connecting, viewModel.talk.phase)
        assertEquals(listOf(CAMERA), talks.sessions.map { it.deviceId })
        assertTrue(talks.last.collecting)

        talks.last.ready()
        runCurrent()
        assertEquals(TalkPhase.Talking, viewModel.talk.phase)

        viewModel.onTalkReleased()
        assertEquals(TalkPhase.Stopping, viewModel.talk.phase)
        assertTrue(talks.last.stopRequested)

        talks.last.end()
        runCurrent()
        assertEquals(TalkPhase.Idle, viewModel.talk.phase)
        assertNull(viewModel.talk.notice)
        assertFalse(talks.last.collecting)
    }

    @Test
    fun `the camera's sound is off from the press until the talk is over, then as the user left it`() =
        runTest(main.dispatcher) {
            val viewModel = started()
            viewModel.onToggleMute()
            assertFalse(viewModel.uiState.value.soundMuted)

            viewModel.onTalkPressed()
            assertTrue(viewModel.uiState.value.soundMuted)
            talks.last.ready()
            runCurrent()
            assertTrue(viewModel.uiState.value.soundMuted)

            // The sound button cannot bring it back meanwhile, and says why.
            viewModel.onToggleMute()
            assertTrue(viewModel.uiState.value.soundMuted)
            assertEquals(TalkNotice.SoundOffWhileTalking, viewModel.talk.notice)

            viewModel.onTalkReleased()
            assertTrue(viewModel.uiState.value.soundMuted)
            talks.last.end()
            runCurrent()
            assertFalse(viewModel.uiState.value.soundMuted)
            assertFalse(viewModel.uiState.value.muted)
        }

    @Test
    fun `letting go before the camera accepted cancels, and says to hold`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onTalkPressed()
        viewModel.onTalkReleased()
        runCurrent()

        assertEquals(TalkPhase.Idle, viewModel.talk.phase)
        assertEquals(TalkNotice.HoldToTalk, viewModel.talk.notice)
        assertTrue(talks.last.cancelled)
        assertFalse(talks.last.stopRequested)
    }

    @Test
    fun `TalkBack's double tap toggles, start then stop`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onTalkToggled()
        assertEquals(TalkPhase.Connecting, viewModel.talk.phase)
        talks.last.ready()
        runCurrent()
        assertEquals(TalkPhase.Talking, viewModel.talk.phase)

        viewModel.onTalkToggled()
        assertEquals(TalkPhase.Stopping, viewModel.talk.phase)
        assertTrue(talks.last.stopRequested)

        // A double tap while it connects cancels, with nothing to say.
        talks.last.end()
        runCurrent()
        viewModel.onTalkToggled()
        viewModel.onTalkToggled()
        runCurrent()
        assertEquals(TalkPhase.Idle, viewModel.talk.phase)
        assertNull(viewModel.talk.notice)
        assertTrue(talks.last.cancelled)
    }

    @Test
    fun `pressing a camera that cannot talk opens nothing and says why`() = runTest(main.dispatcher) {
        val viewModel = started()
        for (reason in listOf(
            TalkAvailability.NeedsPatcher,
            TalkAvailability.DeviceUnreachable,
            TalkAvailability.Unsupported,
            TalkAvailability.ServerTooOld,
            TalkAvailability.Unknown,
        )) {
            withTalk(reason)
            runCurrent()
            assertEquals(reason, viewModel.talk.availability)
            viewModel.onTalkPressed()
            viewModel.onTalkReleased()
            assertEquals(TalkPhase.Idle, viewModel.talk.phase)
            assertEquals(TalkNotice.Unavailable(reason), viewModel.talk.notice)
        }
        assertTrue(talks.sessions.isEmpty())
    }

    @Test
    fun `the microphone is asked for at the first press with its reason, and once allowed the next press talks`() =
        runTest(main.dispatcher) {
            microphone.granted = false
            val viewModel = started()
            val effect = backgroundScope.launch {
                assertEquals(LiveEffect.RequestMicrophone, viewModel.effects.first())
            }

            viewModel.onTalkPressed()
            viewModel.onTalkReleased()
            runCurrent()
            assertTrue(effect.isCompleted)
            assertEquals(TalkNotice.MicrophoneReason, viewModel.talk.notice)
            assertTrue(talks.sessions.isEmpty())

            microphone.granted = true
            viewModel.onMicrophoneResult(granted = true)
            assertEquals(TalkNotice.MicrophoneAllowed, viewModel.talk.notice)
            viewModel.onTalkPressed()
            assertEquals(TalkPhase.Connecting, viewModel.talk.phase)
            assertNull(viewModel.talk.notice)
        }

    @Test
    fun `a refused microphone says how to allow it`() = runTest(main.dispatcher) {
        microphone.granted = false
        val viewModel = started()
        viewModel.onTalkPressed()
        viewModel.onMicrophoneResult(granted = false)

        assertEquals(TalkNotice.MicrophoneDenied, viewModel.talk.notice)
        assertTrue(talks.sessions.isEmpty())
    }

    @Test
    fun `a talk refused or ended by the server says why, whether held or not`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onTalkPressed()
        talks.last.end(TalkFailure.Busy)
        runCurrent()
        assertEquals(TalkPhase.Idle, viewModel.talk.phase)
        assertEquals(TalkNotice.Failed(TalkFailure.Busy), viewModel.talk.notice)
        // The finger comes up afterwards: nothing more happens.
        viewModel.onTalkReleased()
        assertEquals(TalkNotice.Failed(TalkFailure.Busy), viewModel.talk.notice)

        viewModel.onTalkPressed()
        talks.last.ready()
        runCurrent()
        talks.last.end(TalkFailure.DeviceUnreachable)
        runCurrent()
        assertEquals(TalkPhase.Idle, viewModel.talk.phase)
        assertEquals(TalkNotice.Failed(TalkFailure.DeviceUnreachable), viewModel.talk.notice)
        assertFalse(viewModel.uiState.value.soundMuted && !viewModel.uiState.value.muted)
    }

    @Test
    fun `going to the background stops a talk, and cancels one still connecting`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onTalkPressed()
        talks.last.ready()
        runCurrent()
        viewModel.onHidden()
        assertEquals(TalkPhase.Stopping, viewModel.talk.phase)
        assertTrue(talks.last.stopRequested)
        talks.last.end()
        runCurrent()

        viewModel.onVisible()
        viewModel.onTalkPressed()
        viewModel.onHidden()
        runCurrent()
        assertEquals(TalkPhase.Idle, viewModel.talk.phase)
        assertTrue(talks.last.cancelled)
    }

    @Test
    fun `presses are ignored while the talk is finishing`() = runTest(main.dispatcher) {
        val viewModel = started()
        viewModel.onTalkPressed()
        talks.last.ready()
        runCurrent()
        viewModel.onTalkReleased()
        viewModel.onTalkPressed()
        viewModel.onTalkToggled()

        assertEquals(TalkPhase.Stopping, viewModel.talk.phase)
        assertEquals(1, talks.sessions.size)
    }

    private object NoSnapshots : SnapshotRepository {
        override suspend fun save(jpeg: ByteArray, fileName: String) = false
    }

    private companion object {
        val CAMERA = Household.cameraLitterBox.id
    }
}
