package org.petkitlocal.feature.device.common

import androidx.annotation.StringRes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.device.RunDeviceActionUseCase
import org.petkitlocal.core.domain.device.SetEntityValueUseCase
import org.petkitlocal.core.domain.model.DeviceAction
import org.petkitlocal.core.domain.model.Entity
import org.petkitlocal.core.domain.model.EntityValue
import org.petkitlocal.core.domain.repository.CommandDelivery

/** What a device screen shows about the commands it sent: shared by every device screen. */
data class CommandState(
    /** A destructive action waiting for the user's confirmation. */
    val confirming: DeviceAction? = null,
    /** Action and entity keys whose request is in flight: pressing them again does nothing. */
    val sending: Set<String> = emptySet(),
    /** The last command's outcome, until it is dismissed or times out. */
    val feedback: CommandFeedback? = null,
)

/** What a command was about, by stable key and the server's English name (translated on screen). */
sealed interface CommandSubject {
    val key: String
    val name: String

    data class Action(override val key: String, override val name: String) : CommandSubject

    data class Setting(override val key: String, override val name: String) : CommandSubject

    /**
     * A change a page words itself (a feeder's plan, a one-off feed): [label] is its string
     * resource, [name] its English for logs and tests.
     */
    data class Labelled(override val key: String, override val name: String, @param:StringRes val label: Int) :
        CommandSubject
}

sealed interface CommandResult {
    data object Sending : CommandResult

    /** Accepted, and how petkit-local passed it on. Not proof the device did it (ADR 0002). */
    data class Sent(val delivery: CommandDelivery) : CommandResult

    data class Failed(val error: DomainError) : CommandResult
}

/** One banner; [id] tells two identical outcomes apart, so the second one is shown again. */
data class CommandFeedback(val subject: CommandSubject, val result: CommandResult, val id: Long)

/**
 * The command half of a device screen's ViewModel: actions with their confirmation, setting
 * writes, and the feedback banner, in [scope] (the ViewModel's). No optimistic UI (ADR 0002):
 * nothing here changes what the screen says the device is doing; the cache does that when the
 * server's answer or the device's next report arrives.
 */
class DeviceCommandController(
    private val scope: CoroutineScope,
    private val deviceId: Long,
    private val runAction: RunDeviceActionUseCase,
    private val setEntityValue: SetEntityValueUseCase,
) {
    private val mutableState = MutableStateFlow(CommandState())
    val state: StateFlow<CommandState> = mutableState.asStateFlow()

    private var nextId = 0L
    private var dismissal: Job? = null

    /** A tap on an action: sent at once, or first confirmed when it cannot be undone. */
    fun onAction(action: DeviceAction) {
        if (action.key in mutableState.value.sending) return
        if (action.destructive) {
            mutableState.update { it.copy(confirming = action) }
        } else {
            send(action, confirmed = false)
        }
    }

    fun onConfirm() {
        val action = mutableState.value.confirming ?: return
        mutableState.update { it.copy(confirming = null) }
        send(action, confirmed = true)
    }

    fun onDismissConfirmation() {
        mutableState.update { it.copy(confirming = null) }
    }

    fun onDismissFeedback() {
        dismissal?.cancel()
        mutableState.update { it.copy(feedback = null) }
    }

    /** A switch flipped, or any write whose failure goes to the banner. */
    fun onWrite(entity: Entity, value: EntityValue) {
        if (entity.key in mutableState.value.sending) return
        scope.launch { write(entity, value, reportFailure = true) }
    }

    /**
     * Writes [value]; success always goes to the banner, a failure only if [reportFailure] (an
     * editor that stays open says it in place instead).
     */
    suspend fun write(entity: Entity, value: EntityValue, reportFailure: Boolean): Outcome<CommandDelivery> {
        val subject = CommandSubject.Setting(entity.key, entity.name)
        return track(subject, reportFailure) { setEntityValue(deviceId, entity, value) }
    }

    /**
     * Any other command a page sends (a feeding plan, a one-off feed), with the same banner:
     * sending, then how it went out; a failure only if [reportFailure], as for [write].
     */
    suspend fun report(
        subject: CommandSubject,
        reportFailure: Boolean,
        request: suspend () -> Outcome<CommandDelivery>,
    ): Outcome<CommandDelivery> = track(subject, reportFailure, request)

    private fun send(action: DeviceAction, confirmed: Boolean) {
        scope.launch {
            track(CommandSubject.Action(action.key, action.name), reportFailure = true) {
                runAction(deviceId, action, confirmed)
            }
        }
    }

    private suspend fun track(
        subject: CommandSubject,
        reportFailure: Boolean,
        request: suspend () -> Outcome<CommandDelivery>,
    ): Outcome<CommandDelivery> {
        mutableState.update { it.copy(sending = it.sending + subject.key) }
        show(subject, CommandResult.Sending)
        val result = request()
        mutableState.update { it.copy(sending = it.sending - subject.key) }
        when (result) {
            is Outcome.Success -> show(subject, CommandResult.Sent(result.value))

            is Outcome.Failure -> if (reportFailure) {
                show(subject, CommandResult.Failed(result.error))
            } else {
                clear(subject)
            }
        }
        return result
    }

    private fun show(subject: CommandSubject, result: CommandResult) {
        dismissal?.cancel()
        val feedback = CommandFeedback(subject, result, nextId++)
        mutableState.update { it.copy(feedback = feedback) }
        val shownFor = when (result) {
            CommandResult.Sending -> return
            is CommandResult.Sent -> SENT_SHOWN_MS
            is CommandResult.Failed -> FAILED_SHOWN_MS
        }
        dismissal = scope.launch {
            delay(shownFor)
            mutableState.update { if (it.feedback?.id == feedback.id) it.copy(feedback = null) else it }
        }
    }

    /** Removes [subject]'s "sending" banner, if it is still the one shown. */
    private fun clear(subject: CommandSubject) {
        mutableState.update { if (it.feedback?.subject == subject) it.copy(feedback = null) else it }
    }

    companion object {
        const val SENT_SHOWN_MS = 4_000L
        const val FAILED_SHOWN_MS = 8_000L
    }
}
