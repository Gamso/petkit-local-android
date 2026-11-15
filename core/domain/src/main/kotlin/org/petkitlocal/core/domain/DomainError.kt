package org.petkitlocal.core.domain

import kotlin.time.Duration

/**
 * Every way a call to the server can fail, as the rest of the app sees it. The data layer maps
 * HTTP statuses, `Error.error` codes and WebSocket close codes to these; nothing above it ever
 * sees an exception, a status code or a JSON body.
 */
sealed interface DomainError {
    /** The server could not be reached or the connection broke: offline, DNS, TLS, timeout. */
    data class Network(val detail: String? = null) : DomainError

    /**
     * A call that checks the password was refused: sign-in, or changing the password.
     * [retryAfter] is when the next attempt may be made, if the server said.
     */
    data class Unauthorized(val reason: UnauthorizedReason, val retryAfter: Duration? = null) : DomainError

    /** The session token is no longer accepted; the app clears it and returns to sign-in. */
    data class SessionEnded(val reason: SessionEndReason) : DomainError

    /** Too many attempts from this address (HTTP 429); the server says how long to wait. */
    data class RateLimited(val retryAfter: Duration?) : DomainError

    /**
     * The server understood the request and refused it (HTTP 400 or 422): an out-of-range value,
     * an unconfirmed destructive action. [code] is the server's machine-readable `error`.
     */
    data class Validation(val field: String?, val code: String, val message: String) : DomainError

    /** No such device, pet, event or feed (HTTP 404). */
    data class NotFound(val code: String, val message: String) : DomainError

    /** The server failed or answered with a status the app does not handle (5xx, 415, ...). */
    data class Server(val status: Int?, val code: String?, val message: String?) : DomainError

    /** The answer was not what petkit-local sends: another server, a proxy page, a bug. */
    data class UnexpectedResponse(val detail: String) : DomainError
}

enum class UnauthorizedReason {
    /** `invalid_password`. */
    InvalidPassword,

    /** `password_not_set`: the panel's "Phone app access" card has no password yet. */
    PasswordNotSet,

    /** Any other 401 on a call made without a session. */
    Other,
}

enum class SessionEndReason {
    /** `invalid_token`: logged out, revoked from the panel, password changed, or 90 days idle. */
    InvalidToken,

    /** `password_not_set` on a signed-in call: the password was removed in the panel. */
    PasswordRemoved,

    /** WebSocket close code `4401`: the session ended while a socket was open. */
    ClosedByServer,
}
