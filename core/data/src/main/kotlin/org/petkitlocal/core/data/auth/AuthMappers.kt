package org.petkitlocal.core.data.auth

import kotlin.time.Instant
import org.petkitlocal.core.domain.auth.ServerInfo
import org.petkitlocal.core.domain.auth.Session
import org.petkitlocal.core.network.dto.InfoDto
import org.petkitlocal.core.network.dto.SessionDto

private const val MILLIS_PER_SECOND = 1000.0

/** The API's times are Unix epoch seconds, with a fraction. */
internal fun epochSeconds(seconds: Double): Instant =
    Instant.fromEpochMilliseconds((seconds * MILLIS_PER_SECOND).toLong())

internal fun SessionDto.toDomain() = Session(
    id = id,
    name = name,
    created = epochSeconds(created),
    lastUsed = epochSeconds(lastUsed),
    expires = epochSeconds(expires),
)

internal fun InfoDto.toDomain() = ServerInfo(
    apiVersion = apiVersion,
    serverTime = epochSeconds(serverTime),
    authenticated = authenticated,
    serverVersion = serverVersion,
    session = session?.toDomain(),
)
