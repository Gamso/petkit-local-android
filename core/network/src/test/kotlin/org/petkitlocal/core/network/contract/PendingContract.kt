package org.petkitlocal.core.network.contract

/**
 * Parts of api/openapi.yaml the app does not model, each with the reason. The contract tests skip
 * exactly these and nothing else, and fail when one leaves the document, so the list cannot
 * outlive what it excuses. The `Session` fields they reach (`push`) are modelled already, since
 * every login carries them.
 */
object PendingContract {
    /**
     * UnifiedPush endpoint registration: the server keeps it for people who already run ntfy, and
     * the app does not use it in v1 (ADR 0007): it holds its own notification socket instead.
     */
    val ROUTES = setOf(
        "PUT /push",
        "DELETE /push",
        // Talking through a camera: the app's client for it is not written yet.
        "GET /devices/{id}/talk",
    )

    /** The bodies of [ROUTES]. */
    val SCHEMAS = setOf(
        "PushRegistrationRequest",
    )

    /**
     * Properties the document requires and never sends null, by schema, that a server released
     * before them does not send at all. Their DTO reads them nullable (absent is null under
     * `explicitNulls = false`) and the app falls back, so it keeps working against such a server.
     * The checks accept exactly these, and fail when one is no longer required and non-null.
     *
     * `model_name` (petkit-local#107): the full product name; without it the app names the
     * device by `model`, as it always did.
     *
     * `talk` (petkit-local#108): whether the camera can be talked through; such a server has no
     * talk route either, and the app says it is too old to talk.
     */
    val ABSENT_FROM_OLDER_SERVERS: Map<String, Set<String>> = mapOf(
        "DeviceSummary" to setOf("model_name", "talk"),
        "DeviceDetail" to setOf("model_name", "talk"),
    )
}
