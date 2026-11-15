package io.github.gamso.petkitlocal.mock

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.ZoneId

class MockConfigTest {
    @Test
    fun `defaults match the real app API`() {
        val c = MockConfig.from(emptyArray(), emptyMap())
        assertEquals(8097, c.port)
        assertEquals("demo", c.password)
    }

    @Test
    fun `flags override environment variables`() {
        val c =
            MockConfig.from(
                arrayOf("--port=9000", "--password", "s3cret", "--control=false", "--timezone=America/New_York"),
                mapOf("PETKIT_MOCK_PORT" to "8100", "PETKIT_MOCK_SCENARIO_INTERVAL" to "0", "PETKIT_MOCK_LOCKOUT_BASE" to "2"),
            )
        assertEquals(9000, c.port)
        assertEquals("s3cret", c.password)
        assertFalse(c.controlEnabled)
        assertEquals(0.0, c.scenarioIntervalSec)
        assertEquals(2.0, c.lockoutBaseSec)
        assertEquals(ZoneId.of("America/New_York"), c.timezone)
    }

    @Test
    fun `unknown options are refused`() {
        assertThrows<IllegalArgumentException> { MockConfig.from(arrayOf("--prot=1"), emptyMap()) }
        assertThrows<IllegalArgumentException> { MockConfig.from(arrayOf("--busy=-1"), emptyMap()) }
    }
}
