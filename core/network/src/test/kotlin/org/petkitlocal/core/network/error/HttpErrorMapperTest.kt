package org.petkitlocal.core.network.error

import kotlin.time.Duration.Companion.seconds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.UnauthorizedReason
import org.petkitlocal.core.network.CloseCodes
import org.petkitlocal.core.network.closeCodeToDomainError

class HttpErrorMapperTest {
    private fun error(code: String, message: String = "text") = """{"error":"$code","message":"$message"}"""

    @Test
    fun `401 invalid_token ends the session`() {
        assertEquals(
            DomainError.SessionEnded(SessionEndReason.InvalidToken),
            HttpErrorMapper.map(401, error("invalid_token"), null, hadSession = true),
        )
    }

    @Test
    fun `401 password_not_set before sign-in tells the user to set one in the panel`() {
        assertEquals(
            DomainError.Unauthorized(UnauthorizedReason.PasswordNotSet),
            HttpErrorMapper.map(401, error("password_not_set"), null, hadSession = false),
        )
    }

    @Test
    fun `401 password_not_set on a signed-in call means the password was removed`() {
        assertEquals(
            DomainError.SessionEnded(SessionEndReason.PasswordRemoved),
            HttpErrorMapper.map(401, error("password_not_set"), null, hadSession = true),
        )
    }

    @Test
    fun `401 invalid_password carries the lockout`() {
        assertEquals(
            DomainError.Unauthorized(UnauthorizedReason.InvalidPassword, 4.seconds),
            HttpErrorMapper.map(401, error("invalid_password"), "4", hadSession = false),
        )
    }

    @Test
    fun `429 carries Retry-After, and an unreadable one is unknown rather than zero`() {
        assertEquals(
            DomainError.RateLimited(900.seconds),
            HttpErrorMapper.map(429, error("too_many_attempts"), "900", false),
        )
        assertEquals(DomainError.RateLimited(null), HttpErrorMapper.map(429, null, "soon", false))
    }

    @Test
    fun `422 is a validation error with the server's code and message`() {
        assertEquals(
            DomainError.Validation(null, "confirmation_required", "Confirm first"),
            HttpErrorMapper.map(422, error("confirmation_required", "Confirm first"), null, true),
        )
    }

    @Test
    fun `409 accessory_unreachable says an accessory's relay is not on MQTT, any other 409 is a server error`() {
        assertEquals(
            DomainError.AccessoryUnreachable(),
            HttpErrorMapper.map(409, error("accessory_unreachable"), null, true),
        )
        assertEquals(
            DomainError.Server(409, "conflict", "text"),
            HttpErrorMapper.map(409, error("conflict"), null, true),
        )
    }

    @Test
    fun `404 keeps the code that says what was not found`() {
        assertEquals(
            DomainError.NotFound("feed_not_found", "text"),
            HttpErrorMapper.map(404, error("feed_not_found"), null, true),
        )
    }

    @Test
    fun `5xx with a non-JSON body is a server error without a code`() {
        assertEquals(
            DomainError.Server(502, null, null),
            HttpErrorMapper.map(502, "<html>Bad gateway</html>", null, true),
        )
    }

    @Test
    fun `WebSocket close codes`() {
        assertNull(closeCodeToDomainError(CloseCodes.NORMAL))
        assertNull(closeCodeToDomainError(CloseCodes.REPLACED))
        assertEquals(
            DomainError.SessionEnded(SessionEndReason.ClosedByServer),
            closeCodeToDomainError(CloseCodes.SESSION_ENDED),
        )
        assertEquals(
            DomainError.Network("WebSocket closed with code 1013"),
            closeCodeToDomainError(CloseCodes.TOO_SLOW),
        )
    }
}
