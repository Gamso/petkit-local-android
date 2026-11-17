package org.petkitlocal.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.core.network.dto.DeviceKind
import org.petkitlocal.core.network.dto.LoginRequestDto

/**
 * End to end, on an emulator: the app's own HTTP stack (core:network: OkHttp, Retrofit, the
 * interceptors, the DTOs and their serialization, all as built into the app) reaches the server
 * on the host through 10.0.2.2, over plain HTTP, which the app accepts for a private LAN address.
 * Against the real server this is where a drift of its answers from the vendored contract shows
 * first: every read the app makes, parsed by the app's own DTOs.
 */
@RunWith(AndroidJUnit4::class)
class ServerApiTest {
    @get:Rule
    val server = TestServerRule()

    @Test
    fun infoIsPublic() = runBlocking {
        val info = TestServer.api().getInfo()

        assertEquals(1, info.apiVersion)
        assertFalse(info.authenticated)
        assertTrue(info.serverTime > 0)
    }

    @Test
    fun signInOpensASessionNamedAfterThePhone() = runBlocking {
        val login = TestServer.api().login(LoginRequestDto(TestServer.password, TestServer.DEVICE_NAME))
        assertEquals("Bearer", login.tokenType)
        assertTrue(login.token.isNotEmpty())

        val info = TestServer.api { login.token }.getInfo()

        assertTrue(info.authenticated)
        assertEquals(TestServer.DEVICE_NAME, info.session?.name)
        assertTrue(info.serverVersion.orEmpty().isNotEmpty())
    }

    /** Whatever the server has: each device's detail, the latest events, each pet, the notifications. */
    @Test
    fun everythingTheServerListsReadsThroughTheAppsClient() = runBlocking {
        val api = TestServer.signedInApi()

        val devices = api.listDevices().devices
        assertTrue("the server lists no device", devices.isNotEmpty())
        for (device in devices) {
            val detail = api.getDevice(device.id).device
            assertEquals(device.id, detail.id)
            assertTrue(detail.kind, detail.kind in DeviceKind.KNOWN)
        }

        // The latest page, as History's first, and each card's detail.
        api.getTimeline(limit = HISTORY_PAGE).items.forEach { api.getTimelineEvent(it.id) }

        api.listPets().pets.forEach { assertEquals(it.id, api.getPet(it.id).pet.id) }
        assertTrue(api.getNotificationSettings().serverKinds.isNotEmpty())
    }

    @Test
    @MockOnly("counts the mock's seeded household")
    fun theSeededHouseholdHasThirteenDevices() = runBlocking {
        val devices = TestServer.signedInApi().listDevices().devices

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
        const val HISTORY_PAGE = 25
    }
}
