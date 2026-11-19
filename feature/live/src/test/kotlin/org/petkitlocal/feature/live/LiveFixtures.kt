package org.petkitlocal.feature.live

import kotlin.time.Duration.Companion.milliseconds
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.TalkAvailability
import org.petkitlocal.core.domain.talk.TalkFailure
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.ui.RecentClipsState

/** The live screen's states, for its screen and screenshot tests. */
internal object LiveFixtures {
    private val camera = Household.cameraLitterBox

    /** The camera's recent clips, once the server answered. */
    val clips = RecentClipsState(
        clips = Household.cameraClips,
        cacheLoaded = true,
        refreshed = true,
        zone = Household.zone,
        today = Household.today,
    )

    private fun state(session: LiveSession) = LiveUiState(
        deviceId = camera.id,
        deviceName = camera.modelName,
        availability = LiveAvailability.Camera,
        session = session,
        clips = clips,
        talk = TalkUiState(TalkAvailability.Available),
    )

    val connecting = state(LiveSession(phase = LivePhase.Starting, attempt = 1))

    val playing = state(LiveSession(phase = LivePhase.Playing, attempt = 1)).copy(delay = 400.milliseconds)

    val playingHls = state(LiveSession(transport = LiveTransport.Hls, phase = LivePhase.Playing, attempt = 2))
        .copy(delay = 3_200.milliseconds, muted = false, snapshot = SnapshotResult.Saved)

    val waiting = state(
        LiveSession(
            transport = LiveTransport.Hls,
            phase = LivePhase.Waiting(LiveFailure.Network(), LiveSession.RETRY_DELAY),
            attempt = 3,
        ),
    )

    val stopped = state(LiveSession(phase = LivePhase.Stopped(LiveFailure.StreamEnded), attempt = 1))

    val tooManyViewers = state(
        LiveSession(phase = LivePhase.Stopped(LiveFailure.Refused(DomainError.RateLimited(null))), attempt = 1),
    )

    val fullscreen = playing.copy(fullscreen = true)

    /** Talk held, the camera playing the microphone. */
    val talking = playing.copy(talk = TalkUiState(TalkAvailability.Available, TalkPhase.Talking))

    /** Talk held, the socket still opening. */
    val talkConnecting = playing.copy(talk = TalkUiState(TalkAvailability.Available, TalkPhase.Connecting))

    /** Let go: the camera plays what it received. */
    val talkStopping = playing.copy(talk = TalkUiState(TalkAvailability.Available, TalkPhase.Stopping))

    /** A camera without the Two-Way Talk patcher: the short reason under Talk. */
    val talkNeedsPatcher = playing.copy(talk = TalkUiState(TalkAvailability.NeedsPatcher))

    /** ...and Talk pressed anyway: what to do about it. */
    val talkNeedsPatcherPressed = talkNeedsPatcher.copy(
        talk = talkNeedsPatcher.talk.copy(notice = TalkNotice.Unavailable(TalkAvailability.NeedsPatcher)),
    )

    /** The microphone refused, and the way to Android's settings. */
    val microphoneDenied = playing.copy(
        talk = TalkUiState(TalkAvailability.Available, notice = TalkNotice.MicrophoneDenied),
    )

    /** Someone else talking through the camera. */
    val talkBusy = playing.copy(
        talk = TalkUiState(TalkAvailability.Available, notice = TalkNotice.Failed(TalkFailure.Busy)),
    )

    val notACamera = LiveUiState(
        deviceId = Household.feeder.id,
        deviceName = Household.feeder.modelName,
        availability = LiveAvailability.NotACamera,
    )
}
