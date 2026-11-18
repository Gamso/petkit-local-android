package org.petkitlocal.android

import android.os.ParcelFileDescriptor
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.component.PkConfirmTestTags
import org.petkitlocal.core.domain.feeder.FeederDayFilter
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.feeder.FeederTestTags
import org.petkitlocal.feature.device.litter.PageTestTags
import org.petkitlocal.feature.home.HomeTestTags

/**
 * The litter box and feeder pages end to end, against the mock server: sign in, open each device
 * from Home. A destructive action is confirmed before it is sent, its banner says it was queued
 * for the HTTP box, and the box reports the cycle over `/ws`; feed now is queued and the feeder
 * reports feeding; a one-off feed is added and cancelled on the server; a meal served shows in
 * the feeder's day, counted, with what each hopper gave; a cat eating at the camera is counted
 * in the meals eaten.
 */
@MockOnly("the mock's Pura Max and feeder, and the commands they answer")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class DevicePagesFlowTest {
    /** Meal times and one-off feeds are in the feeder's time zone: the mock's. */
    @get:Rule(order = 0)
    val zone = TimeZoneRule(MOCK_ZONE)

    @get:Rule(order = 1)
    val server = TestServerRule()

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aConfirmedDumpIsQueuedAndTheBoxReportsIt() {
        signIn()
        open(PURA_MAX)
        waitFor(hasText("Pura Max"))
        waitFor(hasContentDescription("Litter,", substring = true))

        // Dump Litter cannot be undone: behind More in the shared sheet, and confirmed first.
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNode(
            hasText("Dump Litter") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET)),
        ).performClick()
        waitFor(hasText("Dump Litter?"))
        // The confirm button takes taps once the dialog has been open a moment.
        val confirm = hasTestTag(PkConfirmTestTags.CONFIRM) and hasText("Dump Litter")
        waitFor(confirm)
        compose.onNode(confirm).performClick()
        // The Pura Max polls over HTTP: queued, then its cycle shows when the mock reports it.
        waitFor(hasTestTag(DeviceTestTags.FEEDBACK) and hasText("Queued", substring = true))
        waitFor(hasText("Dumping", ignoreCase = true) and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS)))
    }

    @Test
    fun feedNowIsQueuedAndAOneOffFeedIsAddedThenCancelled() {
        signIn()
        open(FRESH_ELEMENT)
        waitFor(hasText("Fresh Element Solo"))
        waitFor(hasTestTag(FeederTestTags.FEED_NOW) and isEnabled())

        compose.onNodeWithTag(FeederTestTags.FEED_NOW).tap()
        waitFor(hasTestTag(DeviceTestTags.FEEDBACK) and hasText("Feed") and hasText("Queued", substring = true))
        waitFor(hasText("Feeding") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS)))

        val list = compose.onNodeWithTag(PageTestTags.LIST)
        list.performScrollToNode(hasTestTag(FeederTestTags.ADD_ONE_OFF))
        compose.onNodeWithTag(FeederTestTags.ADD_ONE_OFF).tap()
        waitFor(hasTestTag(FeederTestTags.FEED_FORM))
        compose.onNodeWithTag(FeederTestTags.SAVE).performClick()
        waitFor(hasTestTag(DeviceTestTags.FEEDBACK) and hasText("One-off feed") and hasText("Queued", substring = true))

        val cancel = hasContentDescription("Cancel the feed of", substring = true)
        list.performScrollToNode(cancel)
        compose.onNode(cancel).tap()
        waitFor(hasText("One-off feed cancelled"))
        list.performScrollToNode(hasText("None planned."))
    }

    @Test
    fun aMealServedShowsInTheDayWithItsPortionsPerHopper() {
        signIn()
        open(DUAL_HOPPER)
        waitFor(hasText("YumShare Dual-Hopper"))
        val list = compose.onNodeWithTag(PageTestTags.LIST)
        val meals = hasTestTag(FeederTestTags.dayFilter(FeederDayFilter.Meals))
        list.performScrollToNode(meals)
        waitFor(hasTestTag(FeederTestTags.dayFilter(FeederDayFilter.Camera)))
        val before = compose.onNode(meals).chipCount()

        // Two portions from hopper 1 and one from hopper 2: no meal of the seed gave that.
        MockServer.control("POST", "devices/$DUAL_HOPPER/meal", """{"amount1": 2, "amount2": 1}""")

        // The feeder reports the meal over /ws: its row says what each hopper gave, as its card
        // does, and the chip counts it.
        list.performScrollToNode(meals)
        waitFor(hasText("3 portions · Hopper\u00A01\u00A0×2 · Hopper\u00A02\u00A0×1"), unmerged = true)
        compose.waitUntil(TIMEOUT_MS) { compose.onNode(meals).chipCount() > before }
    }

    @Test
    fun aCatEatingAtTheCameraIsCountedInTheMealsEaten() {
        signIn()
        open(DUAL_HOPPER)
        waitFor(hasText("YumShare Dual-Hopper"))
        val list = compose.onNodeWithTag(PageTestTags.LIST)
        list.performScrollToNode(hasTestTag(FeederTestTags.EATING))
        val eaten = hasContentDescription("Meals eaten, ", substring = true)
        waitFor(eaten)
        val before = compose.onNode(eaten).mealsEaten()

        // Tigrou eats at the camera for a minute and a half; the feeder reports it over /ws.
        MockServer.control("POST", "devices/$DUAL_HOPPER/eat", """{"pet_id": 2, "duration_sec": 90}""")

        list.performScrollToNode(hasTestTag(FeederTestTags.EATING))
        compose.waitUntil(TIMEOUT_MS) { compose.onNode(eaten).mealsEaten() == before + 1 }
    }

    /** The figure of the meals-eaten tile, as TalkBack reads it: "Meals eaten, 4, …" is 4. */
    private fun SemanticsNodeInteraction.mealsEaten(): Int =
        fetchSemanticsNode().config[SemanticsProperties.ContentDescription].joinToString("")
            .substringAfter(", ").substringBefore(",").trim().toInt()

    /** The count a chip of the feeder's day shows: "Meals 4" is 4. */
    private fun SemanticsNodeInteraction.chipCount(): Int =
        fetchSemanticsNode().config[SemanticsProperties.Text].joinToString("").substringAfterLast(' ').toInt()

    /** From Home: the grid is lazy, so the first card says the cache is read, then [deviceId]'s is scrolled to. */
    private fun open(deviceId: Long) {
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(deviceId))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
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

    /**
     * Clicks through the node's own click action: a list item scrolled into view may still sit
     * under the action sheet or its banner, where a touch would land on them.
     */
    private fun SemanticsNodeInteraction.tap() {
        performSemanticsAction(SemanticsActions.OnClick)
    }

    private fun waitFor(matcher: SemanticsMatcher, unmerged: Boolean = false) {
        if (unmerged) {
            compose.waitUntil(TIMEOUT_MS) {
                compose.onAllNodes(matcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
            }
        } else {
            compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
        }
    }

    /** Sets the device's time zone for the test and puts the previous one back. */
    class TimeZoneRule(private val zone: String) : ExternalResource() {
        private var previous: String? = null

        override fun before() {
            previous = shell("getprop persist.sys.timezone").trim().takeIf { it.isNotEmpty() }
            shell("cmd alarm set-timezone $zone")
        }

        override fun after() {
            previous?.let { shell("cmd alarm set-timezone $it") }
        }

        private fun shell(command: String): String {
            val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
            return ParcelFileDescriptor.AutoCloseInputStream(output).bufferedReader().use { it.readText() }
        }
    }

    private companion object {
        const val FIRST_CARD = 1001L
        const val PURA_MAX = 1002L
        const val FRESH_ELEMENT = 2001L
        const val DUAL_HOPPER = 2004L
        const val MOCK_ZONE = "Europe/Paris"

        // A page's first refresh reads every device's detail and two days of the timeline.
        const val TIMEOUT_MS = 30_000L
    }
}
