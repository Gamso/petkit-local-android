package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.MockClock
import io.github.gamso.petkitlocal.mock.api.SessionDto
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import kotlin.math.min
import kotlin.math.pow

/** One signed-in phone. `id` is public (the panel revokes by it); the token is not kept. */
class AppSession(
    val id: String,
    val tokenHash: String,
    val name: String,
    val created: Double,
    var lastUsed: Double,
) {
    val expires: Double get() = lastUsed + SESSION_IDLE_LIFETIME

    fun dto() = SessionDto(id, name, created, lastUsed, expires)

    companion object {
        /** 90 days without use. */
        const val SESSION_IDLE_LIFETIME = 90 * 86400

        /** How stale `last_used` may get before a request rewrites it. */
        const val LAST_USED_WRITE_INTERVAL = 5 * 60
        const val MAX_SESSIONS = 50
        const val MAX_DEVICE_NAME = 64
        const val MIN_PASSWORD_LENGTH = 8
        const val MAX_PASSWORD_LENGTH = 1024
    }
}

class PasswordRejected(
    message: String,
) : Exception(message)

/**
 * The password, the sessions and the login throttle: petkit-local's `app_api/auth.py` and
 * `throttle.py`, in memory. Not thread-safe on its own; [World] holds the lock.
 */
class Auth(
    private val clock: MockClock,
    private val lockoutBase: Double,
    private val lockoutMax: Double,
    /** Called with each session that stops being live, so its sockets close with 4401. */
    private val onEnded: (AppSession) -> Unit,
) {
    var password: String? = null
        private set
    private val sessions = LinkedHashMap<String, AppSession>()
    private val failures = HashMap<String, Failure>()
    private val random = SecureRandom()

    private class Failure(
        var count: Int,
        var lockedUntil: Double,
        var last: Double,
    )

    val passwordSet: Boolean get() = !password.isNullOrEmpty()

    /** Sets (or with null/empty clears) the password as the panel does: every session is dropped. */
    fun setPasswordFromPanel(value: String?): Int {
        password = value?.takeIf { it.isNotEmpty() }
        return dropSessions(keep = null)
    }

    /** The in-app change: the rules apply and the caller's session survives. */
    fun changePassword(
        value: Any?,
        keep: String,
    ): Int {
        if (value !is String) throw PasswordRejected("password must be a string")
        if (value.length < AppSession.MIN_PASSWORD_LENGTH) {
            throw PasswordRejected("password must be at least ${AppSession.MIN_PASSWORD_LENGTH} characters")
        }
        if (value.length > AppSession.MAX_PASSWORD_LENGTH) {
            throw PasswordRejected("password must be at most ${AppSession.MAX_PASSWORD_LENGTH} characters")
        }
        password = value
        return dropSessions(keep)
    }

    fun verifyPassword(candidate: Any?): Boolean =
        candidate is String && candidate.isNotEmpty() && candidate.length <= AppSession.MAX_PASSWORD_LENGTH &&
            passwordSet && MessageDigest.isEqual(candidate.toByteArray(), password!!.toByteArray())

    fun createSession(deviceName: Any?): Pair<String, AppSession> {
        val bytes = ByteArray(32).also(random::nextBytes)
        val token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
        val id = ByteArray(8).also(random::nextBytes).joinToString("") { "%02x".format(it) }
        val now = clock.now()
        pruneExpired(now)
        while (sessions.size >= AppSession.MAX_SESSIONS) {
            val oldest = sessions.values.minBy { it.lastUsed }
            sessions.remove(oldest.tokenHash)
            onEnded(oldest)
        }
        val session = AppSession(id, hash(token), cleanName(deviceName), now, now)
        sessions[session.tokenHash] = session
        return token to session
    }

    /** The live session [token] names, sliding its expiry; null otherwise. */
    fun authenticate(token: String?): AppSession? {
        if (token.isNullOrEmpty() || !passwordSet) return null
        val session = sessions[hash(token)] ?: return null
        val now = clock.now()
        if (session.expires <= now) {
            sessions.remove(session.tokenHash)
            onEnded(session)
            return null
        }
        if (now - session.lastUsed >= AppSession.LAST_USED_WRITE_INTERVAL) session.lastUsed = now
        return session
    }

    /** Whether [session] still stands, without sliding its expiry (an open socket asks this). */
    fun isLive(session: AppSession): Boolean = passwordSet && sessions[session.tokenHash] === session && session.expires > clock.now()

    fun sessions(): List<AppSession> {
        val now = clock.now()
        return sessions.values.filter { it.expires > now }.sortedByDescending { it.lastUsed }
    }

    fun revoke(id: String): Boolean {
        val match = sessions.values.firstOrNull { it.id == id } ?: return false
        sessions.remove(match.tokenHash)
        onEnded(match)
        return true
    }

    fun revokeAll(): Int = dropSessions(keep = null)

    private fun dropSessions(keep: String?): Int {
        val dropped = sessions.values.filter { it.id != keep }
        dropped.forEach {
            sessions.remove(it.tokenHash)
            onEnded(it)
        }
        return dropped.size
    }

    /** Ends every session whose 90 days ran out (the clock can be advanced). */
    fun pruneExpired(now: Double = clock.now()) {
        sessions.values.filter { it.expires <= now }.forEach {
            sessions.remove(it.tokenHash)
            onEnded(it)
        }
    }

    // --- throttle -------------------------------------------------------------------------------

    /** Seconds [address] must still wait before a password is even checked; 0 if none. */
    fun retryAfter(address: String): Double {
        val f = failures[address] ?: return 0.0
        return maxOf(0.0, f.lockedUntil - clock.now())
    }

    /** Records a wrong password; returns the lockout it earned (1 s, 2 s, 4 s ... capped). */
    fun failure(address: String): Double {
        val now = clock.now()
        val f = failures[address]?.takeIf { now - it.last <= FORGET_AFTER } ?: Failure(0, 0.0, 0.0)
        f.count += 1
        f.last = now
        val delay = min(lockoutMax, lockoutBase * 2.0.pow(min(f.count - 1, 32)))
        f.lockedUntil = now + delay
        failures[address] = f
        return delay
    }

    fun success(address: String) {
        failures.remove(address)
    }

    fun clearThrottle() = failures.clear()

    companion object {
        const val FORGET_AFTER = 24 * 3600.0

        fun hash(token: String): String =
            MessageDigest.getInstance("SHA-256").digest(token.toByteArray()).joinToString("") { "%02x".format(it) }

        /** Control characters become spaces, whitespace collapses, 64 characters at most. */
        fun cleanName(value: Any?): String {
            val text = (value as? String).orEmpty()
            val cleaned =
                text
                    .map { if (Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt()) ' ' else it }
                    .joinToString("")
                    .split(Regex("\\s+"))
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                    .take(AppSession.MAX_DEVICE_NAME)
            return cleaned.ifEmpty { "Unnamed device" }
        }
    }
}
