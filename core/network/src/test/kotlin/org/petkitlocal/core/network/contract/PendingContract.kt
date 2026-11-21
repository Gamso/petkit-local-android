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
     *
     * `supports_ai` and `faces` (petkit-local#131): which devices recognise pets by their face,
     * and a pet's face photos; such a server cannot manage pets either, and the app offers no
     * device and lists no photo.
     *
     * `accepted_by`, `waiting_for` and the upload's `quality` (petkit-local#132): which devices
     * took a photo in, and what the server found wrong with it; the app says nothing of either.
     * The upload's answer is an inline schema, named here by its DTO.
     */
    val ABSENT_FROM_OLDER_SERVERS: Map<String, Set<String>> = mapOf(
        "DeviceSummary" to setOf("model_name", "talk", "supports_ai"),
        "DeviceDetail" to setOf("model_name", "talk", "supports_ai"),
        "Pet" to setOf("faces"),
        "PetDetail" to setOf("faces"),
        "PetFace" to setOf("accepted_by", "waiting_for"),
        "PetFaceResultDto" to setOf("quality"),
    )
}
