package io.github.gamso.petkitlocal.mock.api

/**
 * A refusal with the API's JSON body, `{"error": code, "message": text}`. Thrown anywhere a
 * request is handled and rendered by the routing layer, as petkit-local's `ApiError` is.
 */
class ApiException(
    val status: Int,
    val code: String,
    override val message: String,
    val headers: Map<String, String> = emptyMap(),
) : RuntimeException(message)
