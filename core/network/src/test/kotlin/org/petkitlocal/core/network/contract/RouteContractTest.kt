package org.petkitlocal.core.network.contract

import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import kotlin.reflect.full.declaredMemberFunctions
import kotlin.reflect.full.findAnnotation
import kotlin.reflect.jvm.jvmErasure
import kotlinx.serialization.serializer
import okhttp3.OkHttpClient
import okhttp3.ResponseBody
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.petkitlocal.core.network.LiveUpdatesSocket
import org.petkitlocal.core.network.NotificationSocket
import org.petkitlocal.core.network.PetkitApi
import org.petkitlocal.core.network.PetkitHttpClient
import org.petkitlocal.core.network.TalkSocketClient
import org.petkitlocal.core.network.video.LiveMediaSources
import org.petkitlocal.core.network.video.LiveSocketDataSource
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Contract tests, route side: every route [PetkitApi] calls exists in api/openapi.yaml with the
 * same method, path parameters, query parameters, request body and response body; and every
 * route of the document is either in [PetkitApi] or one of the non-REST routes below. Adding a
 * route to the app without the server, or to the server without the app, fails here.
 */
class RouteContractTest {
    private val spec = OpenApiSpec.vendored
    private val shapes = ShapeReader(spec)

    /** Routes of the document that are not REST, and the interface each one has in the app. */
    private val notRest: Map<String, KClass<*>> = mapOf(
        "GET /ws" to LiveUpdatesSocket::class,
        "GET /notifications/ws" to NotificationSocket::class,
        "GET /devices/{id}/live" to LiveSocketDataSource::class,
        "GET /devices/{id}/live/hls/{file}" to LiveMediaSources::class,
        "GET /devices/{id}/talk" to TalkSocketClient::class,
    )

    private data class Route(val method: String, val path: String, val function: KFunction<*>) {
        override fun toString() = "$method $path (${function.name})"
    }

    private val routes: List<Route> = PetkitApi::class.declaredMemberFunctions.map { function ->
        val (method, path) = listOfNotNull(
            function.findAnnotation<GET>()?.let { "GET" to it.value },
            function.findAnnotation<POST>()?.let { "POST" to it.value },
            function.findAnnotation<PUT>()?.let { "PUT" to it.value },
            function.findAnnotation<DELETE>()?.let { "DELETE" to it.value },
        ).singleOrNull() ?: error("${function.name} needs exactly one HTTP method annotation")
        Route(method, "/" + path.substringBefore('?'), function)
    }

    @Test
    fun `every route of the document is called by the app, or is a known non-REST route`() {
        val inApp = routes.map { "${it.method} ${it.path}" }.toSet()
        val inSpec = spec.operations.map { it.toString() }.toSet()
        assertEquals((inSpec - notRest.keys - PendingContract.ROUTES).sorted(), inApp.sorted())
        assertTrue(inSpec.containsAll(PendingContract.ROUTES)) { "A pending route left the document" }
        assertTrue(inSpec.containsAll(notRest.keys)) { "A non-REST route left the document: ${notRest.keys - inSpec}" }
    }

    @Test
    fun `Retrofit accepts every method of the service`() {
        PetkitHttpClient.api(OkHttpClient(), validateEagerly = true)
    }

    @TestFactory
    fun `each route matches its operation`(): List<DynamicTest> = routes.map { route ->
        DynamicTest.dynamicTest(route.toString()) {
            val operation = spec.operation(route.method, route.path)
                ?: throw AssertionError("$route is not in api/openapi.yaml")
            val errors = mutableListOf<String>()
            checkParameters(route, operation, errors)
            checkRequestBody(route, operation, errors)
            checkResponse(route, operation, errors)
            assertTrue(errors.isEmpty()) { errors.joinToString("\n", "$route:\n") }
        }
    }

    private fun checkParameters(route: Route, operation: OpenApiSpec.Operation, errors: MutableList<String>) {
        val appPath = route.function.parameters.mapNotNull { it.findAnnotation<Path>()?.value }.toSet()
        val appQuery = route.function.parameters.mapNotNull { it.findAnnotation<Query>()?.value }.toSet()
        val specPath = operation.parameters.filter { it.location == "path" }.map { it.name }.toSet()
        val specQuery = operation.parameters.filter { it.location == "query" }.map { it.name }.toSet()
        val templated = Regex("\\{([^}]+)}").findAll(route.path).map { it.groupValues[1] }.toSet()
        if (appPath != specPath || templated != specPath) {
            errors += "path parameters: app $appPath, template $templated, document $specPath"
        }
        if (appQuery != specQuery) errors += "query parameters: app $appQuery, document $specQuery"
    }

    private fun checkRequestBody(route: Route, operation: OpenApiSpec.Operation, errors: MutableList<String>) {
        val body = route.function.parameters.singleOrNull { it.findAnnotation<Body>() != null }
        val schema = operation.requestBodySchema
        when {
            body == null && schema != null && operation.requestBodyRequired ->
                errors += "the document requires a body the app does not send"

            body != null && schema == null -> errors += "the app sends a body the document does not take"

            body != null && schema != null -> errors += DescriptorChecker.check(
                shapes.read(schema),
                serializer(body.type).descriptor,
                "request body",
            )
        }
    }

    private fun checkResponse(route: Route, operation: OpenApiSpec.Operation, errors: MutableList<String>) {
        val returned = route.function.returnType
        val raw = returned.jvmErasure == ResponseBody::class ||
            (
                returned.jvmErasure == Response::class &&
                    returned.arguments.single().type?.jvmErasure == ResponseBody::class
                )
        val schema = operation.successJsonSchema
        when {
            raw && schema != null -> errors += "the document answers JSON; the app reads raw bytes"

            raw -> Unit

            // a file: an image, a video, the YAML document itself
            schema == null -> errors += "the app expects JSON; the document answers ${operation.successResponses}"

            else -> errors += DescriptorChecker.check(shapes.read(schema), serializer(returned).descriptor, "response")
        }
    }
}
