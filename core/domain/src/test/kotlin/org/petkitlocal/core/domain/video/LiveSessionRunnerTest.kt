package org.petkitlocal.core.domain.video

import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LiveSessionRunnerTest {
    private val network = LiveFailure.Network("reset")

    private fun TestScope.runner() = LiveSessionRunner(backgroundScope)

    @Test
    fun `a wait starts again by itself once it is over`() = runTest {
        val runner = runner()
        runner.start()
        runner.fail(1, network)
        assertEquals(LivePhase.Waiting(network, LiveSession.RETRY_DELAY), runner.session.value.phase)

        advanceTimeBy(LiveSession.RETRY_DELAY + 1.milliseconds)
        assertEquals(LivePhase.Starting, runner.session.value.phase)
        assertEquals(2, runner.session.value.attempt)
    }

    @Test
    fun `stopping during a wait cancels it, so nothing starts behind the screen's back`() = runTest {
        val runner = runner()
        runner.start()
        runner.fail(1, network)
        runner.stop()

        advanceUntilIdle()
        assertEquals(LivePhase.Idle, runner.session.value.phase)
        assertEquals(1, runner.session.value.attempt)
    }

    @Test
    fun `a start that takes its time is marked slow, and playing clears it`() = runTest {
        val runner = runner()
        runner.start()
        advanceTimeBy(LiveSession.SLOW_START - 1.milliseconds)
        assertFalse(runner.session.value.startingSlowly)

        advanceTimeBy(2.milliseconds)
        assertTrue(runner.session.value.startingSlowly)
        assertEquals(LivePhase.Starting, runner.session.value.phase)

        runner.playing(1)
        assertEquals(LivePhase.Playing, runner.session.value.phase)
        assertFalse(runner.session.value.startingSlowly)
    }

    @Test
    fun `a start that plays in time is never slow`() = runTest {
        val runner = runner()
        runner.start()
        runner.playing(1)
        advanceUntilIdle()
        assertFalse(runner.session.value.startingSlowly)
    }

    @Test
    fun `each new attempt has its own slow-start clock`() = runTest {
        val runner = runner()
        runner.start()
        advanceTimeBy(LiveSession.SLOW_START - 1.milliseconds)
        runner.retry()
        runCurrent()
        advanceTimeBy(2.milliseconds)
        assertFalse(runner.session.value.startingSlowly, "the retry's attempt started 1 ms ago")

        advanceTimeBy(LiveSession.SLOW_START)
        assertTrue(runner.session.value.startingSlowly)
    }
}
