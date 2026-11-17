package org.petkitlocal.core.testing

import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.settings.NotificationKinds
import org.petkitlocal.core.domain.settings.NotificationSettings
import org.petkitlocal.core.domain.settings.NotificationSettingsRepository
import org.petkitlocal.core.domain.settings.ServerNotificationKind

/**
 * A server holding one session's notification kinds, as petkit-local does: a write is answered
 * with what it now holds, in its own order. [failLoad] and [failWrite] script refusals; a call
 * suspends in [gate] when it is set.
 */
class FakeNotificationSettingsRepository(var settings: NotificationSettings = DEFAULT) :
    NotificationSettingsRepository {
    var failLoad: DomainError? = null
    var failWrite: DomainError? = null
    var gate: (suspend () -> Unit)? = null

    /** Every `kinds` sent, in order. */
    val writes = mutableListOf<List<String>?>()
    var loads = 0
        private set

    override suspend fun load(): Outcome<NotificationSettings> {
        loads++
        gate?.invoke()
        return failLoad?.let { Outcome.Failure(it) } ?: Outcome.Success(settings)
    }

    override suspend fun setKinds(kinds: List<String>?): Outcome<NotificationSettings> {
        writes += kinds
        gate?.invoke()
        failWrite?.let { return Outcome.Failure(it) }
        val order = settings.serverKinds.map { it.kind }
        settings = settings.copy(chosen = kinds?.let { chosen -> order.filter { it in chosen } })
        return Outcome.Success(settings)
    }

    companion object {
        /** A new session: every kind, every one switched on in the panel. */
        val DEFAULT = NotificationSettings(
            chosen = null,
            serverKinds = NotificationKinds.KNOWN.map { ServerNotificationKind(it, enabledOnServer = true) },
            connected = false,
            lastId = 0,
        )
    }
}
