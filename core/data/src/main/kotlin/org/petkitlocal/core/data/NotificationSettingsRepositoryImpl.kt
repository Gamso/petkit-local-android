package org.petkitlocal.core.data

import javax.inject.Inject
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.map
import org.petkitlocal.core.domain.settings.NotificationSettings
import org.petkitlocal.core.domain.settings.NotificationSettingsRepository
import org.petkitlocal.core.domain.settings.ServerNotificationKind
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.dto.NotificationKindsRequestDto
import org.petkitlocal.core.network.dto.NotificationSettingsDto
import org.petkitlocal.core.network.error.apiCall

/**
 * `GET`/`PUT /notifications`. The server holds the choice with the session and filters what it
 * sends by it, so nothing is kept on the phone: what is shown is what the server answered.
 */
class NotificationSettingsRepositoryImpl @Inject constructor(private val api: PetkitApi) :
    NotificationSettingsRepository {
    override suspend fun load(): Outcome<NotificationSettings> =
        apiCall { api.getNotificationSettings() }.map { it.toDomain() }

    override suspend fun setKinds(kinds: List<String>?): Outcome<NotificationSettings> =
        apiCall { api.setNotificationKinds(NotificationKindsRequestDto(kinds)) }.map { it.toDomain() }
}

internal fun NotificationSettingsDto.toDomain() = NotificationSettings(
    chosen = kinds,
    serverKinds = serverKinds.map { ServerNotificationKind(it.kind, enabledOnServer = it.enabled) },
    connected = connected,
    lastId = lastId,
)
