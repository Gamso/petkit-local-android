package org.petkitlocal.core.network.contract

/**
 * Parts of api/openapi.yaml the app does not model yet, each with the issue that will. The
 * contract tests skip exactly these and nothing else, and fail when one leaves the document, so
 * the list cannot outlive what it excuses. The `Session` fields they reach (`kinds`, `push`) are
 * modelled already, since every login carries them.
 */
object PendingContract {
    /** The notification socket and UnifiedPush: issue #23. The kinds (`/notifications`) are modelled. */
    val ROUTES = setOf(
        "PUT /push",
        "DELETE /push",
        "GET /notifications/ws",
    )

    /** The bodies and socket messages of [ROUTES]: issue #23. */
    val SCHEMAS = setOf(
        "PushRegistrationRequest",
        "NotificationSocketMessage",
        "NotificationHello",
        "NotificationDelivery",
        "NotificationPong",
        "PushMessage",
    )
}
