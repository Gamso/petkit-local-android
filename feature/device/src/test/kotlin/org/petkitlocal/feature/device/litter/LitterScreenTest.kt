package org.petkitlocal.feature.device.litter

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
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
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.devicepage.DevicePart
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.ui.RecentClipsTestTags
import org.petkitlocal.core.ui.formatTimeOfDay
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.ActivityTestTags
import org.petkitlocal.feature.device.common.CONFIRM_ARM_MS
import org.petkitlocal.feature.device.common.CommandState
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.common.RecentClipsUi
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
        onOpenHistory = { calls += "history:$it" },
        onOpenDevice = { calls += "device:$it" },
        onOpenLive = { calls += "live:$it" },
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

    private fun show(state: LitterUiState, clips: RecentClipsUi? = null) {
        compose.setContent { PkTheme { LitterScreen(state, navigation, callbacks, clips = clips) } }
    }

    /** A time of today as the page writes it, in the test's locale. */
    private fun time(hour: Int, minute: Int) =
        formatTimeOfDay(Household.at(hour, minute).toKotlinInstant(), Household.zone, Locale.getDefault())

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
        // A Pura Max has no N60 of its own, and no Pura Air is linked to this one.
        compose.onNodeWithContentDescription("N60 spray", substring = true).assertDoesNotExist()
        compose.onNodeWithContentDescription("Pura Air · N60", substring = true).assertDoesNotExist()
        // Four visits today, two yesterday.
        // Each tile is read as words, never as an arrow.
        scrollTo("Today")
        compose.onNodeWithContentDescription("Visits, 4, 2 more than yesterday").assertExists()
        // (95 + 186 + 60 + 40) / 4 = 95 s today; (100 + 140) / 2 = 120 s yesterday.
        compose.onNodeWithContentDescription(
            "Average visit, 1 minute 35 seconds, 25 seconds shorter than yesterday",
        ).assertExists()
    }

    @Test
    fun `the chart can be filtered on a pet, and the activity leads to History`() {
        show(LitterFixtures.content)

        scrollTo("Visits over the day")
        compose.onNodeWithContentDescription("Chart of 4 visits today, by time and duration").assertExists()
        compose.onAllNodesWithText("Tigrou", useUnmergedTree = true).onFirst().performClick()
        scrollTo("See all in History")
        compose.onNodeWithText("See all in History").performClick()

        assertEquals(listOf("pet:2", "history:1002"), calls)
    }

    @Test
    fun `a visit is one row, read as one sentence with the steps that followed it`() {
        show(LitterFixtures.content)
        scrollTo("Mochi spent")

        // Shown: the sentence with the weight, and each step under it with its time.
        compose.onNodeWithText("Mochi spent 1:35 · 4.3\u00A0kg", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("${time(9, 47)} · Cleaning done", useUnmergedTree = true).assertExists()
        // The weight check and the mechanism's start are low-level steps the app does not show.
        compose.onNodeWithText("Weight check", substring = true, useUnmergedTree = true).assertDoesNotExist()
        // Read: one item for the visit and its steps, durations in words.
        compose.onNodeWithTag(ActivityTestTags.row(16)).assertContentDescriptionEquals(
            "${time(9, 45)}, Mochi spent 1 minute 35 seconds, 4.3\u00A0kg. " +
                "${time(9, 47)}, Cleaning done. ${time(9, 48)}, Deodorizing done",
        )
    }

    @Test
    fun `a failed step says so with its reason, and pets the box could not tell are a pet`() {
        show(LitterFixtures.content)
        scrollTo("A pet spent")

        compose.onNodeWithTag(ActivityTestTags.row(14))
            .assert(hasContentDescription("${time(6, 22)}, Deodorizing failed", substring = true))
        compose.onNodeWithTag(ActivityTestTags.row(10))
            .assert(hasContentDescription("${time(1, 22)}, Cleaning failed: bin full", substring = true))
        // Tigrou has no photo: its initial stands for it.
        compose.onNode(hasText("T") and hasAnyAncestor(hasTestTag(ActivityTestTags.row(14))), useUnmergedTree = true)
            .assertExists()
        compose.onNodeWithTag(ActivityTestTags.row(9))
            .assertContentDescriptionEquals("${time(0, 40)}, A pet spent 40 seconds")
        // A pet seen and a scheduled cleaning stay single rows.
        compose.onNodeWithTag(ActivityTestTags.row(13)).assertContentDescriptionEquals("${time(5, 10)}, A pet came by")
        compose.onNodeWithText("Cleaning done", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Periodic", useUnmergedTree = true).assertExists()
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
        compose.mainClock.advanceTimeBy(CONFIRM_ARM_MS)

        compose.onNodeWithText("This cannot be undone", substring = true).assertExists()
        compose.onNode(hasText("Dump Litter") and hasClickAction() and hasAnyAncestor(isDialog())).performClick()

        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `a Pura Max with its Pura Air shows the N60 level as a pill`() {
        show(LitterFixtures.withPuraAir)
        compose.onNodeWithContentDescription("Pura Air · N60, 63 %").performClick()

        assertEquals(listOf("hotspot:${DevicePart.PuraAirSpray}"), calls)
    }

    @Test
    fun `the Pura Air's pill shows its other readings and leads to its own screen`() {
        show(LitterFixtures.withPuraAir.copy(openPart = DevicePart.PuraAirSpray))
        compose.onNodeWithText("by the level it reports", substring = true).assertExists()
        // Its other reading, and the way to its own screen.
        compose.onNodeWithText("K3 Battery").assertExists()
        compose.onNodeWithText("88 %").assertExists()
        compose.onNodeWithText("Open the Pura Air K3").performClick()

        assertEquals(listOf("hotspot:null", "device:4001"), calls)
    }

    @Test
    fun `a hotspot pill opens its part`() {
        show(LitterFixtures.content)
        compose.onNodeWithContentDescription("Litter, 91 %").performClick()
        assertEquals(listOf("hotspot:${DevicePart.Litter}"), calls)
    }

    @Test
    fun `a hotspot's explanation offers the actions on that part, and no way to the settings`() {
        show(LitterFixtures.content.copy(openPart = DevicePart.Litter))
        compose.onNodeWithText("Top it up", substring = true).assertExists()
        compose.onNode(hasText("Level Litter") and hasAnyAncestor(isDialog())).performClick()
        compose.onNodeWithText("All settings").assertDoesNotExist()

        assertEquals(listOf("action:level_litter"), calls)
    }

    @Test
    fun `the header's gear is the way to the settings`() {
        show(LitterFixtures.content)

        compose.onNodeWithContentDescription("Settings").performClick()

        assertEquals(listOf("settings:1002"), calls)
    }

    @Test
    fun `a camera's header leads to its live video`() {
        show(LitterFixtures.trouble)

        compose.onNodeWithContentDescription("Live video").performClick()

        assertEquals(listOf("live:1004"), calls)
    }

    @Test
    fun `a box without a camera has no live video`() {
        show(LitterFixtures.content)

        compose.onNodeWithContentDescription("Live video").assertDoesNotExist()
    }

    @Test
    fun `a camera's page shows its recent clips, each opening its clip, the rest in History`() {
        val clips = DeviceFixtures.recentClips.copy(
            onOpenClip = { calls += "clip:${it.kind}" },
            onSeeAll = { calls += "all-clips" },
        )
        show(LitterFixtures.trouble, clips)

        val list = compose.onNodeWithTag(PageTestTags.LIST)
        list.performScrollToNode(hasTestTag(RecentClipsTestTags.SECTION))
        compose.onNodeWithText("Recent videos").assertIsDisplayed()
        compose.onNodeWithTag(RecentClipsTestTags.clip(12)).performClick()
        list.performScrollToNode(hasText("More videos in History"))
        compose.onNodeWithText("More videos in History").performClick()

        assertEquals(listOf("clip:Highlight", "all-clips"), calls)
    }

    @Test
    fun `a box without a camera has no recent clips`() {
        show(LitterFixtures.content, DeviceFixtures.recentClips)

        compose.onNodeWithTag(RecentClipsTestTags.SECTION).assertDoesNotExist()
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
