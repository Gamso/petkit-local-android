package org.petkitlocal.core.domain.auth

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class ServerAddressesTest {
    private fun valid(input: String): ServerAddress = when (val check = ServerAddresses.normalise(input)) {
        is AddressCheck.Valid -> check.address
        is AddressCheck.Invalid -> error("$input: ${check.problem}")
    }

    private fun problem(input: String): AddressProblem? =
        (ServerAddresses.normalise(input) as? AddressCheck.Invalid)?.problem

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(
        "pets.example.com, https://pets.example.com",
        "  https://Pets.Example.COM/  , https://pets.example.com",
        "HTTPS://pets.example.com:443, https://pets.example.com",
        "https://pets.example.com:8443, https://pets.example.com:8443",
        "https://example.com/petkit/, https://example.com/petkit",
        "https://example.com/petkit/app/v1/, https://example.com/petkit",
        "https://example.com/app/v1/devices?x=1#top, https://example.com",
        "https://example.com/app/v10, https://example.com/app/v10",
        "192.168.1.20:8097, http://192.168.1.20:8097",
        "http://10.0.2.2:8097, http://10.0.2.2:8097",
        "https://192.168.1.20, https://192.168.1.20",
        "homeassistant.local:8097, http://homeassistant.local:8097",
        "http://[fd00::12]:8097, http://[fd00::12]:8097",
        "http://localhost:80, http://localhost",
    )
    fun `normalises what the user typed`(input: String, expected: String) {
        assertEquals(expected, valid(input).baseUrl)
    }

    @Test
    fun `the API base is the root plus app v1`() {
        assertEquals("https://example.com/petkit/app/v1/", valid("example.com/petkit").apiBaseUrl)
    }

    @Test
    fun `plain HTTP on the LAN is flagged so the screen can warn`() {
        assertTrue(valid("http://192.168.1.20:8097").plainHttpOnLan)
        assertFalse(valid("https://192.168.1.20").plainHttpOnLan)
        assertFalse(valid("pets.example.com").plainHttpOnLan)
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "http://pets.example.com",
            "http://8.8.8.8",
            "http://172.32.0.1",
            "http://100.64.0.1",
            "http://[2001:db8::1]",
            "http://192.168.1.20.nip.io",
        ],
    )
    fun `plain HTTP off the LAN is refused`(input: String) {
        assertEquals(AddressProblem.HttpsRequired, problem(input))
    }

    @Test
    fun `typed nonsense is refused with the reason`() {
        assertEquals(AddressProblem.Empty, problem("   "))
        assertEquals(AddressProblem.Malformed, problem("pets example.com"))
        assertEquals(AddressProblem.Malformed, problem("https://"))
        assertEquals(AddressProblem.Malformed, problem("https://user:pw@example.com"))
        assertEquals(AddressProblem.Malformed, problem("https://example.com:99999"))
        assertEquals(AddressProblem.UnsupportedScheme, problem("ftp://example.com"))
        assertEquals(AddressProblem.UnsupportedScheme, problem("ws://example.com"))
    }

    @ParameterizedTest
    @ValueSource(
        strings = [
            "10.1.2.3", "172.16.0.1", "172.31.255.255", "192.168.0.1", "127.0.0.1", "169.254.1.1",
            "[::1]", "[fe80::1]", "[fc00::1]", "localhost", "nas", "box.lan", "pets.home.arpa", "router.internal",
        ],
    )
    fun `private LAN hosts`(host: String) {
        assertTrue(ServerAddresses.isPrivateLanHost(host))
    }

    @ParameterizedTest
    @ValueSource(strings = ["8.8.8.8", "172.15.0.1", "192.169.0.1", "300.1.1.1", "[2001:db8::1]", "example.com"])
    fun `public hosts`(host: String) {
        assertFalse(ServerAddresses.isPrivateLanHost(host))
    }
}
