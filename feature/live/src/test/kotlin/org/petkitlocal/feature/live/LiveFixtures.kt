package org.petkitlocal.feature.live

import kotlin.time.Duration.Companion.milliseconds
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.video.LiveFailure
import org.petkitlocal.core.domain.video.LivePhase
import org.petkitlocal.core.domain.video.LiveSession
import org.petkitlocal.core.domain.video.LiveTransport
import org.petkitlocal.core.testing.Household

/** The live screen's states, for its screen and screenshot tests. */
internal object LiveFixtures {
    private val camera = Household.cameraLitterBox

    private fun state(session: LiveSession) = LiveUiState(
        deviceId = camera.id,
        deviceName = camera.model,
        availability = LiveAvailability.Camera,
        session = session,
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

    val notACamera = LiveUiState(
        deviceId = Household.feeder.id,
        deviceName = Household.feeder.model,
        availability = LiveAvailability.NotACamera,
    )
}
