package org.petkitlocal.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.MockServerRule
import org.petkitlocal.core.network.dto.DeviceKind
import org.petkitlocal.core.network.dto.LoginRequestDto

/**
 * End to end, on an emulator: the app's own HTTP stack (core:network: OkHttp, Retrofit, the
 * interceptors, the DTOs and their serialization, all as built into the app) reaches the mock
 * server on the host through 10.0.2.2, over plain HTTP, which the app accepts for a private LAN address.
 * Sign-in through the UI arrives with M1 and builds on this.
 */
@RunWith(AndroidJUnit4::class)
class MockServerApiTest {
    @get:Rule
    val mock = MockServerRule()

    @Test
    fun infoIsPublic() = runBlocking {
        val info = MockServer.api().getInfo()

        assertEquals(1, info.apiVersion)
        assertFalse(info.authenticated)
        assertTrue(info.serverTime > 0)
    }

    @Test
    fun signInOpensASessionNamedAfterThePhone() = runBlocking {
        val login = MockServer.api().login(LoginRequestDto(MockServer.PASSWORD, MockServer.DEVICE_NAME))
        assertEquals("Bearer", login.tokenType)
        assertTrue(login.token.isNotEmpty())

        val info = MockServer.api { login.token }.getInfo()

        assertTrue(info.authenticated)
        assertEquals(MockServer.DEVICE_NAME, info.session?.name)
    }

    @Test
    fun theSeededHouseholdHasThirteenDevices() = runBlocking {
        val devices = MockServer.signedInApi().listDevices().devices

        assertEquals(SEEDED_DEVICES, devices.size)
        assertEquals(
            setOf(DeviceKind.LITTER_BOX, DeviceKind.FEEDER, DeviceKind.FOUNTAIN, DeviceKind.PURIFIER),
            devices.map { it.kind }.toSet(),
        )
        assertEquals("Pura Max", devices.single { it.id == PURA_MAX }.model)
    }

    private companion object {
        /** mock-server/README.md, "The seeded household". */
        const val SEEDED_DEVICES = 13
        const val PURA_MAX = 1002L
    }
}
