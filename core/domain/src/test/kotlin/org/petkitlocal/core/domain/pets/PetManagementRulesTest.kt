package org.petkitlocal.core.domain.pets

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.domain.model.Device
import org.petkitlocal.core.domain.model.DeviceConnection
import org.petkitlocal.core.domain.model.DeviceFamily
import org.petkitlocal.core.domain.model.DeviceTransport
import org.petkitlocal.core.domain.model.Pet
import org.petkitlocal.core.domain.model.PetFace

class PetManagementRulesTest {
    private val litterBox = device(1002, supportsAi = false)
    private val camera = device(1003, supportsAi = true)
    private val fountain = device(3001, supportsAi = true)
    private val accessory = device(3002, supportsAi = true).copy(connection = DeviceConnection.Ble)
    private val devices = listOf(litterBox, camera, fountain, accessory)
    private val mochi = Pet(1, "Mochi", listOf(1002, 1003), 4200.0, 4333.0, weightLearned = true, photoUrl = null)

    @Test
    fun `only devices that recognise faces are offered, never an accessory`() {
        assertEquals(listOf(1003L, 3001L), PetManagementRules.recognitionDevices(devices).map { it.id })
    }

    @Test
    fun `a device the editor did not offer stays assigned`() {
        val offered = PetManagementRules.recognitionDevices(devices)

        assertEquals(listOf(1002L, 3001L), PetManagementRules.deviceIds(mochi, offered, picked = setOf(3001)))
        assertEquals(listOf(1003L), PetManagementRules.deviceIds(null, offered, picked = setOf(1003)))
    }

    @Test
    fun `an edit names only what changed`() {
        val same = PetDraft("Mochi", 4200.0, listOf(1003, 1002))
        assertTrue(PetManagementRules.changes(mochi, same).isEmpty)
        // A weight shown rounded to 10 g is no change.
        assertTrue(PetManagementRules.changes(mochi, same.copy(weightG = 4204.0)).isEmpty)

        assertEquals(PetChanges(name = "Moka"), PetManagementRules.changes(mochi, same.copy(name = " Moka ")))
        assertEquals(PetChanges(weightG = 4500.0), PetManagementRules.changes(mochi, same.copy(weightG = 4500.0)))
        assertEquals(PetChanges(clearWeight = true), PetManagementRules.changes(mochi, same.copy(weightG = null)))
        assertEquals(
            PetChanges(deviceIds = listOf(1002L)),
            PetManagementRules.changes(mochi, same.copy(deviceIds = listOf(1002))),
        )
    }

    @Test
    fun `a photo lists the devices that hold it, then those it waits for, when the server says`() {
        val face = PetFace(7, null, acceptedBy = listOf(3001), waitingFor = listOf(1003, 9999))

        val status = PetManagementRules.faceStatus(face, devices)!!

        // A device the cache does not know (9999) is left out.
        assertEquals(listOf(3001L to true, 1003L to false), status.map { it.device.id to it.accepted })
        assertNull(PetManagementRules.faceStatus(face.copy(acceptedBy = null), devices))
    }

    private fun device(id: Long, supportsAi: Boolean) = Device(
        id = id,
        type = "t5",
        modelName = "Device $id",
        family = DeviceFamily.LitterBox,
        online = true,
        transport = DeviceTransport.Mqtt,
        lastSeen = null,
        firmware = null,
        isCamera = supportsAi,
        supportsAi = supportsAi,
    )
}
