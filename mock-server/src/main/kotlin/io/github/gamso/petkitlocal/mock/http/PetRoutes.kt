package io.github.gamso.petkitlocal.mock.http

import io.github.gamso.petkitlocal.mock.api.ApiException
import io.github.gamso.petkitlocal.mock.api.Ok
import io.github.gamso.petkitlocal.mock.api.PetDetail
import io.github.gamso.petkitlocal.mock.api.PetDto
import io.github.gamso.petkitlocal.mock.api.PetFaceList
import io.github.gamso.petkitlocal.mock.api.PetFaceResult
import io.github.gamso.petkitlocal.mock.api.PetsSource
import io.github.gamso.petkitlocal.mock.media.MediaFactory
import io.github.gamso.petkitlocal.mock.state.Pet
import io.github.gamso.petkitlocal.mock.state.PetFace
import io.github.gamso.petkitlocal.mock.state.World
import io.github.gamso.petkitlocal.mock.state.createPet
import io.github.gamso.petkitlocal.mock.state.deleteFace
import io.github.gamso.petkitlocal.mock.state.deletePet
import io.github.gamso.petkitlocal.mock.state.jpegSize
import io.github.gamso.petkitlocal.mock.state.petsSourceDto
import io.github.gamso.petkitlocal.mock.state.photoQuality
import io.github.gamso.petkitlocal.mock.state.recognisers
import io.github.gamso.petkitlocal.mock.state.setPetsSource
import io.github.gamso.petkitlocal.mock.state.updatePet
import io.github.gamso.petkitlocal.mock.state.uploadFace
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respondBytes
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.patch
import io.ktor.server.routing.post
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** A face photo's upload limit (`POST /pets/{id}/faces`). */
private const val MAX_FACE_BYTES = 16L * 1024 * 1024

/** The size a stored face photo is cropped and scaled to. */
private const val FACE_SIZE = 224

private fun World.petOr404(call: ApplicationCall): Pet =
    synchronized(lock) { pets[call.pathInt("id", "pet")] } ?: throw ApiException(404, "pet_not_found", "no such pet")

private fun Pet.faceOr404(call: ApplicationCall): PetFace {
    val faceId = call.pathInt("face_id", "face")
    return faces.firstOrNull { it.id == faceId } ?: throw ApiException(404, "face_not_found", "this pet has no such face photo")
}

private fun isJpeg(bytes: ByteArray) =
    bytes.size >= 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

/** The faces of [pet] as the API lists them, oldest first. */
private fun World.faceList(pet: Pet): PetFaceList {
    val recognisers = recognisers()
    return PetFaceList(faces = pet.faces.map { it.dto(pet, recognisers) }, maxFaces = Pet.MAX_FACES)
}

private val petBody = MapSerializer(String.serializer(), PetDto.serializer())

/**
 * `/pets` and its faces, and `/settings/pets-source` (`app_api/handlers/pets.py`). Every write is
 * sent to every `/ws` socket ([io.github.gamso.petkitlocal.mock.state.publishPet]).
 */
fun Route.petRoutes(world: World) {
    get("/pets") {
        val pets = synchronized(world.lock) { world.pets.values.map { it.dto(world.recognisers()) } }
        call.respondJson(MapSerializer(String.serializer(), ListSerializer(PetDto.serializer())), mapOf("pets" to pets))
    }
    post("/pets") {
        val body = call.jsonBody()
        val pet = synchronized(world.lock) { world.createPet(body).dto(world.recognisers()) }
        call.respondJson(petBody, mapOf("pet" to pet), HttpStatusCode.Created)
    }
    get("/pets/{id}") {
        val pet = world.petOr404(call)
        val detail = synchronized(world.lock) { world.petDetail(pet) }
        call.respondJson(MapSerializer(String.serializer(), PetDetail.serializer()), mapOf("pet" to detail))
    }
    patch("/pets/{id}") {
        val pet = world.petOr404(call)
        val body = call.jsonBody()
        val dto =
            synchronized(world.lock) {
                if (world.pets[pet.id] !== pet) throw ApiException(404, "pet_not_found", "no such pet")
                world.updatePet(pet, body).dto(world.recognisers())
            }
        call.respondJson(petBody, mapOf("pet" to dto))
    }
    delete("/pets/{id}") {
        val pet = world.petOr404(call)
        synchronized(world.lock) { world.deletePet(pet) }
        call.respondJson(Ok.serializer(), Ok())
    }
    get("/pets/{id}/photo") {
        val pet = world.petOr404(call)
        val face =
            synchronized(world.lock) { pet.faces.firstOrNull() }
                ?: throw ApiException(404, "photo_not_found", "this pet has no photo")
        call.respondBytes(MediaFactory.jpeg(FACE_SIZE, FACE_SIZE, face.seed), ContentType.Image.JPEG)
    }
    faceRoutes(world)
    get("/settings/pets-source") {
        call.respondJson(PetsSource.serializer(), synchronized(world.lock) { world.petsSourceDto() })
    }
    patch("/settings/pets-source") {
        val body = call.jsonBody()
        call.respondJson(PetsSource.serializer(), synchronized(world.lock) { world.setPetsSource(body) })
    }
}

private fun Route.faceRoutes(world: World) {
    get("/pets/{id}/faces") {
        val pet = world.petOr404(call)
        call.respondJson(PetFaceList.serializer(), synchronized(world.lock) { world.faceList(pet) })
    }
    post("/pets/{id}/faces") {
        val pet = world.petOr404(call)
        val declared = call.request.headers[HttpHeaders.ContentLength]?.toLongOrNull()
        val tooLarge = ApiException(413, "photo_too_large", "a face photo is at most 16 MiB")
        if (declared != null && declared > MAX_FACE_BYTES) throw tooLarge
        val bytes = call.receive<ByteArray>()
        if (bytes.size > MAX_FACE_BYTES) throw tooLarge
        if (bytes.isEmpty()) throw ApiException(400, "bad_request", "send the JPEG itself as the body")
        if (!isJpeg(bytes)) throw ApiException(415, "not_jpeg", "the body is not a JPEG; send the photo's raw bytes as image/jpeg")
        // The mock does not crop what it was sent: it stores a fresh 224x224 placeholder.
        val size = jpegSize(bytes)
        val face =
            synchronized(world.lock) {
                if (world.pets[pet.id] !== pet) throw ApiException(404, "pet_not_found", "no such pet")
                world.uploadFace(pet, size).dto(pet, world.recognisers())
            }
        call.respondJson(PetFaceResult.serializer(), PetFaceResult(face, photoQuality(size)), HttpStatusCode.Created)
    }
    delete("/pets/{id}/faces/{face_id}") {
        val pet = world.petOr404(call)
        val faceId = call.pathInt("face_id", "face")
        synchronized(world.lock) { world.deleteFace(pet, faceId) }
        call.respondJson(Ok.serializer(), Ok())
    }
    get("/pets/{id}/faces/{face_id}/photo") {
        val pet = world.petOr404(call)
        val face = synchronized(world.lock) { pet.faceOr404(call) }
        call.respondBytes(MediaFactory.jpeg(FACE_SIZE, FACE_SIZE, face.seed), ContentType.Image.JPEG)
    }
}
