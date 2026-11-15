package org.petkitlocal.feature.device.litter

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
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
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless litter box page: content, clicks, dialogs. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class LitterScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val navigation = DeviceNavigation(
        onBack = { calls += "back" },
        onOpenSettings = { calls += "settings:$it" },
        onOpenHistory = { calls += "history" },
    )

    private val callbacks = LitterCallbacks(
        onRefresh = { calls += "refresh" },
        onModeToggle = { key, on -> calls += "mode:$key:$on" },
        onPetFilter = { calls += "pet:$it" },
        onHotspot = { calls += "hotspot:$it" },
        commands = PageCommandCallbacks(
            onAction = { calls += "action:${it.key}" },
            onConfirmAction = { calls += "confirm" },
            onDismissConfirmation = { calls += "dismiss" },
        ),
    )

    private fun show(state: LitterUiState) {
        compose.setContent { PkTheme { LitterScreen(state, navigation, callbacks) } }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `the page shows the box's parts, its status and today against yesterday`() {
        show(LitterFixtures.content)

        compose.onNodeWithText("Pura Max").assertIsDisplayed()
        compose.onNodeWithText("Idle", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Litter, 91 %").assertExists()
        compose.onNodeWithContentDescription("Waste bin, OK").assertExists()
        compose.onNodeWithContentDescription("N50 pack, 21 days").assertExists()
        // The server reports an N60 countdown on a T4 too, but a Pura Max has no N60.
        compose.onNodeWithContentDescription("N60 spray, 4 days").assertDoesNotExist()
        // Four visits today, two yesterday.
        scrollTo("Visits")
        compose.onNodeWithText("+2 vs yesterday", useUnmergedTree = true).assertExists()
        // (95 + 186 + 60 + 40) / 4 = 95 s today; (100 + 140) / 2 = 120 s yesterday.
        compose.onNodeWithText("1:35", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("−0:25 vs yesterday", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the chart can be filtered on a pet, and the activity leads to History`() {
        show(LitterFixtures.content)

        scrollTo("Visits over the day")
        compose.onNodeWithContentDescription("Chart of 4 visits today, by time and duration").assertExists()
        compose.onAllNodesWithText("Tigrou", useUnmergedTree = true).onFirst().performClick()
        scrollTo("See all in History")
        compose.onNodeWithText("See all in History").performClick()

        assertEquals(listOf("pet:2", "history"), calls)
    }

    @Test
    fun `the shared action sheet keeps the everyday actions in view`() {
        show(LitterFixtures.content)

        compose.onNodeWithTag(DeviceTestTags.ACTION_SHEET).assertExists()
        compose.onNode(hasText("Scoop") and hasClickAction()).performClick()
        // Dump Litter is maintenance and destructive: behind More, not in the collapsed row.
        compose.onNodeWithText("Dump Litter").assertDoesNotExist()

        assertEquals(listOf("action:cleaning_start"), calls)
    }

    @Test
    fun `a destructive action is confirmed in the shared dialog naming it`() {
        val dump = LitterFixtures.actionsT4.first { it.key == "dump_litter" }
        show(LitterFixtures.content.copy(commands = CommandState(confirming = dump)))

        compose.onNodeWithText("This cannot be undone", substring = true).assertExists()
        compose.onNode(hasText("Dump Litter") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `a hotspot pill opens its part`() {
        show(LitterFixtures.content)
        compose.onNodeWithContentDescription("Litter, 91 %").performClick()
        assertEquals(listOf("hotspot:${DevicePart.Litter}"), calls)
    }

    @Test
    fun `a hotspot's explanation offers the actions on that part, and the settings`() {
        show(LitterFixtures.content.copy(openPart = DevicePart.Litter))
        compose.onNodeWithText("Top it up", substring = true).assertExists()
        compose.onNode(hasText("Level Litter") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("All settings").performClick()

        assertEquals(listOf("action:level_litter", "hotspot:null", "settings:1002"), calls)
    }

    @Test
    fun `the header's gear is the way to the settings`() {
        show(LitterFixtures.content)

        compose.onNodeWithContentDescription("Settings").performClick()

        assertEquals(listOf("settings:1002"), calls)
    }

    @Test
    fun `mode chips say their state and write the other one`() {
        show(LitterFixtures.content)

        compose.onNodeWithText("Auto Clean · On", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(PageTestTags.mode("auto_work")).performClick()

        assertEquals(listOf("mode:auto_work:false"), calls)
    }

    @Test
    fun `a box in trouble says so in words, over the last known data, with the last command's banner`() {
        show(LitterFixtures.trouble)

        compose.onNodeWithText("Error: bin full", useUnmergedTree = true).assertExists()
        compose.onNodeWithContentDescription("Waste bin, Full, Needs attention").assertExists()
        compose.onNodeWithContentDescription("N60 spray, Missing, Needs attention").assertExists()
        compose.onNodeWithContentDescription("Litter, 12 %, Needs attention").assertExists()
        compose.onNodeWithText("Showing the last known data", substring = true).assertExists()
        compose.onNodeWithTag(DeviceTestTags.FEEDBACK).assertExists()
        compose.onNodeWithText("Sent to the device.", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `with nothing cached and the server unreachable, it says so and offers to retry`() {
        show(LitterFixtures.gone.copy(refreshError = DomainError.Network("timeout")))

        compose.onNodeWithText("The server cannot be reached.").assertExists()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun `a device the server no longer has, and the way back`() {
        show(LitterFixtures.gone)

        compose.onNodeWithText("This device is no longer on the server.").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }
}
