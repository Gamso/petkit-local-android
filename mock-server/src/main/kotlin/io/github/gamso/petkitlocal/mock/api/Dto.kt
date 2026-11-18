@file:UseSerializers(PlainDoubleSerializer::class)

package io.github.gamso.petkitlocal.mock.api

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

/**
 * Every JSON object `/app/v1` returns, one class per schema of `api/openapi.yaml` with the same
 * name. Nulls are written out (`explicitNulls`), as the real server writes them: the schema
 * requires each key, null or not.
 */
@OptIn(ExperimentalSerializationApi::class)
val MockJson =
    Json {
        encodeDefaults = true
        explicitNulls = true
        ignoreUnknownKeys = true
    }

@Serializable
data class ApiErrorBody(
    val error: String,
    val message: String,
)

@Serializable
data class Ok(
    val ok: Boolean = true,
)

@Serializable
data class SessionDto(
    val id: String,
    val name: String,
    val created: Double,
    @SerialName("last_used") val lastUsed: Double,
    val expires: Double,
    /** The kinds this session chose (`PUT /notifications`); null for every kind. */
    val kinds: List<String>? = null,
    // The mock has no UnifiedPush: no session ever registers an endpoint.
    val push: PushRegistration? = null,
)

@Serializable
data class PushRegistration(
    val host: String,
    val kinds: List<String>?,
    val registered: Double,
)

/** One entry of `NotificationSettings.server_kinds`: a kind and the panel's switch for it. */
@Serializable
data class ServerKind(
    val kind: String,
    val enabled: Boolean,
)

@Serializable
data class NotificationSettings(
    val kinds: List<String>?,
    @SerialName("server_kinds") val serverKinds: List<ServerKind>,
    val connected: Boolean,
    @SerialName("last_id") val lastId: Long,
)

@Serializable
data class Info(
    @SerialName("api_version") val apiVersion: Int = 1,
    @SerialName("server_time") val serverTime: Double,
    val authenticated: Boolean,
    @SerialName("server_version") val serverVersion: String?,
    val session: SessionDto?,
)

@Serializable
data class LoginResponse(
    val token: String,
    @SerialName("token_type") val tokenType: String = "Bearer",
    @SerialName("idle_timeout_sec") val idleTimeoutSec: Int,
    val session: SessionDto,
)

@Serializable
data class ChangePasswordResponse(
    val ok: Boolean = true,
    @SerialName("revoked_sessions") val revokedSessions: Int,
)

@Serializable
data class DeviceSummary(
    val id: Int,
    val type: String,
    val model: String,
    val kind: String,
    val online: Boolean,
    val transport: String,
    @SerialName("last_seen") val lastSeen: Double?,
    val firmware: String?,
    @SerialName("is_camera") val isCamera: Boolean,
    val connection: String,
    @SerialName("parent_id") val parentId: Int?,
    val accessories: List<AccessoryRef>,
)

@Serializable
data class AccessoryRef(
    val id: Int,
    val type: String,
    val kind: String,
)

@Serializable
data class DeviceDetail(
    val id: Int,
    val type: String,
    val model: String,
    val kind: String,
    val online: Boolean,
    val transport: String,
    @SerialName("last_seen") val lastSeen: Double?,
    val firmware: String?,
    @SerialName("is_camera") val isCamera: Boolean,
    val connection: String,
    @SerialName("parent_id") val parentId: Int?,
    val accessories: List<AccessoryRef>,
    val serial: String?,
    val entities: List<EntityDto>,
    val actions: List<ActionDto>,
)

@Serializable
data class EntityDto(
    val key: String,
    val name: String,
    val component: String,
    val value: JsonElement?,
    val display: String?,
    val unit: String?,
    @SerialName("device_class") val deviceClass: String?,
    val category: String?,
    val options: List<String>?,
    val settable: Boolean,
    val min: Double?,
    val max: Double?,
    val step: Double?,
) {
    fun change() = EntityChange(key, value, display)
}

@Serializable
data class EntityChange(
    val key: String,
    val value: JsonElement?,
    val display: String?,
)

@Serializable
data class ActionDto(
    val key: String,
    val name: String,
    val destructive: Boolean,
)

@Serializable
data class ActionResult(
    val ok: Boolean = true,
    val action: String,
    val delivered: String,
)

@Serializable
data class WriteResult(
    val ok: Boolean = true,
    val delivered: String,
)

@Serializable
data class EntityWriteResult(
    val ok: Boolean = true,
    val delivered: String,
    val entity: EntityDto,
)

@Serializable
data class MealDto(
    val id: String,
    val time: Int,
    val amount: Int?,
    val amount1: Int?,
    val amount2: Int?,
)

@Serializable
data class FeedingGroupDto(
    val days: List<Int>,
    val meals: List<MealDto>,
)

@Serializable
data class FeedingPlanDto(
    @SerialName("device_id") val deviceId: Int,
    val active: Boolean,
    @SerialName("dual_hopper") val dualHopper: Boolean,
    val groups: List<FeedingGroupDto>,
)

@Serializable
data class FeedingPlanResult(
    val ok: Boolean = true,
    val delivered: String,
    val schedule: FeedingPlanDto,
)

@Serializable
data class DeferredFeedDto(
    val id: String,
    @SerialName("fire_at") val fireAt: Double?,
    val amount: Int?,
    val amount1: Int?,
    val amount2: Int?,
)

@Serializable
data class DeferredFeedResult(
    val ok: Boolean = true,
    val delivered: String,
    val feed: DeferredFeedDto,
)

@Serializable
data class MediaDto(
    val thumbnail: String?,
    val snapshot: String?,
    val highlight: String?,
    val playback: String?,
    val timelapse: String?,
    val waste: List<String>,
    val health: List<String>,
    @SerialName("video_pending") val videoPending: Boolean,
)

@Serializable
data class PetRef(
    val id: Int,
    val name: String?,
)

@Serializable
data class TimelineStep(
    val id: Int,
    val ts: Double?,
    val label: String,
    val detail: Boolean,
)

@Serializable
data class TimelineItemDto(
    val id: Int,
    val kind: String,
    val filters: List<String>,
    @SerialName("device_id") val deviceId: Int?,
    @SerialName("device_type") val deviceType: String?,
    val ts: Double?,
    @SerialName("display_ts") val displayTs: Double?,
    @SerialName("event_type") val eventType: String?,
    @SerialName("event_kind") val eventKind: String?,
    val label: String?,
    val pet: PetRef?,
    @SerialName("duration_sec") val durationSec: Int?,
    @SerialName("weight_g") val weightG: Int?,
    val served: ServedAmounts?,
    val facts: List<String>,
    val media: MediaDto?,
    val steps: List<TimelineStep>,
)

/** What a finished feed put out: per hopper on a Dual-Hopper, the total alone on a single hopper. */
@Serializable
data class ServedAmounts(
    val total: Int,
    val hopper1: Int?,
    val hopper2: Int?,
)

@Serializable
data class TimelinePage(
    val items: List<TimelineItemDto>,
    @SerialName("next_cursor") val nextCursor: String?,
)

@Serializable
data class EventField(
    val key: String,
    val label: String,
    val text: String,
)

@Serializable
data class EventDetail(
    val id: Int,
    @SerialName("device_id") val deviceId: Int?,
    @SerialName("device_type") val deviceType: String?,
    val ts: Double?,
    @SerialName("event_type") val eventType: String?,
    @SerialName("event_kind") val eventKind: String?,
    val label: String,
    val pet: PetRef?,
    val facts: List<String>,
    val fields: List<EventField>,
    val media: MediaDto?,
)

@Serializable
data class PetDto(
    val id: Int,
    val name: String,
    @SerialName("device_ids") val deviceIds: List<Int>,
    @SerialName("entered_weight_g") val enteredWeightG: Double?,
    @SerialName("weight_g") val weightG: Double?,
    @SerialName("weight_learned") val weightLearned: Boolean,
    val photo: String?,
)

@Serializable
data class PetStats(
    @SerialName("median_weight_g") val medianWeightG: Double?,
    @SerialName("visits_today") val visitsToday: Int,
    @SerialName("last_visit_ts") val lastVisitTs: Double?,
    @SerialName("last_visit_weight_g") val lastVisitWeightG: Double?,
    @SerialName("last_visit_duration_sec") val lastVisitDurationSec: Double?,
    @SerialName("last_device_id") val lastDeviceId: Int?,
)

@Serializable
data class PetDetail(
    val id: Int,
    val name: String,
    @SerialName("device_ids") val deviceIds: List<Int>,
    @SerialName("entered_weight_g") val enteredWeightG: Double?,
    @SerialName("weight_g") val weightG: Double?,
    @SerialName("weight_learned") val weightLearned: Boolean,
    val photo: String?,
    val stats: PetStats,
    @SerialName("recent_visits") val recentVisits: List<TimelineItemDto>,
)

// --- /ws messages -------------------------------------------------------------------------------

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateHello(
    @EncodeDefault val type: String = "hello",
    @SerialName("server_time") val serverTime: Double,
    @SerialName("heartbeat_sec") val heartbeatSec: Double,
    val devices: List<DeviceDetail>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateState(
    @EncodeDefault val type: String = "state",
    @SerialName("device_id") val deviceId: Int,
    val ts: Double,
    val entities: List<EntityChange>,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateDevice(
    @EncodeDefault val type: String = "device",
    val device: DeviceSummary,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateDeviceRemoved(
    @EncodeDefault val type: String = "device_removed",
    @SerialName("device_id") val deviceId: Int,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class UpdateTimeline(
    @EncodeDefault val type: String = "timeline",
    val item: TimelineItemDto,
    val replaces: List<Int>,
)

// --- /notifications/ws messages -----------------------------------------------------------------

/** `PushMessage`: stable keys only, the same body a UnifiedPush endpoint would be sent. */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class PushMessage(
    @EncodeDefault val v: Int = 1,
    val kind: String,
    @SerialName("device_id") val deviceId: Int?,
    @SerialName("pet_id") val petId: Int?,
    val code: String?,
    val ts: Long,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationHello(
    @EncodeDefault val type: String = "hello",
    @SerialName("server_time") val serverTime: Double,
    @SerialName("heartbeat_sec") val heartbeatSec: Double,
    @SerialName("last_id") val lastId: Long,
    val replayed: Int,
    val gap: Boolean,
)

@OptIn(ExperimentalSerializationApi::class)
@Serializable
data class NotificationDelivery(
    @EncodeDefault val type: String = "notification",
    val id: Long,
    val replay: Boolean,
    val message: PushMessage,
)
