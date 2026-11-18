package org.petkitlocal.feature.device.feeder

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertContentDescriptionEquals
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import java.time.DayOfWeek
import java.util.Locale
import kotlin.time.toKotlinInstant
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.feeder.FeederDayFilter
import org.petkitlocal.core.testing.Household
import org.petkitlocal.core.ui.LiveCardState
import org.petkitlocal.core.ui.LiveCardTestTags
import org.petkitlocal.core.ui.formatTimeOfDay
import org.petkitlocal.feature.device.DeviceFixtures
import org.petkitlocal.feature.device.DeviceNavigation
import org.petkitlocal.feature.device.common.ActivityTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.litter.PageCommandCallbacks
import org.petkitlocal.feature.device.litter.PageTestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless feeder page, its plan editor and its feed form. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class FeederScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val callbacks = FeederCallbacks(
        onRefresh = { calls += "refresh" },
        onPortions = { hopper, n -> calls += "portions:$hopper:$n" },
        onFeedNow = { calls += "feed" },
        onPlanActive = { calls += "active:$it" },
        onEditPlan = { calls += "edit" },
        onPlanEdit = { calls += "plan:$it" },
        onSavePlan = { calls += "save" },
        onAddFeed = { calls += "add" },
        onSaveFeed = { calls += "save-feed" },
        onCancelFeed = { calls += "cancel:$it" },
        onDayFilter = { calls += "day:$it" },
        onPetFilter = { calls += "pet:$it" },
        commands = PageCommandCallbacks(onAction = { calls += "action:${it.key}" }),
    )

    private fun show(state: FeederUiState, live: LiveCardState = LiveCardState()) {
        compose.setContent { PkTheme { FeederScreen(state, navigation, callbacks, live = live) } }
    }

    private val navigation = DeviceNavigation(
        onOpenHistory = { calls += "history:$it" },
        onOpenClip = { calls += "clip:${it.kind}" },
        onOpenLive = { calls += "live:$it" },
    )

    private fun time(hour: Int, minute: Int) =
        formatTimeOfDay(Household.at(hour, minute).toKotlinInstant(), Household.zone, Locale.getDefault())

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `a single hopper shows its next meal, today's plan and its one-off feed`() {
        show(FeederFixtures.single)

        compose.onNodeWithText("Fresh Element Solo").assertExists()
        compose.onNodeWithContentDescription("Desiccant, 3 days, Needs attention").assertExists()
        compose.onNodeWithText("Today, 12:00", substring = true).assertExists()
        compose.onNodeWithText("Served today: 20 g · 1 meal").assertExists()
        compose.onNodeWithText("Gives the feeder's own manual portion.").assertExists()
        compose.onNodeWithTag(FeederTestTags.FEED_NOW).performClick()
        scrollTo("Edit the weekly plan")
        compose.onNodeWithText("Served", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Active", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(FeederTestTags.PLAN_ACTIVE).performClick()
        compose.onNodeWithTag(FeederTestTags.EDIT_PLAN).performClick()
        scrollTo("Add a one-off feed")
        compose.onNodeWithText("Tomorrow, 12:30", substring = true).assertExists()
        compose.onNodeWithContentDescription("Cancel the feed of Tomorrow, 12:30", substring = true).performClick()
        compose.onNodeWithTag(FeederTestTags.ADD_ONE_OFF).performClick()

        assertEquals(listOf("feed", "active:false", "edit", "cancel:d_1", "add"), calls)
    }

    @Test
    fun `a single hopper's day reads each meal with its amount, a failed one with its reason`() {
        show(FeederFixtures.single)
        scrollTo("See all in History")

        compose.onNodeWithText("All 2").assertExists()
        compose.onNodeWithText("Meals 2").assertExists()
        // No camera, no chip for it, and no meals eaten: it cannot see who eats.
        compose.onNodeWithText("At the camera", substring = true).assertDoesNotExist()
        compose.onNodeWithTag(FeederTestTags.EATING).assertDoesNotExist()
        compose.onNodeWithTag(
            ActivityTestTags.row(30),
        ).assertContentDescriptionEquals("${time(7, 0)}, Meal served, 1 portion")
        compose.onNodeWithTag(ActivityTestTags.row(34))
            .assertContentDescriptionEquals("${time(9, 15)}, Meal failed: nothing dispensed")
        compose.onNodeWithText("See all in History").performClick()

        assertEquals(listOf("history:2001"), calls)
    }

    @Test
    fun `a camera feeder's live card leads to its live video`() {
        show(FeederFixtures.dualPaused, live = DeviceFixtures.liveCard)

        compose.onNodeWithTag(LiveCardTestTags.CARD)
            .assertContentDescriptionEquals("Watch YumShare Dual-Hopper live. Still from ${time(5, 10)}")
            .performClick()
        compose.onNodeWithContentDescription("Live video").assertDoesNotExist()
        assertEquals(listOf("live:2004"), calls)
    }

    @Test
    fun `a feeder without a camera has no live card`() {
        show(FeederFixtures.single, live = DeviceFixtures.liveCard)
        compose.onNodeWithTag(LiveCardTestTags.CARD).assertDoesNotExist()
    }

    @Test
    fun `a dual hopper's meals give their portions per hopper, and a clip opens in the player`() {
        show(FeederFixtures.dualPaused)
        scrollTo("See all in History")

        compose.onNodeWithText(
            "2 portions · Hopper\u00A01\u00A0×1 · Hopper\u00A02\u00A0×1",
            useUnmergedTree = true,
        ).assertExists()
        compose.onNodeWithTag(ActivityTestTags.row(31)).assertContentDescriptionEquals(
            "${time(8, 0)}, Meal served, 2 portions, 1 from hopper 1, 1 from hopper 2",
        )
        compose.onNodeWithTag(ActivityTestTags.row(33))
            .assertContentDescriptionEquals("${time(9, 40)}, Meal served, 2 portions, 2 from hopper 2")
        compose.onNodeWithTag(ActivityTestTags.row(32)).assertContentDescriptionEquals("${time(9, 20)}, Mochi came by")
        // Two rows have a clip; the feed the camera did not record has none, and says nothing of it.
        compose.onAllNodesWithContentDescription("Play the video").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("Play the video").onFirst().performClick()
        compose.onNodeWithTag(FeederTestTags.dayFilter(FeederDayFilter.Camera)).assert(hasText("At the camera 4"))
        compose.onNodeWithTag(FeederTestTags.dayFilter(FeederDayFilter.Camera)).performClick()

        assertEquals(listOf("clip:Highlight", "day:Camera"), calls)
    }

    @Test
    fun `a camera feeder's Today reads its meals eaten and the average meal against yesterday`() {
        show(FeederFixtures.eating())
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FeederTestTags.EATING))

        compose.onNodeWithContentDescription("Meals eaten, 3, 1 more than yesterday").assertExists()
        compose.onNodeWithContentDescription("Average meal, 1 minute 8 seconds, 22 seconds shorter than yesterday")
            .assertExists()
        // Tigrou is eating still: counted, with no duration yet, so a mark on the axis.
        compose.onNodeWithContentDescription(
            "Chart of 3 meals eaten today, by time and duration. 1 with no known duration, on the axis",
        ).assertExists()
        compose.onNodeWithText("Meals eaten over the day", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `meals eaten with none yesterday have nothing to compare with`() {
        show(FeederFixtures.eating(yesterday = emptyList()))
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FeederTestTags.EATING))
        compose.onNodeWithContentDescription("Meals eaten, 3").assertExists()
        compose.onNodeWithContentDescription("Average meal, 1 minute 8 seconds").assertExists()
    }

    @Test
    fun `meals eaten as many and as long as yesterday are the same as yesterday`() {
        val sameAsToday = FeederFixtures.todayItems.map { it.copy(id = it.id + 100) }
        show(FeederFixtures.eating(yesterday = sameAsToday))
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FeederTestTags.EATING))

        compose.onNodeWithContentDescription("Meals eaten, 3, Same as yesterday").assertExists()
        compose.onNodeWithContentDescription("Average meal, 1 minute 8 seconds, Same as yesterday").assertExists()
    }

    @Test
    fun `meals eaten without a duration keep their count, lose the average, and lie on the axis`() {
        show(
            FeederFixtures.eating(
                today = FeederFixtures.untimed(FeederFixtures.todayItems),
                yesterday = FeederFixtures.untimed(FeederFixtures.yesterdayItems),
            ),
        )
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FeederTestTags.EATING))

        compose.onNodeWithContentDescription("Meals eaten, 3, 1 more than yesterday").assertExists()
        compose.onNodeWithText("Average meal", useUnmergedTree = true).assertDoesNotExist()
        compose.onNodeWithContentDescription(
            "Chart of 3 meals eaten today, by time and duration. 3 with no known duration, on the axis",
        ).assertExists()
        compose.onNodeWithText("Durations not reported", useUnmergedTree = true).assertExists()
    }

    @Test
    fun `the meals eaten chart can be filtered on a pet, apart from the day's chips`() {
        show(FeederFixtures.eating())
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(hasTestTag(FeederTestTags.EATING))

        // Mochi and Tigrou ate today; the cat the camera could not tell has no chip of its own.
        fun chip(name: String) = hasText(name) and hasAnyAncestor(hasTestTag(FeederTestTags.EATING))
        compose.onNode(chip("Tigrou"), useUnmergedTree = true).performClick()
        compose.onNode(chip("Mochi"), useUnmergedTree = true).performClick()

        assertEquals(listOf("pet:2", "pet:1"), calls)
    }

    @Test
    fun `a chip shows only its rows`() {
        show(FeederFixtures.dualPaused.copy(dayFilter = FeederDayFilter.Camera))
        scrollTo("See all in History")

        compose.onNodeWithTag(ActivityTestTags.row(32)).assertExists()
        compose.onNodeWithTag(ActivityTestTags.row(31)).assertDoesNotExist()
        compose.onNodeWithTag(FeederTestTags.dayFilter(FeederDayFilter.Camera)).assertIsSelected()
    }

    @Test
    fun `a paused dual hopper says so, and feed now asks for a portion`() {
        show(FeederFixtures.dualPaused)

        compose.onNodeWithText("The plan is paused", substring = true).assertExists()
        compose.onNodeWithContentDescription("Hopper 2, Empty, Needs attention").assertExists()
        compose.onNodeWithText("Choose at least one portion.").assertExists()
        compose.onNodeWithContentDescription("Hopper 1: one portion more").performClick()
        scrollTo("Edit the weekly plan")
        compose.onAllNodesWithText("Paused", useUnmergedTree = true).assertCountEquals(2)

        assertEquals(listOf("portions:0:1"), calls)
    }

    @Test
    fun `the shared sheet feeds in view, and leads to the plan, later and History behind More`() {
        show(FeederFixtures.single)

        compose.onNode(hasText("Feed") and hasClickAction()).performClick()
        listOf("Plan", "Later", "History").forEach { label ->
            compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
            compose.onNode(hasText(label) and hasClickAction()).performClick()
        }

        // Feed goes to the ViewModel, which feeds now with the chosen portions.
        assertEquals(listOf("action:feed", "edit", "add", "history:2001"), calls)
    }

    @Test
    fun `an unread plan can be read again`() {
        show(FeederFixtures.planUnread)

        scrollTo("The plan could not be read.")
        compose.onNodeWithText("Try again").performClick()

        assertEquals(listOf("refresh"), calls)
    }

    @Test
    fun `the plan editor says what to fix where it is`() {
        compose.setContent {
            PkTheme {
                PlanEditor(FeederFixtures.editor, saving = false, onEdit = {
                    calls += "plan:$it"
                }, onSave = {}, onClose = {})
            }
        }

        compose.onNodeWithText("Monday already has meals above.").assertExists()
        compose.onNodeWithText("Two meals", substring = true).assertDoesNotExist()
        compose.onAllNodesWithContentDescription("Monday")[2].performClick()

        assertEquals(listOf("plan:${PlanEdit.ToggleDay(2, DayOfWeek.MONDAY)}"), calls)
    }

    @Test
    fun `the feed form says what to fix and still lets the user try`() {
        compose.setContent {
            PkTheme {
                FeedForm(FeederFixtures.feedForm, FeederFixtures.today, saving = false, onChange = {}, onSave = {
                    calls +=
                        "save-feed"
                }, onDismiss = {})
            }
        }

        compose.onNodeWithText("This time has passed: pick a later one.").assertExists()
        compose.onNodeWithText("Choose at least one portion.").assertExists()
        compose.onNodeWithTag(FeederTestTags.SAVE).performClick()

        assertEquals(listOf("save-feed"), calls)
    }
}
