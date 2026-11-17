package org.petkitlocal.core.network.contract

import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.nullable
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestFactory
import org.petkitlocal.core.network.PetkitJson
import org.petkitlocal.core.network.dto.AccessoryKind
import org.petkitlocal.core.network.dto.AccessoryRefDto
import org.petkitlocal.core.network.dto.ActionDto
import org.petkitlocal.core.network.dto.ActionRequestDto
import org.petkitlocal.core.network.dto.ActionResultDto
import org.petkitlocal.core.network.dto.ChangePasswordRequestDto
import org.petkitlocal.core.network.dto.ChangePasswordResponseDto
import org.petkitlocal.core.network.dto.DeferredFeedDto
import org.petkitlocal.core.network.dto.DeferredFeedRequestDto
import org.petkitlocal.core.network.dto.DeferredFeedResultDto
import org.petkitlocal.core.network.dto.Delivered
import org.petkitlocal.core.network.dto.DeviceConnection
import org.petkitlocal.core.network.dto.DeviceDetailDto
import org.petkitlocal.core.network.dto.DeviceKind
import org.petkitlocal.core.network.dto.DeviceSummaryDto
import org.petkitlocal.core.network.dto.DeviceTransport
import org.petkitlocal.core.network.dto.EntityCategory
import org.petkitlocal.core.network.dto.EntityChangeDto
import org.petkitlocal.core.network.dto.EntityComponent
import org.petkitlocal.core.network.dto.EntityDto
import org.petkitlocal.core.network.dto.EntityWriteRequestDto
import org.petkitlocal.core.network.dto.EntityWriteResultDto
import org.petkitlocal.core.network.dto.ErrorDto
import org.petkitlocal.core.network.dto.EventDetailDto
import org.petkitlocal.core.network.dto.EventFieldDto
import org.petkitlocal.core.network.dto.FeedingGroupDto
import org.petkitlocal.core.network.dto.FeedingPlanDto
import org.petkitlocal.core.network.dto.FeedingPlanResultDto
import org.petkitlocal.core.network.dto.FeedingPlanWriteDto
import org.petkitlocal.core.network.dto.InfoDto
import org.petkitlocal.core.network.dto.LoginRequestDto
import org.petkitlocal.core.network.dto.LoginResponseDto
import org.petkitlocal.core.network.dto.MealDto
import org.petkitlocal.core.network.dto.MediaDto
import org.petkitlocal.core.network.dto.NotificationKind
import org.petkitlocal.core.network.dto.NotificationKindsRequestDto
import org.petkitlocal.core.network.dto.NotificationSettingsDto
import org.petkitlocal.core.network.dto.NotificationSocketMessageDto
import org.petkitlocal.core.network.dto.OkDto
import org.petkitlocal.core.network.dto.PetDetailDto
import org.petkitlocal.core.network.dto.PetDto
import org.petkitlocal.core.network.dto.PetRefDto
import org.petkitlocal.core.network.dto.PetStatsDto
import org.petkitlocal.core.network.dto.PushMessageDto
import org.petkitlocal.core.network.dto.PushRegistrationDto
import org.petkitlocal.core.network.dto.SessionDto
import org.petkitlocal.core.network.dto.TimelineFilter
import org.petkitlocal.core.network.dto.TimelineItemDto
import org.petkitlocal.core.network.dto.TimelinePageDto
import org.petkitlocal.core.network.dto.TimelineStepDto
import org.petkitlocal.core.network.dto.UpdateMessageDto
import org.petkitlocal.core.network.dto.WriteResultDto

/**
 * Contract tests, schema side: every component schema of api/openapi.yaml has a DTO with the
 * same properties, required-ness, nullability and types, and the DTO reads every document the
 * schema allows. A schema added, renamed or changed on the server fails here.
 */
class SchemaContractTest {
    private val spec = OpenApiSpec.vendored
    private val shapes = ShapeReader(spec)

    /** Component schema -> the DTO that mirrors it. */
    private val dtos: Map<String, KSerializer<*>> = mapOf(
        "Error" to ErrorDto.serializer(),
        "Ok" to OkDto.serializer(),
        "Session" to SessionDto.serializer(),
        "Info" to InfoDto.serializer(),
        "LoginRequest" to LoginRequestDto.serializer(),
        "LoginResponse" to LoginResponseDto.serializer(),
        "ChangePasswordRequest" to ChangePasswordRequestDto.serializer(),
        "ChangePasswordResponse" to ChangePasswordResponseDto.serializer(),
        "DeviceSummary" to DeviceSummaryDto.serializer(),
        "DeviceDetail" to DeviceDetailDto.serializer(),
        "AccessoryRef" to AccessoryRefDto.serializer(),
        "Action" to ActionDto.serializer(),
        "ActionRequest" to ActionRequestDto.serializer(),
        "WriteResult" to WriteResultDto.serializer(),
        "ActionResult" to ActionResultDto.serializer(),
        "EntityWriteRequest" to EntityWriteRequestDto.serializer(),
        "EntityWriteResult" to EntityWriteResultDto.serializer(),
        "EntityChange" to EntityChangeDto.serializer(),
        "Entity" to EntityDto.serializer(),
        "Meal" to MealDto.serializer(),
        "FeedingGroup" to FeedingGroupDto.serializer(),
        "FeedingPlan" to FeedingPlanDto.serializer(),
        "FeedingPlanWrite" to FeedingPlanWriteDto.serializer(),
        "FeedingPlanResult" to FeedingPlanResultDto.serializer(),
        "DeferredFeed" to DeferredFeedDto.serializer(),
        "DeferredFeedRequest" to DeferredFeedRequestDto.serializer(),
        "DeferredFeedResult" to DeferredFeedResultDto.serializer(),
        "Media" to MediaDto.serializer(),
        "PetRef" to PetRefDto.serializer(),
        "TimelineStep" to TimelineStepDto.serializer(),
        "TimelineItem" to TimelineItemDto.serializer(),
        "TimelinePage" to TimelinePageDto.serializer(),
        "EventField" to EventFieldDto.serializer(),
        "EventDetail" to EventDetailDto.serializer(),
        "Pet" to PetDto.serializer(),
        "PetStats" to PetStatsDto.serializer(),
        "PetDetail" to PetDetailDto.serializer(),
        "UpdateMessage" to UpdateMessageDto.serializer(),
        "NotificationSocketMessage" to NotificationSocketMessageDto.serializer(),
        "PushMessage" to PushMessageDto.serializer(),
        "PushRegistration" to PushRegistrationDto.serializer(),
        "NotificationSettings" to NotificationSettingsDto.serializer(),
        "NotificationKindsRequest" to NotificationKindsRequestDto.serializer(),
        // A named `oneOf: [array, null]`, used by `Session.kinds`: a nullable list of kinds.
        "NotificationKinds" to ListSerializer(String.serializer()).nullable,
    )

    /**
     * The members of the two socket messages (`UpdateMessage`, `NotificationSocketMessage`): DTO
     * subclasses, checked with `type` as the discriminator.
     */
    private val updateMessages: Map<String, KSerializer<*>> = mapOf(
        "UpdateHello" to UpdateMessageDto.Hello.serializer(),
        "UpdateState" to UpdateMessageDto.State.serializer(),
        "UpdateDevice" to UpdateMessageDto.Device.serializer(),
        "UpdateDeviceRemoved" to UpdateMessageDto.DeviceRemoved.serializer(),
        "UpdateTimeline" to UpdateMessageDto.Timeline.serializer(),
        "UpdatePong" to UpdateMessageDto.Pong.serializer(),
        "NotificationHello" to NotificationSocketMessageDto.Hello.serializer(),
        "NotificationDelivery" to NotificationSocketMessageDto.Delivery.serializer(),
        "NotificationPong" to NotificationSocketMessageDto.Pong.serializer(),
    )

    /** String enums kept as Strings, with the values the app knows. */
    private val stringEnums: Map<String, Set<String>> = mapOf(
        "Delivered" to Delivered.KNOWN,
        "NotificationKind" to NotificationKind.KNOWN,
    )

    @Test
    fun `every schema of the document has a DTO, and every DTO a schema`() {
        val modelled = dtos.keys + updateMessages.keys + stringEnums.keys + PendingContract.SCHEMAS
        assertEquals(spec.schemas.keys.sorted(), modelled.sorted())
    }

    @TestFactory
    fun `each DTO matches its schema`(): List<DynamicTest> = (dtos + updateMessages).map { (name, serializer) ->
        DynamicTest.dynamicTest(name) {
            val discriminator = if (name in updateMessages) "type" else null
            val errors = DescriptorChecker.check(schemaShape(name), serializer.descriptor, name, discriminator)
            assertTrue(errors.isEmpty()) { errors.joinToString("\n", "$name does not match its schema:\n") }
        }
    }

    @TestFactory
    fun `each DTO reads every document its schema allows, and writes it back`(): List<DynamicTest> =
        dtos.flatMap { (name, serializer) ->
            val shape = schemaShape(name)
            val choices = (shape as? Shape.Discriminated)?.mapping?.keys?.toList() ?: listOf(null)
            choices.flatMap { choice ->
                listOf(false, true).map { nulls ->
                    val label = listOfNotNull(name, choice, if (nulls) "nulls" else "values").joinToString(" ")
                    DynamicTest.dynamicTest(label) {
                        assertRoundTrip(serializer, SampleBuilder.build(shape, nulls, choice))
                    }
                }
            }
        }

    @Test
    fun `examples in the document, if any, parse with their DTO`() {
        val examples = dtos.flatMap { (name, serializer) ->
            val schema = spec.schemas.getValue(name)
            val listed = schema["examples"].asList() + listOfNotNull(schema["example"])
            listed.map { name to (serializer to it) }
        }
        examples.forEach { (name, pair) ->
            val (serializer, example) = pair
            val json = yamlToJson(example)
            runCatching { PetkitJson.decodeFromJsonElement(serializer, json) }
                .onFailure { throw AssertionError("$name example does not parse: $json", it) }
        }
    }

    @Test
    fun `unknown fields are ignored, as adding one is a compatible change`() {
        val sample = SampleBuilder.build(schemaShape("DeviceSummary"), nulls = false) as JsonObject
        val extended = JsonObject(sample + ("added_in_a_later_server" to JsonPrimitive(1)))
        PetkitJson.decodeFromJsonElement(DeviceSummaryDto.serializer(), extended)
    }

    @Test
    fun `known enum values are exactly the schema's`() {
        fun enumOf(schema: String, property: String? = null): Set<String> {
            var node = spec.schemas.getValue(schema)
            if (property != null) node = spec.resolve(node.map("properties").map(property))
            return node["enum"].asList().filterIsInstance<String>().toSet()
        }
        val timelineKind = spec.operation("GET", "/timeline")!!.parameters.single { it.name == "kind" }
            .schema!!["enum"].asList().filterIsInstance<String>().toSet()

        assertEquals(enumOf("Delivered"), Delivered.KNOWN)
        assertEquals(enumOf("NotificationKind"), NotificationKind.KNOWN)
        assertEquals(enumOf("PushMessage", "kind"), NotificationKind.KNOWN)
        assertEquals(enumOf("DeviceSummary", "connection"), DeviceConnection.KNOWN)
        assertEquals(enumOf("AccessoryRef", "kind"), AccessoryKind.KNOWN)
        assertEquals(enumOf("DeviceSummary", "kind"), DeviceKind.KNOWN)
        assertEquals(enumOf("DeviceSummary", "transport"), DeviceTransport.KNOWN)
        assertEquals(enumOf("Entity", "component"), EntityComponent.KNOWN)
        assertEquals(enumOf("Entity", "category"), EntityCategory.KNOWN)
        assertEquals(timelineKind, TimelineFilter.KNOWN)
    }

    private fun schemaShape(name: String): Shape = shapes.read(spec.schemas.getValue(name), name)

    private fun assertRoundTrip(serializer: KSerializer<*>, sample: JsonElement) {
        @Suppress("UNCHECKED_CAST")
        val typed = serializer as KSerializer<Any?>
        val decoded = runCatching { PetkitJson.decodeFromJsonElement(typed, sample) }
            .getOrElse { throw AssertionError("Does not parse: $sample", it) }
        val encoded = PetkitJson.encodeToJsonElement(typed, decoded)
        assertEquals(sample.withoutNulls(), encoded.withoutNulls()) { "Round trip changed the document" }
    }

    /** `explicitNulls = false` writes a null as an absent key, which the server reads the same. */
    private fun JsonElement.withoutNulls(): JsonElement = when (this) {
        is JsonObject -> JsonObject(filterValues { it != JsonNull }.mapValues { (_, v) -> v.withoutNulls() })
        is JsonArray -> JsonArray(map { it.withoutNulls() })
        else -> this
    }
}
