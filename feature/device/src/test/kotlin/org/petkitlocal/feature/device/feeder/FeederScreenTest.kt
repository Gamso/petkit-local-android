package org.petkitlocal.feature.device.feeder

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import java.time.DayOfWeek
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.feature.device.DeviceNavigation
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
        commands = PageCommandCallbacks(onAction = { calls += "action:${it.key}" }),
    )

    private fun show(state: FeederUiState) {
        compose.setContent {
            PkTheme { FeederScreen(state, DeviceNavigation(onOpenHistory = { calls += "history:$it" }), callbacks) }
        }
    }

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
