package org.petkitlocal.core.data

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.Outcome
import org.petkitlocal.core.domain.getOrNull
import org.petkitlocal.core.domain.model.PetFace
import org.petkitlocal.core.domain.pets.FaceWarning
import org.petkitlocal.core.domain.pets.PetChanges
import org.petkitlocal.core.domain.pets.PetDraft
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.core.domain.pets.PetsSourceSetting
import org.robolectric.RobolectricTestRunner

/**
 * Integration: managing pets against a scripted server and a real Room database. Every write goes
 * to the server first, and the cache gets only what it answered (ADR 0002).
 */
@RunWith(RobolectricTestRunner::class)
class PetManagementRepositoryTest {
    @get:Rule
    val server = ScriptedServer()

    private val pets by lazy {
        PetRepositoryImpl(server.api, server.db.petDao(), Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)) { server.base }
    }

    @Test
    fun `a created pet is cached as the server answered, after the others`() = runTest {
        server.on("pets", """{"pets":[${pet(1, "Mochi")}]}""")
        pets.refresh()
        server.on("pets", """{"pet":${pet(3, "Nala", weight = "3900")}}""", code = 201)

        val created = pets.createPet(PetDraft("Nala", 3900.0, listOf(1003)))

        assertEquals(3L, created.getOrNull()?.id)
        assertEquals("""POST pets {"name":"Nala","weight_g":3900.0,"device_ids":[1003]}""", server.calls.last())
        assertEquals(listOf("Mochi", "Nala"), pets.observePets().first().map { it.name })
    }

    @Test
    fun `an edit sends only what changed, and clearing the weight is an explicit null`() = runTest {
        server.on("pets/1", """{"pet":${pet(1, "Moka", weight = "null")}}""")

        pets.updatePet(1, PetChanges(name = "Moka", clearWeight = true))

        assertEquals("""PATCH pets/1 {"name":"Moka","weight_g":null}""", server.calls.last())
        val cached = pets.observePets().first().single()
        assertEquals("Moka" to null, cached.name to cached.enteredWeightG)
    }

    @Test
    fun `a refused edit changes nothing in the cache`() = runTest {
        server.on("pets", """{"pets":[${pet(1, "Mochi")}]}""")
        pets.refresh()
        server.on("pets/1", """{"error":"invalid_pet","message":"name must be a non-empty string"}""", code = 400)

        val result = pets.updatePet(1, PetChanges(name = " "))

        assertEquals(
            DomainError.Validation(null, "invalid_pet", "name must be a non-empty string"),
            (result as Outcome.Failure).error,
        )
        assertEquals(listOf("Mochi"), pets.observePets().first().map { it.name })
    }

    @Test
    fun `a deleted pet leaves the cache, and so does one already gone`() = runTest {
        server.on("pets", """{"pets":[${pet(1, "Mochi")},${pet(2, "Tigrou")}]}""")
        pets.refresh()
        server.on("pets/1", """{"ok":true}""")
        server.on("pets/2", """{"error":"pet_not_found","message":"no such pet"}""", code = 404)

        assertEquals(Outcome.Success(Unit), pets.deletePet(1))
        assertTrue(pets.deletePet(2) is Outcome.Failure)

        assertEquals(listOf("DELETE pets/1", "DELETE pets/2"), server.calls.takeLast(2))
        assertEquals(emptyList<Any>(), pets.observePets().first())
    }

    @Test
    fun `faces are cached with the pet, and its photo is the first face's own address`() = runTest {
        server.on("pets", """{"pets":[${pet(1, "Mochi")}]}""")
        pets.refresh()
        server.on(
            "pets/1/faces",
            """{"faces":[${face(7)},${face(9, accepted = "[1003]", waiting = "[3001]")}],"max_faces":6}""",
        )

        val faces = pets.refreshFaces(1).getOrNull()!!

        assertEquals(6, faces.maxFaces)
        val cached = pets.observePets().first().single()
        assertEquals(listOf(7L, 9L), cached.faces.map { it.id })
        assertEquals(listOf(null, listOf(1003L)), cached.faces.map { it.acceptedBy })
        assertEquals(listOf(null, listOf(3001L)), cached.faces.map { it.waitingFor })
        assertEquals("${server.base}pets/1/faces/7/photo", cached.photoUrl)
    }

    @Test
    fun `an upload sends the JPEG itself, reads the photos again and translates the warnings it knows`() = runTest {
        server.on("pets", """{"pets":[${pet(1, "Mochi")}]}""")
        pets.refresh()
        server.on(
            "pets/1/faces",
            """{"face":${face(8)},"quality":{"checked":true,"warnings":["blurry","too_dark","a_new_one"]}}""",
            code = 201,
        )

        val upload = pets.addFace(1, byteArrayOf(-1, -40, -1, 0)).getOrNull()!!

        assertEquals(PetFace(8, "${server.base}pets/1/faces/8/photo", null), upload.face)
        assertEquals(listOf(FaceWarning.Blurry, FaceWarning.TooDark), upload.warnings)
        assertEquals(listOf("POST pets/1/faces <4 bytes image/jpeg>", "GET pets/1/faces"), server.calls.takeLast(2))
    }

    @Test
    fun `an upload from a server older than the photo checks has no warnings`() = runTest {
        server.on("pets/1/faces", """{"face":${face(8)}}""", code = 201)

        assertEquals(emptyList<FaceWarning>(), pets.addFace(1, byteArrayOf(1)).getOrNull()!!.warnings)
    }

    @Test
    fun `a pet at its limit is refused with the server's code`() = runTest {
        server.on("pets/1/faces", """{"error":"too_many_faces","message":"a pet has at most 6 photos"}""", code = 409)

        val error = (pets.addFace(1, byteArrayOf(1)) as Outcome.Failure).error

        assertEquals(DomainError.Server(409, "too_many_faces", "a pet has at most 6 photos"), error)
    }

    @Test
    fun `whose pets the devices recognise is read and set`() = runTest {
        server.on(
            "settings/pets-source",
            """{"pets_source":"local","proxy_mode":true,"effective":"local","options":["petkit","local"]}""",
        )

        val set = pets.setPetsSource(PetsSource.Local).getOrNull()

        assertEquals("""PATCH settings/pets-source {"pets_source":"local"}""", server.calls.last())
        assertEquals(
            PetsSourceSetting(PetsSource.Local, true, PetsSource.Local, listOf(PetsSource.PetKit, PetsSource.Local)),
            set,
        )
    }

    private fun pet(id: Long, name: String, weight: String = "4200") = """{"id":$id,"name":"$name",
        "device_ids":[1002],"entered_weight_g":$weight,"weight_g":$weight,"weight_learned":false,"photo":null,
        "faces":[]}"""

    /** A face; without [accepted], as a server older than petkit-local#132 lists it. */
    private fun face(id: Long, accepted: String? = null, waiting: String = "[]"): String {
        val status = accepted?.let { ""","accepted_by":$it,"waiting_for":$waiting""" }.orEmpty()
        return """{"id":$id,"photo":"/app/v1/pets/1/faces/$id/photo"$status}"""
    }
}
