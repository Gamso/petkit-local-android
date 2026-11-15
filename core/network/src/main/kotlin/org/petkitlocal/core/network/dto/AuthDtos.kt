package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** `Session`: one signed-in phone. Times are Unix epoch seconds. */
@Serializable
data class SessionDto(
    /** Public id; the panel revokes by it. */
    @SerialName("id") val id: String,
    /** The `device_name` given at login. */
    @SerialName("name") val name: String,
    @SerialName("created") val created: Double,
    /** Updated at most every 5 minutes. */
    @SerialName("last_used") val lastUsed: Double,
    /** `last_used` + 90 days. */
    @SerialName("expires") val expires: Double,
)

/** `Info`: server identity and clock, from `GET /info`. */
@Serializable
data class InfoDto(
    @SerialName("api_version") val apiVersion: Int,
    @SerialName("server_time") val serverTime: Double,
    @SerialName("authenticated") val authenticated: Boolean,
    @SerialName("server_version") val serverVersion: String?,
    @SerialName("session") val session: SessionDto?,
)

/** `LoginRequest`. */
@Serializable
data class LoginRequestDto(
    @SerialName("password") val password: String,
    /** At most 64 characters; shown in the panel's list of signed-in phones. */
    @SerialName("device_name") val deviceName: String? = null,
)

/** `LoginResponse`. The token is shown only in this response. */
@Serializable
data class LoginResponseDto(
    @SerialName("token") val token: String,
    /** Always `Bearer`. */
    @SerialName("token_type") val tokenType: String,
    @SerialName("idle_timeout_sec") val idleTimeoutSec: Long,
    @SerialName("session") val session: SessionDto,
)

/** `ChangePasswordRequest`. */
@Serializable
data class ChangePasswordRequestDto(
    @SerialName("current_password") val currentPassword: String,
    /** 8 to 1024 characters. */
    @SerialName("new_password") val newPassword: String,
)

/** `ChangePasswordResponse`. */
@Serializable
data class ChangePasswordResponseDto(
    @SerialName("ok") val ok: Boolean,
    @SerialName("revoked_sessions") val revokedSessions: Int,
)
