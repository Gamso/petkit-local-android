package org.petkitlocal.core.domain.video

import kotlin.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet
import kotlinx.coroutines.launch

/**
 * Holds one [LiveSession] for whoever shows it (the live screen, a camera page's live card):
 * applies each event to it and keeps its time in [scope], which [LiveSession] has no clock for.
 * A [LivePhase.Waiting] starts again by itself once its wait is over, and an attempt still
 * starting after [LiveSession.SLOW_START] is marked [LiveSession.startingSlowly]. Every change
 * cancels the wait it replaces, so a stopped session never starts again behind the screen's back.
 */
class LiveSessionRunner(private val scope: CoroutineScope) {
    private val state = MutableStateFlow(LiveSession())
    private var timer: Job? = null

    val session: StateFlow<LiveSession> = state.asStateFlow()

    /** The screen is visible: play. */
    fun start() = update { it.start() }

    /** The screen left or went to the background: no player, no socket, no viewer slot. */
    fun stop() = update { it.stop() }

    /** The slot is handed to another screen; the next [start] is a fresh one ([LiveSession.release]). */
    fun release() = update { it.release() }

    /** The user's retry. */
    fun retry() = update { it.retry() }

    /** The player of [attempt] shows its first frame. */
    fun playing(attempt: Int) = update { it.playing(attempt) }

    /** The player of [attempt] stopped with [failure]. */
    fun fail(attempt: Int, failure: LiveFailure) = update { it.fail(attempt, failure) }

    private fun update(transform: (LiveSession) -> LiveSession) {
        val before = state.value
        val after = state.updateAndGet(transform)
        if (after == before) return
        val phase = after.phase
        val newStart = phase == LivePhase.Starting &&
            (before.phase != LivePhase.Starting || before.attempt != after.attempt)
        when {
            phase is LivePhase.Waiting -> schedule(phase.retryIn) { it.retryDue() }

            newStart -> schedule(LiveSession.SLOW_START) { it.slowStart(after.attempt) }

            // Still the same start, only marked slow: its timer has just fired.
            phase == LivePhase.Starting -> Unit

            else -> cancelTimer()
        }
    }

    private fun schedule(after: Duration, transform: (LiveSession) -> LiveSession) {
        cancelTimer()
        timer = scope.launch {
            delay(after)
            timer = null
            update(transform)
        }
    }

    private fun cancelTimer() {
        timer?.cancel()
        timer = null
    }
}
