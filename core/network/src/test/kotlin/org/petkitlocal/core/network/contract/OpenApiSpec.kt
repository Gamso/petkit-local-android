package org.petkitlocal.core.network.contract

import java.io.File
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor

/**
 * A small reader for the parts of an OpenAPI 3.1 document the contract tests need: operations
 * with their parameters, request and response schemas, and the component schemas, with `$ref`s
 * followed. Not a general OpenAPI library; it fails loudly on what it does not understand.
 */
class OpenApiSpec private constructor(private val root: Map<String, Any?>) {
    val schemas: Map<String, Map<String, Any?>> =
        root.map("components").map("schemas").mapValues { (_, v) -> v.asMap() }

    val operations: List<Operation> = root.map("paths").flatMap { (path, item) ->
        val pathItem = item.asMap()
        val shared = pathItem["parameters"].asList().map { resolve(it.asMap()) }
        pathItem.filterKeys { it in HTTP_METHODS }.map { (method, op) ->
            val node = op.asMap()
            Operation(
                path = path,
                method = method.uppercase(),
                parameters = shared + node["parameters"].asList().map { resolve(it.asMap()) },
                node = node,
                spec = this,
            )
        }
    }

    fun operation(method: String, path: String): Operation? =
        operations.firstOrNull { it.method == method && it.path == path }

    /** Follows a local `$ref` (`#/components/...`) until a node that is not one. */
    fun resolve(node: Map<String, Any?>): Map<String, Any?> {
        var current = node
        repeat(MAX_REF_DEPTH) {
            val ref = current["\$ref"] as? String ?: return current
            require(ref.startsWith("#/")) { "Only local \$refs are supported: $ref" }
            current = ref.removePrefix("#/").split('/').fold<String, Any?>(root) { acc, key ->
                acc.asMap()[key] ?: error("Unresolvable \$ref $ref")
            }.asMap()
        }
        error("\$ref chain too deep at $node")
    }

    /** The name of the component schema a node refers to, for messages. */
    fun refName(node: Map<String, Any?>): String? = (node["\$ref"] as? String)?.substringAfterLast('/')

    data class Parameter(val name: String, val location: String, val required: Boolean, val schema: Map<String, Any?>?)

    class Operation(
        val path: String,
        val method: String,
        parameters: List<Map<String, Any?>>,
        private val node: Map<String, Any?>,
        private val spec: OpenApiSpec,
    ) {
        val parameters: List<Parameter> = parameters.map {
            Parameter(it["name"] as String, it["in"] as String, it["required"] == true, it["schema"]?.asMap())
        }

        /** The JSON request body schema, or null when the operation takes none. */
        val requestBodySchema: Map<String, Any?>? = node["requestBody"]?.asMap()?.let(spec::resolve)
            ?.map("content")?.get(JSON)?.asMap()?.get("schema")?.asMap()

        /** Every content type the request body may have; empty when the operation takes none. */
        val requestBodyContentTypes: Set<String> = node["requestBody"]?.asMap()?.let(spec::resolve)
            ?.get("content").asMapOrEmpty().keys

        val requestBodyRequired: Boolean =
            node["requestBody"]?.asMap()?.let(spec::resolve)?.get("required") == true

        /** The 2xx responses, by status, each with its content types and schemas. */
        val successResponses: Map<String, Map<String, Map<String, Any?>?>> = node.map("responses")
            .filterKeys { it.startsWith("2") }
            .mapValues { (_, response) ->
                spec.resolve(response.asMap())["content"].asMapOrEmpty()
                    .mapValues { (_, media) -> media.asMap()["schema"]?.asMap() }
            }

        /** The schema of the one JSON 2xx answer, or null when the success is not JSON. */
        val successJsonSchema: Map<String, Any?>?
            get() = successResponses.values.firstNotNullOfOrNull { it[JSON] }

        override fun toString() = "$method $path"
    }

    companion object {
        const val JSON = "application/json"
        private const val MAX_REF_DEPTH = 16
        private val HTTP_METHODS = setOf("get", "put", "post", "delete", "patch", "head", "options", "trace")

        /** The vendored document, api/openapi.yaml, whose path Gradle passes in. */
        val vendored: OpenApiSpec by lazy {
            val path = System.getProperty("petkit.openapi")
                ?: error("System property petkit.openapi is not set; run the tests through Gradle")
            load(File(path))
        }

        fun load(file: File): OpenApiSpec = file.inputStream().use { input ->
            val yaml = Yaml(SafeConstructor(LoaderOptions()))
            OpenApiSpec(yaml.load<Any?>(input).asMap())
        }
    }
}

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMap(): Map<String, Any?> =
    this as? Map<String, Any?> ?: error("Expected a mapping, got ${this?.javaClass?.simpleName}: $this")

@Suppress("UNCHECKED_CAST")
internal fun Any?.asMapOrEmpty(): Map<String, Any?> = this as? Map<String, Any?> ?: emptyMap()

internal fun Any?.asList(): List<Any?> = this as? List<Any?> ?: emptyList()

internal fun Map<String, Any?>.map(key: String): Map<String, Any?> = this[key].asMap()

/** A YAML value as loaded by SnakeYAML, as the JSON it stands for. */
internal fun yamlToJson(value: Any?): JsonElement = when (value) {
    null -> JsonNull
    is Boolean -> JsonPrimitive(value)
    is Number -> JsonPrimitive(value)
    is String -> JsonPrimitive(value)
    is Map<*, *> -> JsonObject(value.entries.associate { (k, v) -> k.toString() to yamlToJson(v) })
    is List<*> -> JsonArray(value.map(::yamlToJson))
    else -> error("Not a JSON value: ${value.javaClass.simpleName}")
}
