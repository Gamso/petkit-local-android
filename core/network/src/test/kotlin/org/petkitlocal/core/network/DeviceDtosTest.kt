package org.petkitlocal.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.petkitlocal.core.network.dto.DeviceDetailDto
import org.petkitlocal.core.network.dto.DeviceSummaryDto
import org.petkitlocal.core.network.dto.UpdateMessageDto

/**
 * A device's full name and generation (petkit-local#107) and whether it can be talked through
 * (petkit-local#108): read when the server sends them, null when a server older than them leaves
 * them out, as a released server does.
 */
class DeviceDtosTest {
    private fun summary(extra: String) =
        """{"id":1002,"type":"t4","model":"Pura Max",$extra"kind":"litter_box","online":true,""" +
            """"transport":"http","last_seen":null,"firmware":"1.652","is_camera":false,"connection":"wifi",""" +
            """"parent_id":null,"accessories":[]}"""

    @Test
    fun `a summary carries the full name and the generation`() {
        val dto = PetkitJson.decodeFromString(
            DeviceSummaryDto.serializer(),
            summary(""""model_name":"Pura Max 2","generation":2,"""),
        )
        assertEquals("Pura Max", dto.model)
        assertEquals("Pura Max 2", dto.modelName)
        assertEquals(2, dto.generation)
    }

    @Test
    fun `an unknown generation is null, and the name is the codename's`() {
        val dto = PetkitJson.decodeFromString(
            DeviceSummaryDto.serializer(),
            summary(""""model_name":"Pura Max","generation":null,"""),
        )
        assertEquals("Pura Max", dto.modelName)
        assertNull(dto.generation)
    }

    @Test
    fun `a server that predates them sends neither, and the summary still reads`() {
        val dto = PetkitJson.decodeFromString(DeviceSummaryDto.serializer(), summary(""))
        assertEquals("Pura Max", dto.model)
        assertNull(dto.modelName)
        assertNull(dto.generation)
    }

    @Test
    fun `a detail and a socket's device read them too, or their absence`() {
        val detail = summary(""""model_name":"YumShare Dual-Hopper 2","generation":2,""")
            .replace("\"t4\"", "\"d4sh\"")
            .dropLast(1) + ""","serial":null,"entities":[],"actions":[]}"""
        val dto = PetkitJson.decodeFromString(DeviceDetailDto.serializer(), detail)
        assertEquals("YumShare Dual-Hopper 2", dto.modelName)
        assertEquals(2, dto.generation)

        val message = decodeUpdateMessage("""{"type":"device","device":${summary("")}}""")
        val device = (message as UpdateMessageDto.Device).device
        assertNull(device.modelName)
        assertNull(device.generation)
    }

    @Test
    fun `both are written back as the server sent them`() {
        val dto = PetkitJson.decodeFromString(
            DeviceSummaryDto.serializer(),
            summary(""""model_name":"Pura Max 2","generation":2,"""),
        )
        val json = PetkitJson.encodeToString(DeviceSummaryDto.serializer(), dto)
        assertEquals(dto, PetkitJson.decodeFromString(DeviceSummaryDto.serializer(), json))
        assertTrue(""""model_name":"Pura Max 2","generation":2""" in json) { json }
    }

    @Test
    fun `a summary and a detail say whether the camera can be talked through`() {
        val dto = PetkitJson.decodeFromString(
            DeviceSummaryDto.serializer(),
            summary(""""talk":"needs_patcher",""").replace("\"is_camera\":false", "\"is_camera\":true"),
        )
        assertEquals("needs_patcher", dto.talk)

        val detail = summary(""""talk":"available",""").dropLast(1) + ""","serial":null,"entities":[],"actions":[]}"""
        assertEquals("available", PetkitJson.decodeFromString(DeviceDetailDto.serializer(), detail).talk)
    }

    @Test
    fun `a server that predates talk sends none, and the summary still reads`() {
        assertNull(PetkitJson.decodeFromString(DeviceSummaryDto.serializer(), summary("")).talk)
        val message = decodeUpdateMessage("""{"type":"device","device":${summary("")}}""")
        assertNull((message as UpdateMessageDto.Device).device.talk)
    }
}
