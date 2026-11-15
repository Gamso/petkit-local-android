package org.petkitlocal.core.network.error

import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.CancellationException
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.UnauthorizedReason
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.auth.AuthInterceptor
import org.petkitlocal.core.network.dto.ErrorDto
import retrofit2.HttpException

/** The server's `Error.error` codes this mapping tells apart (api/openapi.yaml). */
object ErrorCodes {
    const val INVALID_TOKEN = "invalid_token"
    const val PASSWORD_NOT_SET = "password_not_set"
    const val INVALID_PASSWORD = "invalid_password"
}

/**
 * Runs one API call and turns every failure into a [DomainError]: a non-2xx answer by its
 * status and `Error` body, an I/O failure as [DomainError.Network], an answer that does not
 * parse as [DomainError.UnexpectedResponse]. Cancellation is rethrown, never swallowed.
 */
@Suppress("TooGenericExceptionCaught") // Every failure must become a value; see the branches.
suspend fun <T> apiCall(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    Outcome.Failure(e.toDomainError())
} catch (e: IOException) {
    Outcome.Failure(DomainError.Network(e.message))
} catch (e: IllegalArgumentException) {
    // kotlinx.serialization's SerializationException: not the JSON petkit-local sends.
    Outcome.Failure(DomainError.UnexpectedResponse(e.message ?: e.javaClass.simpleName))
}

fun HttpException.toDomainError(): DomainError {
    val response = response()
    val raw = response?.raw()
    return HttpErrorMapper.map(
        status = code(),
        body = response?.errorBody()?.string(),
        retryAfter = raw?.header(RETRY_AFTER),
        hadSession = raw?.request?.header(AuthInterceptor.AUTHORIZATION) != null,
    )
}

/** For a refused WebSocket upgrade, which OkHttp reports with the plain HTTP response. */
fun okhttp3.Response.toDomainError(): DomainError = HttpErrorMapper.map(
    status = code,
    body = body.string(),
    retryAfter = header(RETRY_AFTER),
    hadSession = request.header(AuthInterceptor.AUTHORIZATION) != null,
)

private const val RETRY_AFTER = "Retry-After"

object HttpErrorMapper {
    private const val BAD_REQUEST = 400
    private const val UNAUTHORIZED = 401
    private const val NOT_FOUND = 404
    private const val UNPROCESSABLE = 422
    private const val TOO_MANY_REQUESTS = 429

    /**
     * @param body the response body, an `Error` object when the server wrote it.
     * @param retryAfter the `Retry-After` header: seconds, as petkit-local sends it.
     * @param hadSession whether the request carried a bearer token. A 401 then means the session
     *   is over; without one (sign-in) it means the password was refused.
     */
    fun map(status: Int, body: String?, retryAfter: String?, hadSession: Boolean): DomainError {
        val error = body?.let(::parseError)
        val code = error?.error
        val message = error?.message
        val wait = parseRetryAfter(retryAfter)
        return when (status) {
            UNAUTHORIZED -> unauthorized(code, wait, hadSession)

            TOO_MANY_REQUESTS -> DomainError.RateLimited(wait)

            BAD_REQUEST, UNPROCESSABLE ->
                DomainError.Validation(field = null, code = code ?: "http_$status", message = message.orEmpty())

            NOT_FOUND -> DomainError.NotFound(code = code ?: "not_found", message = message.orEmpty())

            else -> DomainError.Server(status = status, code = code, message = message)
        }
    }

    private fun unauthorized(code: String?, wait: Duration?, hadSession: Boolean): DomainError = when {
        code == ErrorCodes.INVALID_TOKEN -> DomainError.SessionEnded(SessionEndReason.InvalidToken)

        code == ErrorCodes.PASSWORD_NOT_SET && hadSession ->
            DomainError.SessionEnded(SessionEndReason.PasswordRemoved)

        code == ErrorCodes.PASSWORD_NOT_SET -> DomainError.Unauthorized(UnauthorizedReason.PasswordNotSet)

        code == ErrorCodes.INVALID_PASSWORD -> DomainError.Unauthorized(UnauthorizedReason.InvalidPassword, wait)

        hadSession -> DomainError.SessionEnded(SessionEndReason.InvalidToken)

        else -> DomainError.Unauthorized(UnauthorizedReason.Other, wait)
    }

    /** The `Error.error` code of [body], or null when it is not petkit-local's `Error`. */
    fun errorCode(body: String?): String? = body?.let(::parseError)?.error

    private fun parseError(body: String): ErrorDto? = try {
        PetkitJson.decodeFromString(ErrorDto.serializer(), body)
    } catch (_: IllegalArgumentException) { // a proxy's HTML page, an empty body
        null
    }

    private fun parseRetryAfter(value: String?): Duration? = value?.trim()?.toLongOrNull()?.takeIf { it >= 0 }?.seconds
}
