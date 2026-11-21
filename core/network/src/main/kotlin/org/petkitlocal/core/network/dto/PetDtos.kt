package org.petkitlocal.core.network.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

/** `Pet`. Weights are grams. */
@Serializable
data class PetDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("device_ids") val deviceIds: List<Long>,
    /** The weight the owner typed. */
    @SerialName("entered_weight_g") val enteredWeightG: Double?,
    /** What a visit is compared with: the learned weight once trusted. */
    @SerialName("weight_g") val weightG: Double?,
    @SerialName("weight_learned") val weightLearned: Boolean,
    /** `/app/v1/pets/{id}/photo`, if there is one. */
    @SerialName("photo") val photo: String?,
    /**
     * Its reference face photos, oldest first; [photo] is the first. Required by the contract, but
     * a server older than petkit-local#131 does not send it: null then.
     */
    @SerialName("faces") val faces: List<PetFaceDto>?,
)

/** `PetStats`. */
@Serializable
data class PetStatsDto(
    @SerialName("median_weight_g") val medianWeightG: Double?,
    @SerialName("visits_today") val visitsToday: Int,
    @SerialName("last_visit_ts") val lastVisitTs: Double?,
    @SerialName("last_visit_weight_g") val lastVisitWeightG: Double?,
    @SerialName("last_visit_duration_sec") val lastVisitDurationSec: Double?,
    @SerialName("last_device_id") val lastDeviceId: Long?,
)

/** `PetDetail`: `Pet` plus its statistics and recent visits. */
@Serializable
data class PetDetailDto(
    @SerialName("id") val id: Long,
    @SerialName("name") val name: String,
    @SerialName("device_ids") val deviceIds: List<Long>,
    @SerialName("entered_weight_g") val enteredWeightG: Double?,
    @SerialName("weight_g") val weightG: Double?,
    @SerialName("weight_learned") val weightLearned: Boolean,
    @SerialName("photo") val photo: String?,
    /** As in [PetDto.faces]: null from a server that predates it. */
    @SerialName("faces") val faces: List<PetFaceDto>?,
    @SerialName("stats") val stats: PetStatsDto,
    /** Up to 20 visits from the last 7 days, newest first. */
    @SerialName("recent_visits") val recentVisits: List<TimelineItemDto>,
)

/** Body of `GET /pets`. */
@Serializable
data class PetListResponseDto(@SerialName("pets") val pets: List<PetDto>)

/** Body of `GET /pets/{id}`. */
@Serializable
data class PetResponseDto(@SerialName("pet") val pet: PetDetailDto)

/** `PetFace`: one reference face photo of a pet. */
@Serializable
data class PetFaceDto(
    /** Unique across every pet. */
    @SerialName("id") val id: Long,
    /** `/app/v1/pets/{id}/faces/{face_id}/photo`, the stored 224x224 JPEG. */
    @SerialName("photo") val photo: String,
    /**
     * The pet's devices that report holding it. Required by the contract; null from a server
     * older than petkit-local#132, which does not say.
     */
    @SerialName("accepted_by") val acceptedBy: List<Long>?,
    /** The pet's devices that recognise pets and do not hold it yet; null as [acceptedBy]. */
    @SerialName("waiting_for") val waitingFor: List<Long>?,
)

/** `PetFaceList`: body of `GET /pets/{id}/faces`. */
@Serializable
data class PetFaceListDto(
    @SerialName("faces") val faces: List<PetFaceDto>,
    /** How many photos one pet may have. */
    @SerialName("max_faces") val maxFaces: Int,
)

/**
 * Body of `POST /pets/{id}/faces` (201). [quality] is required by the contract; null from a
 * server older than petkit-local#132, which does not check photos.
 */
@Serializable
data class PetFaceResultDto(
    @SerialName("face") val face: PetFaceDto,
    @SerialName("quality") val quality: PhotoQualityDto?,
)

/** `PhotoQuality`: what the server made of an uploaded photo; warnings never block. */
@Serializable
data class PhotoQualityDto(
    /** Whether the checks that need the image decoded ran (blur, exposure). */
    @SerialName("checked") val checked: Boolean,
    /** Stable codes, [PetFaceWarning.KNOWN]; the app ignores one it does not know. */
    @SerialName("warnings") val warnings: List<String>,
)

/** The codes of [PhotoQualityDto.warnings]. */
object PetFaceWarning {
    const val BLURRY = "blurry"
    const val TOO_DARK = "too_dark"
    const val OVEREXPOSED = "overexposed"
    const val NOT_SQUARE = "not_square"
    val KNOWN = setOf(BLURRY, TOO_DARK, OVEREXPOSED, NOT_SQUARE)
}

/** `PetCreateRequest`. Weights are grams; [deviceIds] null sends none. */
@Serializable
data class PetCreateRequestDto(
    @SerialName("name") val name: String,
    @SerialName("weight_g") val weightG: Double? = null,
    @SerialName("device_ids") val deviceIds: List<Long>? = null,
)

/**
 * `PetUpdateRequest`: only the fields sent change, and a null field is not sent
 * ([org.petkitlocal.core.network.PetkitJson] drops nulls). So [clearWeight] is how to send the
 * explicit `weight_g: null` that clears the weight; it wins over [weightG].
 */
@Serializable(with = PetUpdateRequestSerializer::class)
data class PetUpdateRequestDto(
    val name: String? = null,
    val weightG: Double? = null,
    val deviceIds: List<Long>? = null,
    val resetLearnedWeight: Boolean? = null,
    val clearWeight: Boolean = false,
)

/** The fields of `PetUpdateRequest` as the document names them: the shape of [PetUpdateRequestDto]. */
@Serializable
private data class PetUpdateRequestFields(
    @SerialName("name") val name: String? = null,
    @SerialName("weight_g") val weightG: Double? = null,
    @SerialName("device_ids") val deviceIds: List<Long>? = null,
    @SerialName("reset_learned_weight") val resetLearnedWeight: Boolean? = null,
)

/** Writes [PetUpdateRequestDto.clearWeight] as `weight_g: null`, the one null this API reads. */
internal object PetUpdateRequestSerializer : KSerializer<PetUpdateRequestDto> {
    private const val WEIGHT = "weight_g"
    private val fields = PetUpdateRequestFields.serializer()

    override val descriptor: SerialDescriptor = fields.descriptor

    override fun serialize(encoder: Encoder, value: PetUpdateRequestDto) {
        val plain = PetUpdateRequestFields(value.name, value.weightG, value.deviceIds, value.resetLearnedWeight)
        val json = encoder as? JsonEncoder
        if (json == null || !value.clearWeight) {
            encoder.encodeSerializableValue(fields, plain)
            return
        }
        val tree = json.json.encodeToJsonElement(fields, plain.copy(weightG = null)).jsonObject
        json.encodeJsonElement(JsonObject(tree + (WEIGHT to JsonNull)))
    }

    override fun deserialize(decoder: Decoder): PetUpdateRequestDto {
        val json = decoder as? JsonDecoder
        if (json == null) {
            val plain = decoder.decodeSerializableValue(fields)
            return PetUpdateRequestDto(plain.name, plain.weightG, plain.deviceIds, plain.resetLearnedWeight)
        }
        val tree = json.decodeJsonElement().jsonObject
        val plain = json.json.decodeFromJsonElement(fields, tree)
        return PetUpdateRequestDto(
            plain.name,
            plain.weightG,
            plain.deviceIds,
            plain.resetLearnedWeight,
            clearWeight = tree[WEIGHT] is JsonNull,
        )
    }
}

/** Values of `PetsSourceValue`: whose pets the devices recognise while proxy mode is on. */
object PetsSourceValue {
    /** PetKit's app's pets, relayed from the cloud. */
    const val PETKIT = "petkit"

    /** petkit-local's own, the ones `/pets` lists. */
    const val LOCAL = "local"
    val KNOWN = setOf(PETKIT, LOCAL)
}

/** `PetsSource`: body of `GET` and `PATCH /settings/pets-source`. */
@Serializable
data class PetsSourceDto(
    /** One of [PetsSourceValue.KNOWN]. */
    @SerialName("pets_source") val petsSource: String,
    /** Whether proxy mode is on (set in the panel only). */
    @SerialName("proxy_mode") val proxyMode: Boolean,
    /** Whose pets devices get now: [petsSource] in proxy mode, `local` otherwise. */
    @SerialName("effective") val effective: String,
    /** Every value [petsSource] takes, in the order to offer them. */
    @SerialName("options") val options: List<String>,
)

/** `PetsSourceRequest`. */
@Serializable
data class PetsSourceRequestDto(@SerialName("pets_source") val petsSource: String)

/** Body of `POST /pets` (201) and `PATCH /pets/{id}`. */
@Serializable
data class PetWriteResponseDto(@SerialName("pet") val pet: PetDto)
