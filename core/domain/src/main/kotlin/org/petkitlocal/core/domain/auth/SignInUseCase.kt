package org.petkitlocal.core.domain.auth

import javax.inject.Inject
import kotlin.time.Duration
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.UnauthorizedReason

/** Every way signing in can fail, each shown in place on the sign-in screen (SPEC.md §5.1). */
sealed interface SignInFailure {
    data class InvalidAddress(val problem: AddressProblem) : SignInFailure

    /** Nothing was typed in the password field. */
    data object PasswordMissing : SignInFailure

    /** Something answered, but not petkit-local's `/app/v1`: another site, a proxy page, a 404. */
    data object NotPetkitLocal : SignInFailure

    /** A petkit-local whose API version this app does not speak. */
    data class IncompatibleServer(val apiVersion: Int) : SignInFailure

    /** No password is set in the panel's "Phone app access" card yet. */
    data object PasswordNotSet : SignInFailure

    /** [retryAfter]: the server locks this address out for that long before the next attempt. */
    data class WrongPassword(val retryAfter: Duration?) : SignInFailure

    /** HTTP 429: too many wrong passwords; [retryAfter] is the server's `Retry-After`. */
    data class LockedOut(val retryAfter: Duration?) : SignInFailure

    /**
     * The server is on the LAN and the user refused Android's local network access (Android 17+),
     * without which every packet to it is dropped. Never "unreachable": the fix is a permission.
     */
    data object LocalNetworkDenied : SignInFailure

    /** No answer: offline, DNS, TLS, timeout. */
    data class Unreachable(val detail: String?) : SignInFailure

    /** The server failed (5xx) or refused the request for a reason of its own. */
    data class ServerError(val status: Int?, val message: String?) : SignInFailure
}

sealed interface SignInResult {
    data class Success(val session: Session) : SignInResult

    data class Failure(val failure: SignInFailure) : SignInResult
}

/**
 * Signs in as SPEC.md §5.1 says: normalise the address, `GET /info` to check that it is a
 * petkit-local this app can speak to, then `POST /auth/login` with the phone's name.
 */
class SignInUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(address: String, password: String, phoneName: String): SignInResult {
        val incomplete = checkForm(address, password)
        if (incomplete != null) return SignInResult.Failure(incomplete)
        val server = (ServerAddresses.normalise(address) as AddressCheck.Valid).address

        val refused = probe(server)
        if (refused != null) return SignInResult.Failure(refused)

        val name = phoneName.trim().take(MAX_PHONE_NAME).ifEmpty { null }
        return when (val login = repository.signIn(server, password, name)) {
            is Outcome.Success -> SignInResult.Success(login.value)
            is Outcome.Failure -> SignInResult.Failure(loginFailure(login.error))
        }
    }

    /** Null when [server] is a petkit-local this app speaks to. */
    private suspend fun probe(server: ServerAddress): SignInFailure? = when (val info = repository.probe(server)) {
        is Outcome.Failure -> probeFailure(info.error)

        is Outcome.Success ->
            info.value.apiVersion
                .takeIf { it != ServerInfo.SUPPORTED_API_VERSION }
                ?.let(SignInFailure::IncompatibleServer)
    }

    private fun probeFailure(error: DomainError): SignInFailure = when (error) {
        is DomainError.Unauthorized -> when (error.reason) {
            UnauthorizedReason.PasswordNotSet -> SignInFailure.PasswordNotSet
            else -> SignInFailure.NotPetkitLocal
        }

        is DomainError.NotFound, is DomainError.UnexpectedResponse, is DomainError.Validation ->
            SignInFailure.NotPetkitLocal

        else -> common(error)
    }

    private fun loginFailure(error: DomainError): SignInFailure = when (error) {
        is DomainError.Unauthorized -> when (error.reason) {
            UnauthorizedReason.PasswordNotSet -> SignInFailure.PasswordNotSet
            UnauthorizedReason.InvalidPassword -> SignInFailure.WrongPassword(error.retryAfter)
            UnauthorizedReason.Other -> SignInFailure.ServerError(status = UNAUTHORIZED, message = null)
        }

        is DomainError.Validation -> SignInFailure.ServerError(status = null, message = error.message)

        is DomainError.NotFound, is DomainError.UnexpectedResponse -> SignInFailure.NotPetkitLocal

        else -> common(error)
    }

    private fun common(error: DomainError): SignInFailure = when (error) {
        is DomainError.Network -> SignInFailure.Unreachable(error.detail)
        is DomainError.RateLimited -> SignInFailure.LockedOut(error.retryAfter)
        is DomainError.Server -> SignInFailure.ServerError(error.status, error.message)
        else -> SignInFailure.ServerError(status = null, message = null)
    }

    companion object {
        /**
         * What is wrong with the form before anything is sent, the address first; null when both
         * fields can be sent. The screen calls it on submit to put the error under the field.
         */
        fun checkForm(address: String, password: String): SignInFailure? {
            val check = ServerAddresses.normalise(address)
            return when {
                check is AddressCheck.Invalid -> SignInFailure.InvalidAddress(check.problem)
                password.isEmpty() -> SignInFailure.PasswordMissing
                else -> null
            }
        }

        /** `LoginRequest.device_name` is at most 64 characters (api/openapi.yaml). */
        const val MAX_PHONE_NAME = 64
        private const val UNAUTHORIZED = 401
    }
}
