package io.github.gamso.petkitlocal.mock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** The oracle itself: it must reject what the spec does not allow, or the other tests prove nothing. */
class ContractOracleTest {
    private val summary =
        """{"id":1,"type":"t4","model":"Pura Max","model_name":"Pura Max","generation":null,""" +
            """"kind":"litter_box","online":true,"transport":"http",""" +
            """"last_seen":null,"firmware":null,"is_camera":false,"connection":"wifi","parent_id":null,"accessories":[]}"""

    private fun check(
        status: Int,
        body: String,
    ) = Contract.check("GET", "/devices", status, "application/json", { null }, body)

    @Test
    fun `a matching body passes`() {
        assertEquals(emptyList<String>(), check(200, """{"devices":[$summary]}"""))
    }

    @Test
    fun `a missing required key, a wrong type, an enum outside its values all fail`() {
        assertTrue(check(200, """{"devices":[${summary.replace(""""online":true,""", "")}]}""").isNotEmpty())
        assertTrue(check(200, """{"devices":[${summary.replace(""""id":1""", """"id":"1"""")}]}""").isNotEmpty())
        assertTrue(check(200, """{"devices":[${summary.replace("litter_box", "toilet")}]}""").isNotEmpty())
    }

    @Test
    fun `a key the spec does not name fails, nested ones too`() {
        val problems = check(200, """{"devices":[${summary.dropLast(1)},"name":"Kitchen"}]}""")
        assertEquals(listOf("GET /devices 200: key $.devices[0].name is not in the schema"), problems)
    }

    @Test
    fun `an undeclared status or content type fails`() {
        assertTrue(check(418, "{}").single().contains("not declared"))
        assertTrue(Contract.check("GET", "/devices", 200, "text/plain", { null }, "x").single().contains("content type"))
    }

    @Test
    fun `ws messages are told apart by type`() {
        assertEquals(emptyList<String>(), Contract.checkMessage("""{"type":"pong"}"""))
        assertTrue(Contract.checkMessage("""{"type":"state","device_id":1}""").isNotEmpty())
        assertTrue(Contract.checkMessage("""{"type":"device_removed","device_id":1,"extra":true}""").isNotEmpty())
    }
}
