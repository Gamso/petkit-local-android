package org.petkitlocal.core.network.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/*
 * DTOs mirror the `components.schemas` of petkit-local's OpenAPI document (api/openapi.yaml) one
 * to one: same field names, same required fields, same nullability. The contract tests in
 * src/test check each class against its schema, so a server change breaks a test, not the app.
 *
 * Conventions:
 * - A required schema property is a constructor parameter without a default; an optional one has
 *   a default (`null` meaning "not sent").
 * - `type: [X, "null"]` and `oneOf: [X, null]` are Kotlin nullable types.
 * - `number` is Double; `integer` is Long for ids and Int for amounts and counts.
 * - Enums stay Strings here: a value the server adds later must not fail a whole response. The
 *   data layer maps them to domain enums with a fallback. The known values are listed in an
 *   object next to each field, and the contract tests check them against the schema.
 */

/** `Error`: every refusal. */
@Serializable
data class ErrorDto(@SerialName("error") val error: String, @SerialName("message") val message: String)

/** `Ok`. */
@Serializable
data class OkDto(@SerialName("ok") val ok: Boolean)

/** Values of `Delivered`: how a write reached the device. */
object Delivered {
    /** Published to the device now. */
    const val MQTT = "mqtt"

    /** The device is not on MQTT and takes it on its next HTTP poll. */
    const val QUEUED = "queued"

    /** Only petkit-local's own state changed. */
    const val LOCAL = "local"

    val KNOWN = setOf(MQTT, QUEUED, LOCAL)
}

/** `WriteResult`. */
@Serializable
data class WriteResultDto(
    @SerialName("ok") val ok: Boolean,
    /** One of [Delivered.KNOWN]. */
    @SerialName("delivered") val delivered: String,
)
