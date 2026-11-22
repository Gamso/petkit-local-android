package org.petkitlocal.android

import android.Manifest
import android.os.Build
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dagger.hilt.android.EntryPointAccessors
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.drinksync.DrinkRecord
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.ActivityTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.fountain.FountainTestTags
import org.petkitlocal.feature.device.litter.PageTestTags
import org.petkitlocal.feature.home.HomeTestTags

/**
 * A CTW3's drink history synced over the phone's Bluetooth (issue #104), end to end against the
 * mock: the EverSweet Max 2's Sync drinks, always there, says why it cannot sync on an emulator
 * (no Bluetooth, Bluetooth off, or no fountain in range: whichever this emulator has); with a
 * scripted fountain in place of the radio, the card walks through its steps, the drinks reach the
 * server and the page's activity, a second sync finds nothing new, and a fountain whose secret the
 * server lacks says so with the server's words.
 */
@MockOnly("the mock's CTW3, its pairing secret and its control API")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class DrinkSyncFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val bluetooth: EndToEndBleTransport
        get() = EntryPointAccessors.fromApplication(
            instrumentation.targetContext.applicationContext,
            EndToEndEntryPoint::class.java,
        ).bleTransport()

    @Before
    fun allowBluetooth() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            listOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        permissions.forEach {
            instrumentation.uiAutomation.grantRuntimePermission(instrumentation.targetContext.packageName, it)
        }
    }

    @After
    fun backToTheRadio() {
        bluetooth.replacement = null
    }

    @Test
    fun theButtonSaysWhyTheEmulatorCannotSync() {
        bluetooth.replacement = null
        signIn()
        openSyncCard()
        status().assertDoesNotExist()
        button().performClick()

        // Whatever this emulator's Bluetooth is: none, off, or on with no fountain in range (a scan
        // gives up after PetKit's 30 s).
        val reasons = listOf(
            "This phone has no Bluetooth Low Energy, so it cannot sync the fountain.",
            "Bluetooth is off. Turn it on, then sync again.",
            "The fountain was not found. Bring the phone close to it and try again.",
        )
        compose.waitUntil(SCAN_TIMEOUT_MS) { statusTexts().any { it in reasons } }
        compose.onNodeWithTag(FountainTestTags.DRINK_SYNC_LAST).assertExists()
        // Still pressable: what keeps it from syncing is said, never a grey button.
        waitFor(hasTestTag(FountainTestTags.DRINK_SYNC_BUTTON) and isEnabled())
    }

    @Test
    fun aFountainNearbySyncsItsDrinksIntoTheServerAndThePage() {
        val now = System.currentTimeMillis() / MS_PER_S - CLOCK_EPOCH
        val fountain = ScriptedFountainTransport(SECRET, listOf(DrinkRecord(now - 600, 21), DrinkRecord(now - 300, 34)))
        bluetooth.replacement = fountain
        signIn()
        openSyncCard()
        waitFor(hasTestTag(FountainTestTags.DRINK_SYNC_LAST) and hasText("Never synced from this phone"))

        button().performClick()

        waitFor(hasTestTag(FountainTestTags.DRINK_SYNC_STATUS) and hasText("Done: 2 new drinks"))
        waitFor(hasTestTag(FountainTestTags.DRINK_SYNC_LAST) and hasText("Last synced today at", substring = true))
        assertEquals(listOf(MAC), fountain.connectedTo)
        // The drinks are on the server, and on the page: today's activity lists both.
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(ActivityTestTags.CARD))
        compose.waitUntil(TIMEOUT_MS) { activityRows() == 2 }

        // Synced again: the fountain still holds them, the server already has them.
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FountainTestTags.DRINK_SYNC))
        button().performClick()
        waitFor(hasTestTag(FountainTestTags.DRINK_SYNC_STATUS) and hasText("Done: no new drinks"))
    }

    @Test
    fun aFountainWhoseSecretTheServerLacksSaysWhatToDo() {
        MockServer.control("PUT", "devices/$CTW3/ble-secret", """{"secret": null}""")
        val fountain = ScriptedFountainTransport(SECRET, emptyList())
        bluetooth.replacement = fountain
        signIn()
        openSyncCard()

        button().performClick()

        waitFor(
            hasTestTag(FountainTestTags.DRINK_SYNC_STATUS) and
                hasText("petkit-local has no pairing key for this fountain."),
        )
        waitFor(hasTestTag(FountainTestTags.DRINK_SYNC_STATUS) and hasText("Import from PetKit", substring = true))
        assertEquals(emptyList<String>(), fountain.connectedTo)
    }

    private fun button() = compose.onNodeWithTag(FountainTestTags.DRINK_SYNC_BUTTON)

    private fun status() = compose.onNodeWithTag(FountainTestTags.DRINK_SYNC_STATUS)

    private fun statusTexts(): List<String> = compose.onAllNodes(hasTestTag(FountainTestTags.DRINK_SYNC_STATUS))
        .fetchSemanticsNodes()
        .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty().map { it.text } }

    private fun activityRows(): Int = compose.onAllNodes(
        SemanticsMatcher("an activity row") { node ->
            node.config.getOrNull(SemanticsProperties.TestTag)?.startsWith("device:activity:") == true
        },
        useUnmergedTree = true,
    ).fetchSemanticsNodes().size

    /** From Home to the EverSweet Max 2's page, its drink history card in view. */
    private fun openSyncCard() {
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(CTW3))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FountainTestTags.DRINK_SYNC))
    }

    private fun signIn() {
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput(TestServer.address)
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput(TestServer.password)
        waitFor(hasTestTag(SignInTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val FIRST_CARD = 1001L
        const val CTW3 = 3003L

        /** The mock's CTW3 (mock-server Seed): its address and its pairing secret's bytes. */
        const val MAC = "A4:C1:38:00:0B:BB"
        val SECRET =
            byteArrayOf(0, 0, 0xA1.toByte(), 0xB2.toByte(), 0xC3.toByte(), 0xD4.toByte(), 0xE5.toByte(), 0xF6.toByte())

        const val CLOCK_EPOCH = 946_684_800L
        const val MS_PER_S = 1000L
        const val TIMEOUT_MS = 30_000L

        /** PetKit's 30 s scan, then the card's answer. */
        const val SCAN_TIMEOUT_MS = 60_000L
    }
}
