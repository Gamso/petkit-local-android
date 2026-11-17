package org.petkitlocal.core.domain.auth

import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow
import org.petkitlocal.core.domain.Outcome

/** The session state the app's navigation follows: sign-in when signed out, Home when signed in. */
class ObserveAuthStateUseCase @Inject constructor(private val repository: AuthRepository) {
    operator fun invoke(): StateFlow<AuthState> = repository.authState
}

/** Signs out here: the server forgets this phone's session, and the phone forgets everything. */
class SignOutUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.signOut()
}

/** The server's version and this phone's name there, from `GET /info` with the session's token. */
class GetServerInfoUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke(): Outcome<ServerInfo> = repository.serverInfo()
}

/** Checks the stored session with the server when the app comes to the foreground. */
class ValidateSessionUseCase @Inject constructor(private val repository: AuthRepository) {
    suspend operator fun invoke() = repository.validateSession()
}

/** What the sign-in form starts with: the last server, and this phone's model as its name. */
class GetSignInDefaultsUseCase @Inject constructor(
    private val repository: AuthRepository,
    private val phoneName: PhoneNameProvider,
) {
    data class Defaults(val serverAddress: String, val phoneName: String)

    suspend operator fun invoke(): Defaults = Defaults(
        serverAddress = repository.lastServerBaseUrl().orEmpty(),
        phoneName = phoneName.defaultPhoneName().take(SignInUseCase.MAX_PHONE_NAME),
    )
}
