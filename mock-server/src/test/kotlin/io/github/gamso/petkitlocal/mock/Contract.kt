package io.github.gamso.petkitlocal.mock

import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.dialect.Dialects
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import tools.jackson.dataformat.yaml.YAMLMapper
import java.util.concurrent.ConcurrentHashMap

/**
 * `api/openapi.yaml` as a test oracle: every response a test receives is checked against the
 * operation's declared statuses, content types, headers and JSON Schema (OpenAPI 3.1 dialect,
 * networknt json-schema-validator), and every key the mock sends must be one the schema names.
 * The last check is what the schema alone cannot do (it allows extra keys), and it is what keeps
 * the mock from growing fields the real server does not have.
 */
object Contract {
    private const val DOC = "classpath:openapi.yaml"
    private val yaml = YAMLMapper()
    private val json = JsonMapper()
    val doc: JsonNode = yaml.readTree(Contract::class.java.getResourceAsStream("/openapi.yaml"))
    private val registry = SchemaRegistry.withDialect(Dialects.getOpenApi31())
    private val compiled = ConcurrentHashMap<String, Schema>()

    /**
     * An array's elements. Kotlin's `Iterable` extensions are not used on Jackson 3 nodes: a node
     * iterates over its values in a way that does not match `get(i)` for these documents.
     */
    private fun JsonNode.elements(): List<JsonNode> = (0 until size()).map { get(it) }

    /** Every `(METHOD, template)` the spec declares. */
    val operations: Set<Pair<String, String>> =
        doc
            .path("paths")
            .properties()
            .flatMap { (path, item) ->
                item
                    .properties()
                    .map { it.key }
                    .filter { it in setOf("get", "post", "put", "delete", "patch") }
                    .map { it.uppercase() to path }
            }.toSet()

    fun operation(
        method: String,
        template: String,
    ): JsonNode {
        val op = doc.path("paths").path(template).path(method.lowercase())
        require(!op.isMissingNode) { "the spec has no $method $template" }
        return op
    }

    fun resolve(node: JsonNode): JsonNode {
        var n = node
        while (n.has("\$ref")) {
            val ref = n.get("\$ref").asString()
            require(ref.startsWith("#/")) { "external ref $ref" }
            n = doc.at(ref.removePrefix("#"))
        }
        return n
    }

    /** The declared response for a status, or null if the operation does not declare it. */
    fun response(
        method: String,
        template: String,
        status: Int,
    ): JsonNode? =
        operation(method, template)
            .path("responses")
            .path(status.toString())
            .takeUnless { it.isMissingNode }
            ?.let(::resolve)

    /** A schema node with every `#/...` ref made absolute, so it can be compiled on its own. */
    private fun absolutize(node: JsonNode): JsonNode = node.deepCopy().also(::absolutizeInPlace)

    private fun absolutizeInPlace(n: JsonNode) {
        if (n.isArray) return n.elements().forEach(::absolutizeInPlace)
        if (n !is ObjectNode) return
        val ref = n.get("\$ref")
        if (ref != null && ref.isString && ref.asString().startsWith("#/")) n.put("\$ref", DOC + ref.asString())
        // A discriminator's mapping names schemas by reference too.
        val mapping = n.path("discriminator").path("mapping") as? ObjectNode
        mapping?.properties()?.toList()?.forEach { (k, v) -> if (v.asString().startsWith("#/")) mapping.put(k, DOC + v.asString()) }
        n.properties().forEach { absolutizeInPlace(it.value) }
    }

    private fun schema(
        key: String,
        node: JsonNode,
    ): Schema = compiled.getOrPut(key) { registry.getSchema(json.writeValueAsString(absolutize(node)), InputFormat.JSON) }

    /** Problems with one HTTP response; empty when it matches the spec. */
    fun check(
        method: String,
        template: String,
        status: Int,
        contentType: String?,
        headers: (String) -> String?,
        body: String,
    ): List<String> {
        val declared = response(method, template, status) ?: return listOf("$method $template: status $status is not declared")
        val problems = mutableListOf<String>()
        // Declared headers are optional in OpenAPI unless `required`; the ones the app relies on are
        // enforced anyway: `Retry-After` on every 429 and on a 401 `invalid_password` (the 401 of
        // login also covers `password_not_set`, which has none), `WWW-Authenticate` on a 401.
        val error = runCatching { json.readTree(body).path("error").asString("") }.getOrDefault("")
        declared.path("headers").properties().forEach { (name, _) ->
            val optional = name == "Retry-After" && status == 401 && error != "invalid_password"
            if (headers(name) == null && !optional) problems += "$method $template $status: header $name is declared but missing"
        }
        val content = declared.path("content")
        if (content.isMissingNode || content.isEmpty) return problems
        val mime = contentType?.substringBefore(';')?.trim()
        val types = content.properties().map { it.key }
        if ("*/*" !in types && mime !in types) {
            problems += "$method $template $status: content type $mime not among $types"
            return problems
        }
        if (mime == "application/json") {
            val schemaNode = content.path("application/json").path("schema")
            problems += validate("$method $template $status", schemaNode, body)
        }
        return problems
    }

    /** One `/ws` message against `UpdateMessage`. */
    fun checkMessage(text: String): List<String> = validate("ws", doc.at("/components/schemas/UpdateMessage"), text)

    fun validate(
        where: String,
        schemaNode: JsonNode,
        body: String,
    ): List<String> {
        val value =
            runCatching { json.readTree(body) }.getOrElse {
                return listOf("$where: body is not JSON: ${body.take(200)}")
            }
        val errors = schema("$where:${schemaNode.hashCode()}", schemaNode).validate(value).map { "$where: ${it.message}" }
        val unknown = mutableListOf<String>()
        unknownKeys(schemaNode, value, "$", unknown)
        return errors + unknown.map { "$where: key $it is not in the schema" }
    }

    /** Keys of [value] the schema does not name, walking `$ref`, `allOf`, `oneOf` and arrays. */
    private fun unknownKeys(
        schemaNode: JsonNode,
        value: JsonNode,
        path: String,
        out: MutableList<String>,
    ) {
        val s = resolve(schemaNode)
        if (s.has("oneOf")) {
            val branch =
                s
                    .get("oneOf")
                    .elements()
                    .map(::resolve)
                    .firstOrNull { matches(it, value) } ?: return
            unknownKeys(branch, value, path, out)
            return
        }
        val properties = LinkedHashMap<String, JsonNode>()
        collectProperties(s, properties)
        if (value.isObject && (properties.isNotEmpty() || s.path("type").asString("") == "object")) {
            value.properties().forEach { (k, v) ->
                val p = properties[k]
                if (p == null) out += "$path.$k" else unknownKeys(p, v, "$path.$k", out)
            }
        } else if (value.isArray && s.has("items")) {
            value.elements().forEachIndexed { i, v -> unknownKeys(s.get("items"), v, "$path[$i]", out) }
        }
    }

    private fun collectProperties(
        s: JsonNode,
        into: MutableMap<String, JsonNode>,
    ) {
        s.path("properties").properties().forEach { (k, v) -> into[k] = v }
        s.path("allOf").elements().forEach { collectProperties(resolve(it), into) }
    }

    /** Whether a `oneOf` branch is the one [value] was meant for (type, then a `type` const). */
    private fun matches(
        branch: JsonNode,
        value: JsonNode,
    ): Boolean {
        val type = branch.path("type").asString("")
        if (value.isNull) return type == "null"
        if (type == "null") return false
        val const = branch.path("properties").path("type").path("const")
        if (!const.isMissingNode) return value.path("type").asString("") == const.asString()
        return true
    }
}
