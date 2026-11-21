package org.petkitlocal.core.network.contract

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.StructureKind
import kotlinx.serialization.descriptors.elementDescriptors
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * The structural meaning of a JSON schema, with `$ref`, `allOf`, `oneOf: [X, null]` and
 * `type: [X, "null"]` already folded: what a DTO has to match.
 */
sealed interface Shape {
    val nullable: Boolean

    /** No `type`: any JSON value (`Entity.value`). */
    data class AnyValue(override val nullable: Boolean = true) : Shape

    data class Primitive(
        val type: String,
        override val nullable: Boolean,
        val enum: List<Any?>? = null,
        val const: Any? = null,
    ) : Shape

    data class Array(val items: Shape, override val nullable: Boolean) : Shape

    data class Object(
        val name: String?,
        val properties: Map<String, Shape>,
        val required: Set<String>,
        override val nullable: Boolean,
    ) : Shape

    /** `oneOf` told apart by [property], each [mapping] value an object. */
    data class Discriminated(val property: String, val mapping: Map<String, Object>, override val nullable: Boolean) :
        Shape
}

/** Folds a schema node of [spec] into a [Shape]. */
class ShapeReader(private val spec: OpenApiSpec) {
    fun read(node: Map<String, Any?>, name: String? = null): Shape {
        val ref = spec.refName(node)
        return when {
            ref != null -> read(spec.resolve(node), ref)
            "allOf" in node -> readAllOf(node["allOf"].asList(), name)
            "oneOf" in node -> readOneOf(node, name)
            else -> readTyped(node, name)
        }
    }

    private fun readTyped(node: Map<String, Any?>, name: String?): Shape {
        val types = typesOf(node)
        val nullable = "null" in types
        val nonNull = types - "null"
        return when {
            nonNull.isEmpty() && "properties" !in node -> Shape.AnyValue()
            nonNull.singleOrNull() == "array" -> Shape.Array(read(node["items"].asMap()), nullable)
            nonNull.singleOrNull() == "object" || "properties" in node -> readObject(node, name, nullable)
            nonNull.size == 1 -> readPrimitive(node, nonNull.single(), nullable)
            else -> error("Unsupported schema type $types in ${name ?: node}")
        }
    }

    private fun typesOf(node: Map<String, Any?>): List<String> = when (val type = node["type"]) {
        null -> emptyList()
        is String -> listOf(type)
        else -> type.asList().map { it as String }
    }

    private fun readPrimitive(node: Map<String, Any?>, type: String, nullable: Boolean) = Shape.Primitive(
        type = type,
        nullable = nullable,
        enum = node["enum"]?.asList(),
        const = node["const"],
    )

    private fun readObject(node: Map<String, Any?>, name: String?, nullable: Boolean) = Shape.Object(
        name = name,
        properties = node["properties"].asMapOrEmpty().mapValues { (_, v) -> read(v.asMap()) },
        required = node["required"].asList().map { it as String }.toSet(),
        nullable = nullable,
    )

    private fun readAllOf(parts: List<Any?>, name: String?): Shape.Object {
        val objects = parts.map { read(it.asMap()) as? Shape.Object ?: error("allOf of a non-object in $name") }
        return Shape.Object(
            name = name,
            properties = objects.fold(emptyMap()) { acc, o -> acc + o.properties },
            required = objects.flatMap { it.required }.toSet(),
            nullable = false,
        )
    }

    private fun readOneOf(node: Map<String, Any?>, name: String?): Shape {
        val options = node["oneOf"].asList().map { it.asMap() }
        val isNull: (Map<String, Any?>) -> Boolean = { it["type"] == "null" }
        val nullable = options.any(isNull)
        val rest = options.filterNot(isNull)
        val discriminator = node["discriminator"]?.asMap()
        if (discriminator != null) {
            val mapping = discriminator.map("mapping").mapValues { (_, ref) ->
                read(mapOf("\$ref" to ref)) as Shape.Object
            }
            return Shape.Discriminated(discriminator["propertyName"] as String, mapping, nullable)
        }
        val single = rest.singleOrNull() ?: error("oneOf with several non-null options in $name")
        return when (val shape = read(single)) {
            is Shape.Object -> shape.copy(nullable = true)
            is Shape.Primitive -> shape.copy(nullable = true)
            is Shape.Array -> shape.copy(nullable = true)
            is Shape.AnyValue -> shape
            is Shape.Discriminated -> shape.copy(nullable = true)
        }
    }
}

/**
 * Compares a DTO's serial descriptor with a [Shape] and lists every difference: a property the
 * DTO lacks or adds, required-ness, nullability, type. [discriminator] names the property a
 * sealed hierarchy carries outside the class itself (`type`).
 */
@OptIn(ExperimentalSerializationApi::class) // descriptor kinds: stable in practice, still marked
object DescriptorChecker {
    private const val JSON_ELEMENT = "kotlinx.serialization.json.JsonElement"

    fun check(shape: Shape, descriptor: SerialDescriptor, where: String, discriminator: String? = null): List<String> =
        mutableListOf<String>().also { check(shape, descriptor, where, discriminator, it) }

    private fun check(
        shape: Shape,
        descriptor: SerialDescriptor,
        where: String,
        discriminator: String?,
        errors: MutableList<String>,
    ) {
        if (shape.nullable && !descriptor.isNullable && shape !is Shape.AnyValue) {
            errors += "$where: nullable in the schema, not in the DTO"
        }
        when (shape) {
            is Shape.AnyValue -> if (descriptor.serialName.removeSuffix("?") != JSON_ELEMENT) {
                errors += "$where: any JSON value in the schema, ${descriptor.serialName} in the DTO"
            }

            is Shape.Primitive -> checkPrimitive(shape, descriptor, where, errors)

            is Shape.Array -> if (descriptor.kind != StructureKind.LIST) {
                errors += "$where: array in the schema, ${descriptor.kind} in the DTO"
            } else {
                check(shape.items, descriptor.getElementDescriptor(0), "$where[]", null, errors)
            }

            is Shape.Object -> checkObject(shape, descriptor, where, discriminator, errors)

            is Shape.Discriminated -> checkDiscriminated(shape, descriptor, where, errors)
        }
    }

    private fun checkPrimitive(
        shape: Shape.Primitive,
        descriptor: SerialDescriptor,
        where: String,
        errors: MutableList<String>,
    ) {
        val expected = when (shape.type) {
            "string" -> setOf(PrimitiveKind.STRING)
            "integer" -> setOf(PrimitiveKind.INT, PrimitiveKind.LONG)
            "number" -> setOf(PrimitiveKind.DOUBLE)
            "boolean" -> setOf(PrimitiveKind.BOOLEAN)
            else -> error("$where: unknown primitive ${shape.type}")
        }
        if (descriptor.kind !in expected) {
            errors += "$where: ${shape.type} in the schema, ${descriptor.kind} in the DTO"
        }
    }

    private fun checkObject(
        shape: Shape.Object,
        descriptor: SerialDescriptor,
        where: String,
        discriminator: String?,
        errors: MutableList<String>,
    ) {
        if (descriptor.kind != StructureKind.CLASS && descriptor.kind != StructureKind.OBJECT) {
            errors += "$where: object in the schema, ${descriptor.kind} in the DTO"
            return
        }
        val schemaProps = shape.properties.keys - setOfNotNull(discriminator)
        val dtoProps = (0 until descriptor.elementsCount).associateBy { descriptor.getElementName(it) }
        (schemaProps - dtoProps.keys).forEach { errors += "$where.$it: in the schema, missing from the DTO" }
        (dtoProps.keys - schemaProps).forEach { errors += "$where.$it: in the DTO, not in the schema" }
        schemaProps.intersect(dtoProps.keys).forEach { name ->
            checkProperty(shape, name, descriptor, dtoProps.getValue(name), where, errors)
        }
    }

    private fun checkProperty(
        shape: Shape.Object,
        name: String,
        descriptor: SerialDescriptor,
        index: Int,
        where: String,
        errors: MutableList<String>,
    ) {
        val propShape = shape.properties.getValue(name)
        val element = descriptor.getElementDescriptor(index)
        val required = name in shape.required
        val optional = descriptor.isElementOptional(index)
        if (required && optional) errors += "$where.$name: required in the schema, has a default in the DTO"
        if (!required && !optional) errors += "$where.$name: optional in the schema, required in the DTO"
        // An optional request field may be nullable in Kotlin: null means "not sent".
        val nullableOnlyInDto = !propShape.nullable && element.isNullable && propShape !is Shape.AnyValue
        // Except a property an older server does not send: the DTO reads its absence as null.
        // An inline schema has no name: its DTO's stands for it.
        val schemaName = shape.name ?: descriptor.serialName.substringAfterLast('.')
        val absentFromOlderServers = name in PendingContract.ABSENT_FROM_OLDER_SERVERS[schemaName].orEmpty()
        if (required && nullableOnlyInDto && !absentFromOlderServers) {
            errors += "$where.$name: never null in the schema, nullable in the DTO"
        }
        check(propShape, element, "$where.$name", null, errors)
    }

    private fun checkDiscriminated(
        shape: Shape.Discriminated,
        descriptor: SerialDescriptor,
        where: String,
        errors: MutableList<String>,
    ) {
        if (descriptor.kind != PolymorphicKind.SEALED) {
            errors += "$where: oneOf by ${shape.property} in the schema, ${descriptor.kind} in the DTO"
            return
        }
        val subclasses = descriptor.getElementDescriptor(1).elementDescriptors.associateBy { it.serialName }
        (shape.mapping.keys - subclasses.keys).forEach { errors += "$where: '$it' has no DTO subclass" }
        (subclasses.keys - shape.mapping.keys).forEach { errors += "$where: DTO subclass '$it' is not in the schema" }
        shape.mapping.filterKeys { it in subclasses }.forEach { (name, objectShape) ->
            checkConstDiscriminator(objectShape, shape.property, name, where, errors)
            check(objectShape, subclasses.getValue(name), "$where<$name>", shape.property, errors)
        }
    }

    private fun checkConstDiscriminator(
        shape: Shape.Object,
        property: String,
        expected: String,
        where: String,
        errors: MutableList<String>,
    ) {
        val const = (shape.properties[property] as? Shape.Primitive)?.const
        if (const != expected) errors += "$where<$expected>: '$property' const is $const"
    }
}

/**
 * Builds JSON documents that satisfy a [Shape]: every property present, enums and consts at a
 * legal value. With [nulls], every nullable property is null instead, so both readings of each
 * field are exercised.
 */
object SampleBuilder {
    fun build(shape: Shape, nulls: Boolean, choice: String? = null): JsonElement = when {
        nulls && shape.nullable && shape !is Shape.AnyValue -> JsonNull

        else -> when (shape) {
            is Shape.AnyValue -> if (nulls) JsonNull else JsonPrimitive(true)
            is Shape.Primitive -> primitive(shape)
            is Shape.Array -> JsonArray(listOf(build(shape.items, nulls)))
            is Shape.Object -> JsonObject(shape.properties.mapValues { (_, v) -> build(v, nulls) })
            is Shape.Discriminated -> build(shape.mapping.getValue(choice ?: shape.mapping.keys.first()), nulls)
        }
    }

    private fun primitive(shape: Shape.Primitive): JsonPrimitive {
        val value = shape.const ?: shape.enum?.firstOrNull { it != null }
        return when (shape.type) {
            "string" -> JsonPrimitive(value as? String ?: "text")
            "integer" -> JsonPrimitive((value as? Number)?.toLong() ?: 7L)
            "number" -> JsonPrimitive((value as? Number)?.toDouble() ?: 1.5)
            "boolean" -> JsonPrimitive(value as? Boolean ?: true)
            else -> error("unknown primitive ${shape.type}")
        }
    }
}
