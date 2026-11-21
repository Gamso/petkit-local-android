package io.github.gamso.petkitlocal.mock.state

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.PetsSource
import io.github.gamso.petkitlocal.mock.api.PhotoQuality
import io.github.gamso.petkitlocal.mock.api.UpdatePet
import io.github.gamso.petkitlocal.mock.api.UpdatePetRemoved
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull

/*
 * Pets written from the app, with petkit-local's rules (`app_api/handlers/pets.py`): a field of
 * the wrong type or out of range is 400 `invalid_pet` and changes nothing; every write is sent
 * to every `/ws` socket as a `pet` message, a delete as `pet_removed`. Callers hold the lock.
 */

/** The heaviest weight a pet may be given, in grams (`PetCreateRequest.weight_g`). */
const val MAX_PET_WEIGHT_G = 100_000.0

private fun invalid(message: String) = ApiException(400, "invalid_pet", message)

private fun parseName(value: JsonElement?): String {
    val name = (value as? JsonPrimitive)?.takeIf { it.isString }?.content?.trim()
    if (name.isNullOrEmpty()) throw invalid("name must be a non-empty string")
    return name
}

/** Null clears it; a boolean is not a number. */
private fun parseWeight(value: JsonElement): Double? {
    if (value is JsonNull) return null
    // Out of range includes an overflow to infinity ("1e999").
    return (value as? JsonPrimitive)
        ?.takeUnless { it.isString || it.booleanOrNull != null }
        ?.doubleOrNull
        ?.takeIf { it > 0.0 && it <= MAX_PET_WEIGHT_G }
        ?: throw invalid("weight_g must be null or a number of grams above 0 and at most 100000")
}

/** A list of integers, duplicates dropped, order kept. */
private fun parseDeviceIds(value: JsonElement): List<Int> {
    val list = value as? JsonArray ?: throw invalid("device_ids must be a list of device ids")
    return list.map { it.asStrictInt() ?: throw invalid("device_ids must be a list of integers") }.distinct()
}

private fun parseReset(value: JsonElement): Boolean =
    (value as? JsonPrimitive)?.takeUnless { it.isString }?.booleanOrNull
        ?: throw invalid("reset_learned_weight must be a boolean")

/** Tells every `/ws` socket what [pet] is now. */
fun World.publishPet(pet: Pet) {
    hub.broadcast(encode(UpdatePet.serializer(), UpdatePet(pet = pet.dto(recognisers()))))
}

/** `POST /pets`: appended after every pet, with the next id. */
fun World.createPet(body: JsonObject): Pet {
    val name = parseName(body["name"])
    val weight = body["weight_g"]?.let(::parseWeight)
    val deviceIds = body["device_ids"]?.let(::parseDeviceIds) ?: emptyList()
    val pet = Pet(nextPetId++, name, deviceIds, enteredWeightG = weight, learnedWeightG = null)
    pets[pet.id] = pet
    publishPet(pet)
    return pet
}

/**
 * `PATCH /pets/{id}`: only the fields sent change. A weight that changes the entered one, or
 * `reset_learned_weight: true`, forgets the learned weight.
 */
fun World.updatePet(
    pet: Pet,
    body: JsonObject,
): Pet {
    val name = body["name"]?.let(::parseName)
    val weightSent = "weight_g" in body
    val weight = body["weight_g"]?.let(::parseWeight)
    val deviceIds = body["device_ids"]?.let(::parseDeviceIds)
    val reset = body["reset_learned_weight"]?.let(::parseReset) ?: false
    // `reset_learned_weight: false` alone names nothing.
    val namesAny = name != null || weightSent || deviceIds != null || reset
    if (!namesAny) {
        throw ApiException(400, "bad_request", "send at least one of name, weight_g, device_ids, reset_learned_weight")
    }
    name?.let { pet.name = it }
    deviceIds?.let { pet.deviceIds = it }
    if (weightSent && weight != pet.enteredWeightG) {
        pet.enteredWeightG = weight
        pet.learnedWeightG = null
    }
    if (reset) pet.learnedWeightG = null
    publishPet(pet)
    return pet
}

/** `DELETE /pets/{id}`: its faces go with it; its visits keep its id, with no name. */
fun World.deletePet(pet: Pet) {
    pets.remove(pet.id)
    pet.faces.clear()
    hub.broadcast(encode(UpdatePetRemoved.serializer(), UpdatePetRemoved(petId = pet.id)))
}

/** A new face for [pet], with the world's next face id: a generated 224x224 placeholder. */
fun World.addFace(pet: Pet): PetFace {
    val id = nextFaceId++
    return PetFace(id, seed = id * 5 + pet.id).also { pet.faces += it }
}

/** The devices that recognise pets by their face (`DeviceSummary.supports_ai`); never an accessory. */
fun World.recognisers(): Set<Int> =
    devices.values
        .filter { it.supportsAi && !it.isAccessory }
        .map { it.id }
        .toSet()

/**
 * `POST /pets/{id}/faces`: 422 `photo_too_small` under 224 px on the short side (read from the
 * JPEG header, as petkit-local does), then 409 `too_many_faces` once it has [Pet.MAX_FACES].
 */
fun World.uploadFace(
    pet: Pet,
    size: Pair<Int, Int>?,
): PetFace {
    if (size != null && minOf(size.first, size.second) < MIN_FACE_SIDE) {
        throw ApiException(422, "photo_too_small", "a face photo needs at least $MIN_FACE_SIDE px on its short side")
    }
    if (pet.faces.size >= Pet.MAX_FACES) {
        throw ApiException(409, "too_many_faces", "a pet has at most ${Pet.MAX_FACES} face photos; delete one first")
    }
    return addFace(pet).also { publishPet(pet) }
}

/** `DELETE /pets/{id}/faces/{face_id}`; a face of another pet is not found either. */
fun World.deleteFace(
    pet: Pet,
    faceId: Int,
) {
    val face = pet.faces.firstOrNull { it.id == faceId } ?: throw ApiException(404, "face_not_found", "this pet has no such face photo")
    pet.faces.remove(face)
    publishPet(pet)
}

/** `PetsSource`: [World.petsSource] applies only while proxy mode is on. */
fun World.petsSourceDto() =
    PetsSource(
        petsSource = petsSource,
        proxyMode = proxyMode,
        effective = if (proxyMode) petsSource else "local",
        options = World.PETS_SOURCES,
    )

/** `PATCH /settings/pets-source`. */
fun World.setPetsSource(body: JsonObject): PetsSource {
    val value = (body["pets_source"] as? JsonPrimitive)?.takeIf { it.isString }?.content
    if (value == null || value !in World.PETS_SOURCES) {
        throw ApiException(400, "bad_parameter", "pets_source must be one of: ${World.PETS_SOURCES.joinToString(", ")}")
    }
    petsSource = value
    return petsSourceDto()
}

/** The short side under which petkit-local refuses a face photo (`photo_too_small`). */
const val MIN_FACE_SIDE = 224

/** Over this ratio a photo is `not_square`: probably not cropped to the head. */
private const val SQUARE_RATIO = 1.6

/**
 * `PhotoQuality` for a photo of [size]: the mock decodes nothing, so `checked` is false and only
 * `not_square`, which the server reads from the JPEG header too, can be warned of.
 */
fun photoQuality(size: Pair<Int, Int>?): PhotoQuality {
    val notSquare = size != null && maxOf(size.first, size.second).toDouble() / minOf(size.first, size.second) > SQUARE_RATIO
    return PhotoQuality(checked = false, warnings = if (notSquare) listOf("not_square") else emptyList())
}

/** The width and height in a JPEG's start-of-frame header; null when it has none the mock can find. */
fun jpegSize(bytes: ByteArray): Pair<Int, Int>? {
    var i = 2
    while (i + 3 < bytes.size) {
        if (bytes[i] != 0xFF.toByte()) return null
        val marker = bytes[i + 1].toInt() and 0xFF
        val length = ((bytes[i + 2].toInt() and 0xFF) shl 8) or (bytes[i + 3].toInt() and 0xFF)
        val startOfFrame = marker in 0xC0..0xCF && marker != 0xC4 && marker != 0xC8 && marker != 0xCC
        if (startOfFrame && i + 8 < bytes.size) {
            val height = ((bytes[i + 5].toInt() and 0xFF) shl 8) or (bytes[i + 6].toInt() and 0xFF)
            val width = ((bytes[i + 7].toInt() and 0xFF) shl 8) or (bytes[i + 8].toInt() and 0xFF)
            return width to height
        }
        i += 2 + length
    }
    return null
}
