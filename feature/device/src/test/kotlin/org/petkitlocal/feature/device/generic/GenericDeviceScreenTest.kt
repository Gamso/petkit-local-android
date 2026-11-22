package org.petkitlocal.feature.device.generic

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.ui.LiveCardTestTags
import org.petkitlocal.core.ui.RecentClipsTestTags
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.common.CONFIRM_ARM_MS
import org.petkitlocal.feature.device.common.CommandFeedback
import org.petkitlocal.feature.device.common.CommandResult
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.CommandSubject
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.LiveCardUi
import org.petkitlocal.feature.device.common.RecentClipsUi
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless generic device screen. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2000dp")
class GenericDeviceScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val actions = GenericDeviceActions(
        onBack = { calls += "back" },
        onOpenSettings = { calls += "settings" },
        onRefresh = { calls += "refresh" },
        onAction = { calls += "action:${it.key}" },
        onConfirmAction = { calls += "confirm" },
        onDismissConfirmation = { calls += "dismiss-confirmation" },
        onDismissFeedback = { calls += "dismiss-feedback" },
        onOpenDevice = { calls += "device:$it" },
    )

    private fun show(
        state: GenericDeviceUiState,
        clips: RecentClipsUi? = null,
        live: LiveCardUi = LiveCardUi(),
        actions: GenericDeviceActions = this.actions,
    ) {
        compose.setContent { PkTheme { GenericDeviceScreen(state, actions, clips = clips, live = live) } }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(DeviceTestTags.LIST).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `an accessory says which device it reports through, and that line opens it`() {
        show(DeviceFixtures.generic(DeviceFixtures.purifier).copy(relay = DeviceFixtures.puraMax))

        compose.onNodeWithText("Reports through Pura Max").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.RELAY).performClick()

        assertEquals(listOf("device:1002"), calls)
    }

    @Test
    fun `an accessory out of reach says which device must be online`() {
        val feedback = CommandFeedback(
            CommandSubject.Setting("ctw3_light", "Light"),
            CommandResult.Failed(DomainError.AccessoryUnreachable("Fresh Element Solo")),
            id = 1,
        )
        show(DeviceFixtures.generic(DeviceFixtures.purifier, commands = CommandState(feedback = feedback)))

        compose.onNodeWithText(
            "The Fresh Element Solo must be online (MQTT) to reach this accessory.",
            useUnmergedTree = true,
        ).assertExists()
    }

    @Test
    fun `status, readings in words and the way back`() {
        show(DeviceFixtures.generic())

        compose.onNodeWithText("EverSweet Ultra AI").assertIsDisplayed()
        compose.onNode(hasText("Idle") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS))).assertIsDisplayed()
        compose.onNodeWithText("Live link (MQTT)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Firmware 1.263").assertIsDisplayed()
        // Values in words and units: unreported, a temperature, an option label, a boolean.
        compose.onNodeWithText("Not reported").assertExists()
        compose.onNodeWithText("24.5 °C").assertExists()
        // The status line, and the deep-clean stage's option label.
        compose.onAllNodesWithText("Idle").assertCountEquals(2)
        compose.onNodeWithText("No").assertExists()

        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    fun `a camera's live card comes before what it reports, and goes full screen in place`() {
        show(DeviceFixtures.generic(), live = DeviceFixtures.liveCardUi(DeviceFixtures.liveCard, calls))

        compose.onNodeWithTag(LiveCardTestTags.CARD).assertIsDisplayed().performClick()
        assertEquals("the middle of a card waiting for play is play", listOf("live-visible", "live-play"), calls)
        compose.onNodeWithContentDescription("Full screen").performClick()
        assertEquals(listOf("live-visible", "live-play", "live-fullscreen"), calls)
    }

    @Test
    fun `a purifier, which has no camera, has no live card`() {
        show(DeviceFixtures.generic(device = DeviceFixtures.purifier), live = LiveCardUi(DeviceFixtures.liveCard))

        compose.onNodeWithTag(LiveCardTestTags.CARD).assertDoesNotExist()
    }

    @Test
    fun `a camera shows its recent clips after what it reports, each opening its clip`() {
        var opened: String? = null
        show(DeviceFixtures.generic(), DeviceFixtures.recentClips.copy(onOpenClip = { opened = it.url }))

        compose.onNodeWithTag(DeviceTestTags.LIST).performScrollToNode(hasTestTag(RecentClipsTestTags.SECTION))
        compose.onNodeWithTag(RecentClipsTestTags.clip(12)).performClick()
        assertEquals(Household.cameraClips.first { it.item.id == 12L }.clip.url, opened)
    }

    @Test
    fun `a purifier, which has no camera, shows no recent clips`() {
        show(DeviceFixtures.generic(device = DeviceFixtures.purifier), DeviceFixtures.recentClips)

        compose.onNodeWithTag(RecentClipsTestTags.SECTION).assertDoesNotExist()
    }

    @Test
    fun `diagnostics are folded away until asked for`() {
        show(DeviceFixtures.generic())

        compose.onNodeWithText("WiFi Signal").assertDoesNotExist()
        scrollTo("Show diagnostics (1)")
        compose.onNodeWithText("Show diagnostics (1)").performClick()
        scrollTo("WiFi Signal")
        compose.onNodeWithText("-54 dBm").assertExists()
    }

    @Test
    fun `the settings open from the header's gear, the only way to them`() {
        show(DeviceFixtures.generic())

        compose.onNodeWithContentDescription("Settings").performClick()

        assertEquals(listOf("settings"), calls)
        compose.onNodeWithText("Settings").assertDoesNotExist()
    }

    @Test
    fun `the sheet shows the everyday actions and More, which shows the rest, destructive last`() {
        show(DeviceFixtures.generic())

        compose.onNode(
            hasText("Refill") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET)),
        ).assertIsDisplayed()
        compose.onNode(hasText("Drain & Flush") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET)))
            .assertIsDisplayed()
        compose.onNodeWithText("Drain").assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.SHEET_HANDLE)
            .assertContentDescriptionEquals("Show all actions")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Collapsed"))

        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()

        compose.onNodeWithText("Drain").assertIsDisplayed()
        compose.onNodeWithText("Asks for confirmation first").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.SHEET_HANDLE)
            .assertContentDescriptionEquals("Hide the other actions")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Expanded"))
        compose.onNodeWithText("Drain").performClick()

        // A tap runs the action and folds the sheet back.
        assertEquals(listOf("action:fountain_drain"), calls)
        compose.onNodeWithText("Drain").assertDoesNotExist()
    }

    @Test
    fun `the handle expands and collapses the sheet too`() {
        show(DeviceFixtures.generic())

        compose.onNodeWithTag(DeviceTestTags.SHEET_HANDLE).performClick()
        compose.onNodeWithText("Drain").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.SHEET_HANDLE).performClick()
        compose.onNodeWithText("Drain").assertDoesNotExist()
    }

    @Test
    fun `three everyday actions or fewer need no More`() {
        val few = DeviceFixtures.fountain.copy(actions = listOf(DeviceFixtures.flush, DeviceFixtures.refill))
        show(DeviceFixtures.generic(device = few))

        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.SHEET_HANDLE).assertDoesNotExist()
        compose.onNodeWithText("Refill").assertIsDisplayed()
    }

    @Test
    fun `actions come from the sheet, and a destructive one is confirmed in a dialog`() {
        show(DeviceFixtures.generic(commands = DeviceFixtures.sent.copy(confirming = DeviceFixtures.drain)))

        compose.onNodeWithText("Refill").performClick()
        compose.onNodeWithText("Drain?").assertIsDisplayed()
        compose.onNodeWithText("This cannot be undone", substring = true).assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()

        assertEquals(listOf("action:fountain_refill", "dismiss-confirmation"), calls)
    }

    @Test
    fun `the confirm button names the action`() {
        // The dialog's button, not the sheet's button of the same name.
        show(DeviceFixtures.generic(commands = DeviceFixtures.sent.copy(confirming = DeviceFixtures.drain)))
        // The confirm button takes taps once the dialog has been open a moment.
        compose.mainClock.advanceTimeBy(CONFIRM_ARM_MS)

        compose.onNode(
            hasText("Drain") and hasClickAction() and !hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET)),
        )
            .performClick()

        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `a command says how it was delivered, and a tap dismisses it`() {
        show(DeviceFixtures.generic(commands = DeviceFixtures.sent))

        compose.onNodeWithText("Queued: the device takes it within about 10 s.").assertIsDisplayed()
        compose.onNodeWithTag(DeviceTestTags.FEEDBACK).performClick()

        assertEquals(listOf("dismiss-feedback"), calls)
    }

    @Test
    fun `a refusal shows the server's own message`() {
        show(DeviceFixtures.generic(commands = DeviceFixtures.refused))

        compose.onNodeWithText("Fountain Time must be between 1 and 60").assertIsDisplayed()
    }

    @Test
    fun `a device with no setting and no action has neither shortcut nor sheet`() {
        show(DeviceFixtures.generic(device = DeviceFixtures.purifier, offline = true))

        compose.onNodeWithText("Server unreachable. Showing the last known data.").assertIsDisplayed()
        compose.onNodeWithText("Liquid", substring = true).assertExists()
        compose.onNodeWithContentDescription("Settings").assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.ACTION_SHEET).assertDoesNotExist()
    }

    @Test
    fun `a device that is gone says so`() {
        show(GenericDeviceUiState(loaded = true, device = null))

        compose.onNodeWithText("This device is no longer on the server.").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h2000dp")
    fun `in French`() {
        show(DeviceFixtures.generic(commands = DeviceFixtures.sent))

        compose.onNode(hasText("Au repos") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS))).assertIsDisplayed()
        compose.onNodeWithText("Liaison directe (MQTT)", substring = true).assertIsDisplayed()
        compose.onNodeWithText("En attente : l'appareil le prendra d'ici 10 s environ.").assertIsDisplayed()
        compose.onNodeWithContentDescription("Réglages").assertIsDisplayed()
        compose.onNodeWithText("Plus").assertIsDisplayed()
    }
}
