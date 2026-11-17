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
}
