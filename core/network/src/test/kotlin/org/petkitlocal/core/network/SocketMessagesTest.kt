package org.petkitlocal.core.network

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.petkitlocal.core.network.dto.ClientUpdateMessageDto
import org.petkitlocal.core.network.dto.LiveVideoMessageDto
import org.petkitlocal.core.network.dto.UpdateMessageDto

/** WebSocket frames, as the server documents them in prose (api/openapi.yaml, `/ws` and `/live`). */
class SocketMessagesTest {
    @Test
    fun `a state message decodes by its type`() {
        val message = decodeUpdateMessage(
            """{"type":"state","device_id":4,"ts":1731600000.5,""" +
                """"entities":[{"key":"litter_level","value":42,"display":null}]}""",
        )
        val state = message as UpdateMessageDto.State
        assertEquals(4L, state.deviceId)
        assertEquals("litter_level", state.entities.single().key)
    }

    @Test
    fun `pong decodes`() {
        assertEquals(UpdateMessageDto.Pong, decodeUpdateMessage("""{"type":"pong"}"""))
    }

    @Test
    fun `a message type added by a later server is skipped, not fatal`() {
        assertNull(decodeUpdateMessage("""{"type":"something_new","x":1}"""))
        assertNull(decodeUpdateMessage("not json"))
    }

    @Test
    fun `the app's ping is the documented one`() {
        val ping: ClientUpdateMessageDto = ClientUpdateMessageDto.Ping
        assertEquals("""{"type":"ping"}""", PetkitJson.encodeToString(ClientUpdateMessageDto.serializer(), ping))
    }

    @Test
    fun `the live-video codec request is the documented shape`() {
        val request: LiveVideoMessageDto = LiveVideoMessageDto.Mse("avc1.640029,mp4a.40.2")
        assertEquals(
            """{"type":"mse","value":"avc1.640029,mp4a.40.2"}""",
            PetkitJson.encodeToString(LiveVideoMessageDto.serializer(), request),
        )
    }

    @Test
    fun `live-video server messages decode`() {
        assertEquals(
            LiveVideoMessageDto.Mse("video/mp4; codecs=\"avc1.42001F\""),
            decodeLiveVideoMessage("""{"type":"mse","value":"video/mp4; codecs=\"avc1.42001F\""}"""),
        )
        assertEquals(
            LiveVideoMessageDto.Error("camera busy"),
            decodeLiveVideoMessage("""{"type":"error","value":"camera busy"}"""),
        )
    }
}
