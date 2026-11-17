package org.petkitlocal.feature.device.fountain

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
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
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.ActivityTestTags
import org.petkitlocal.feature.device.common.CONFIRM_ARM_MS
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.QuickActionsTestTags
import org.petkitlocal.feature.device.litter.PageCommandCallbacks
import org.petkitlocal.feature.device.litter.PageTestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless fountain page: content, clicks, dialogs. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class FountainScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val navigation = DeviceNavigation(
        onBack = { calls += "back" },
        onOpenSettings = { calls += "settings:$it" },
        onOpenHistory = { calls += "history" },
        onOpenDevice = { calls += "device:$it" },
    )

    private val callbacks = FountainCallbacks(
        onRefresh = { calls += "refresh" },
        onMode = { calls += "mode:$it" },
        onPetFilter = { calls += "pet:$it" },
        onHotspot = { calls += "hotspot:$it" },
        commands = PageCommandCallbacks(
            onAction = { calls += "action:${it.key}" },
            onConfirmAction = { calls += "confirm" },
            onDismissConfirmation = { calls += "dismiss" },
        ),
    )

    private fun show(state: FountainUiState) {
        compose.setContent { PkTheme { FountainScreen(state, navigation, callbacks) } }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `a W7H shows its tray, tanks and pump, and today's drinks against yesterday, per pet`() {
        show(FountainFixtures.content)

        compose.onNodeWithText("EverSweet Ultra AI").assertIsDisplayed()
        compose.onNodeWithContentDescription("Drinking tray, OK").assertExists()
        compose.onNodeWithContentDescription("Clean water tank, OK").assertExists()
        compose.onNodeWithContentDescription("Waste water tank, OK").assertExists()
        compose.onNodeWithContentDescription("Pump, Running").assertExists()
        // No filter, water flag or battery on this model.
        compose.onNodeWithContentDescription("Filter", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Battery", substring = true).assertDoesNotExist()
        // Its child lock is off: no chip says so. On mains, not relayed: nothing else at the top.
        compose.onNodeWithText("Child lock", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(FountainTestTags.BATTERY).assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.RELAY).assertDoesNotExist()

        // Five drinks today, three yesterday; (42 + 18 + 25 + 11 + 30) / 5 = 25 s against 28 s.
        // Each tile is read as words, never as an arrow.
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FountainTestTags.TODAY))
        compose.onNodeWithContentDescription("Drinks, 5, 2 more than yesterday").assertExists()
        compose.onNodeWithContentDescription("Average drink, 25 seconds, 3 seconds shorter than yesterday")
            .assertExists()
        compose.onNodeWithTag(FountainTestTags.pet(1)).assertExists()
        compose.onNodeWithText("2 drinks · 2 yesterday", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("This fountain does not report drinks", substring = true).assertDoesNotExist()
    }

    @Test
    fun `the drinks chart can be filtered on a pet`() {
        show(FountainFixtures.content)

        scrollTo("Drinks over the day")
        compose.onNodeWithContentDescription("Chart of 5 drinks today, by time and duration").assertExists()
        // The chip, after the pet's row of the Today card.
        compose.onAllNodesWithText("Tigrou", useUnmergedTree = true).onLast().performClick()

        assertEquals(listOf("pet:2"), calls)
    }

    @Test
    fun `a W7H lists today's drinks with their pet, and leads to History`() {
        show(FountainFixtures.content)
        scrollTo("See all in History")

        compose.onNodeWithText("Tigrou drank for 0:42", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(ActivityTestTags.row(35))
            .assert(hasContentDescription("A pet drank for 30 seconds", substring = true))
        // The litter box's visit of the same day is not this fountain's.
        compose.onNodeWithTag(ActivityTestTags.row(39)).assertDoesNotExist()
        compose.onNodeWithText("See all in History").performClick()

        assertEquals(listOf("history"), calls)
    }

    @Test
    fun `a Bluetooth fountain names its relay, its pump time, and says it reports no drinks`() {
        show(FountainFixtures.bluetooth)

        compose.onNodeWithContentDescription("Water level, OK").assertExists()
        compose.onNodeWithContentDescription("Filter, 64 %").assertExists()
        compose.onNodeWithContentDescription("Pump, Running").assertExists()
        compose.onNodeWithText("Bluetooth", substring = true, useUnmergedTree = true).assertExists()
        // Its last frame was decoded three hours ago, through a relay that is online; the line,
        // not the status, says when, and opens the relay.
        compose.onNodeWithText("Last seen", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.RELAY)
            .assert(hasText("Seen 3 h ago · via YumShare Dual-Hopper (online)"))
            .assert(hasClickAction())
            .performClick()
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FountainTestTags.TODAY))
        compose.onNodeWithContentDescription("Pump running, 4 h 00, Since midnight, by the fountain's count")
            .assertExists()
        compose.onNodeWithText("This fountain does not report drinks", substring = true).assertExists()
        compose.onNodeWithText("Drinks over the day").assertDoesNotExist()
        // Nor an activity that could only say there is nothing.
        compose.onNodeWithTag(ActivityTestTags.CARD).assertDoesNotExist()

        assertEquals(listOf("device:2004"), calls)
    }

    @Test
    fun `the mode selector shows the reported mode, and a tap sends another`() {
        show(FountainFixtures.bluetooth)

        val normal = compose.onNodeWithTag(FountainTestTags.mode("normal"))
        val smart = compose.onNodeWithTag(FountainTestTags.mode("smart"))
        normal.assertIsSelected().assert(hasText("Normal"))
        smart.assertIsNotSelected().assert(hasText("Smart"))
        compose.onNodeWithTag(
            FountainTestTags.MODE_BAR,
        ).assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.SelectableGroup))
        // Every feature is off on this W5: no chip, and smart mode is on the bar, not a chip.
        compose.onNodeWithText("Smart mode", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithText("Do not disturb", substring = true, useUnmergedTree = true).assertDoesNotExist()

        smart.performClick()
        assertEquals(listOf("mode:smart"), calls)
    }

    @Test
    fun `a tapped mode is pending, never selected, until the fountain reports it`() {
        show(FountainFixtures.bluetooth.copy(requestedMode = "smart"))

        compose.onNodeWithTag(FountainTestTags.mode("normal")).assertIsSelected()
        compose.onNodeWithTag(FountainTestTags.mode("smart"))
            .assertIsNotSelected()
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.StateDescription,
                    "Requested, waiting for the fountain",
                ),
            )
    }

    @Test
    fun `taps on the mode are ignored while its write is on its way`() {
        show(
            FountainFixtures.bluetooth.copy(
                requestedMode = "smart",
                commands = CommandState(sending = setOf("w5_mode")),
            ),
        )

        compose.onNodeWithTag(FountainTestTags.mode("normal")).performClick()
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `chips are only for features that are on, and the battery reads its level`() {
        show(FountainFixtures.trouble)

        compose.onNodeWithText("Do not disturb · On", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Child lock", substring = true, useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithTag(FountainTestTags.BATTERY).assert(hasContentDescription("Battery 9 %, low"))
        // The relay is offline, and the line says so in words.
        compose.onNodeWithTag(
            DeviceTestTags.RELAY,
        ).assert(hasText("Seen 19 min ago · via Fresh Element Solo (offline)"))
    }

    @Test
    fun `the filter's explanation offers its reset`() {
        show(FountainFixtures.bluetooth.copy(openPart = DevicePart.Filter))
        compose.onNodeWithText("reset its counter", substring = true).assertExists()
        compose.onNode(hasText("Reset Filter") and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf("action:w5_reset_filter"), calls)
    }

    @Test
    fun `the filter reset cannot be undone, so it is confirmed first`() {
        val reset = FountainFixtures.w5.actions.single()
        show(FountainFixtures.bluetooth.copy(commands = CommandState(confirming = reset)))
        compose.onNodeWithText("This cannot be undone", substring = true).assertExists()
        val confirm = hasText("Reset Filter") and hasClickAction() and hasAnyAncestor(isDialog())
        // A second tap of the pill's Reset Filter, landing where the confirm button now is: ignored.
        compose.onNode(confirm).performClick()
        assertEquals(emptyList<String>(), calls)

        compose.mainClock.advanceTimeBy(CONFIRM_ARM_MS)
        compose.onNode(confirm).performClick()
        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `a fountain in trouble says so in words, over the last known data, with the server's refusal`() {
        show(FountainFixtures.trouble)

        compose.onNodeWithContentDescription("Water level, Low, Needs attention").assertExists()
        compose.onNodeWithContentDescription("Filter, 12 %, Needs attention").assertExists()
        compose.onNodeWithContentDescription("Battery, 9 %, Needs attention").assertExists()
        // A pump at rest is not a fault.
        compose.onNodeWithContentDescription("Pump, Stopped").assertExists()
        compose.onNodeWithText("Showing the last known data", substring = true).assertExists()
        compose.onNodeWithTag(DeviceTestTags.FEEDBACK).assertExists()
        compose.onNodeWithText("The Fresh Element Solo must be online", substring = true, useUnmergedTree = true)
            .assertExists()
    }

    @Test
    fun `the mode takes the collapsed row, every action behind More, and the header's gear`() {
        show(FountainFixtures.content)

        // Four modes in view, the actions not: Refill is a tap on More away.
        listOf("Pause", "Continuous", "Intermittent", "On motion").forEach {
            compose.onNode(hasText(it) and hasAnyAncestor(hasTestTag(FountainTestTags.MODE_BAR))).assertExists()
        }
        compose.onNodeWithTag(FountainTestTags.mode("continuous")).assertIsSelected()
        compose.onNodeWithText("Refill").assertDoesNotExist()
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNode(hasText("Refill") and hasClickAction()).performClick()
        // The row is the mode's, not quick actions: nothing to edit.
        compose.onNodeWithTag(QuickActionsTestTags.EDIT_ENTRY).assertDoesNotExist()
        compose.onNodeWithContentDescription("Settings").performClick()

        assertEquals(listOf("action:fountain_refill", "settings:3001"), calls)
    }

    @Test
    fun `a fountain whose only action is on a pill has its mode and no More`() {
        // The W5's one button, Reset Filter, is on its filter's pill: More would hold only that.
        show(FountainFixtures.bluetooth)
        compose.onNodeWithTag(FountainTestTags.MODE_BAR).assertExists()
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).assertDoesNotExist()
        compose.onNodeWithContentDescription("Filter, 64 %").performClick()

        assertEquals(listOf("hotspot:${DevicePart.Filter}"), calls)
    }

    @Test
    fun `a fountain with no settable mode keeps the quick actions row`() {
        val w7h = FountainFixtures.w7h.copy(
            entities = FountainFixtures.w7h.entities.map {
                if (it.key ==
                    "flow_mode"
                ) {
                    it.copy(settable = false)
                } else {
                    it
                }
            },
        )
        show(FountainUiState(page = FountainFixtures.page(w7h), loaded = true))
        compose.onNodeWithTag(FountainTestTags.MODE_BAR).assertDoesNotExist()
        compose.onNode(hasText("Refill") and hasClickAction()).assertExists()
    }

    @Test
    fun `an action on no pill keeps the sheet, so it stays within reach`() {
        // The same W5 with its filter not reported: no pill offers the reset any more.
        val w5 = FountainFixtures.w5.copy(
            entities = FountainFixtures.w5.entities.filterNot {
                it.key.startsWith("w5_filter")
            },
        )
        show(FountainUiState(page = FountainFixtures.page(w5, relay = FountainFixtures.relay), loaded = true))
        compose.onNodeWithTag(DeviceTestTags.ACTION_SHEET).assertExists()
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).assertExists()
    }

    @Test
    fun `with nothing cached and the server unreachable, it says so and offers to retry`() {
        show(FountainUiState(loaded = true, refreshError = DomainError.Network("timeout")))

        compose.onNodeWithText("The server cannot be reached.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("refresh"), calls)
    }
}
