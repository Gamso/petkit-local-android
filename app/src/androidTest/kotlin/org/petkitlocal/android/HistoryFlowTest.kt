package org.petkitlocal.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.core.network.dto.TimelineItemDto
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.litter.PageTestTags
import org.petkitlocal.feature.history.HistoryTestTags
import org.petkitlocal.feature.home.HomeTestTags

/**
 * History end to end, against the mock server: opened from a device page it is narrowed to that
 * device; from Home it shows every device. A past day (cached by nothing else) is paged in from
 * the server until its last card, a card opens its detail sheet with the event's decoded fields,
 * and its photo and clip open full screen, the clip read through the app's authenticated client.
 */
@MockOnly("the mock's week of history, cut in its time zone")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class HistoryFlowTest {
    /** The mock cuts days in its own time zone; the phone shows the same days in the same one. */
    @get:Rule(order = 0)
    val zone = DevicePagesFlowTest.TimeZoneRule(MOCK_ZONE)

    @get:Rule(order = 1)
    val server = TestServerRule()

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun fromADevicePageHistoryShowsThatDeviceOnly() {
        signIn()
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val card = hasTestTag(HomeTestTags.device(PURA_MAX))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))

        val seeAll = hasText("See all in History")
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(seeAll)
        compose.onNode(seeAll).performSemanticsAction(SemanticsActions.OnClick)

        waitFor(hasTestTag(HistoryTestTags.SCREEN))
        waitFor(hasContentDescription("Show the history of: Pura Max"))
    }

    @Test
    fun aPastDayIsPagedToItsEndAndACardOpensItsDetailAndMedia() {
        val day = LocalDate.now(ZoneId.of(MOCK_ZONE)).minusDays(PAST_DAYS)
        val cards = runBlocking { allCards(day) }
        // More than one page of History's 25: the last card can only arrive by the cursor.
        check(cards.size > HISTORY_PAGE) { "the mock has only ${cards.size} cards on $day" }
        // A camera box's visit: its event has decoded times, a snapshot and a clip.
        val withMedia = cards.first { it.kind == "visit" && it.media?.snapshot != null && it.media?.highlight != null }

        signIn()
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        compose.onNodeWithContentDescription("History").performClick()
        waitFor(hasTestTag(HistoryTestTags.SCREEN))
        waitFor(hasContentDescription("Show the history of: All devices"))
        compose.onNodeWithTag(HistoryTestTags.day(day)).performClick()

        val list = compose.onNodeWithTag(HistoryTestTags.LIST)
        scrollUntilShown(hasTestTag(HistoryTestTags.card(cards.last().id)))

        list.performScrollToNode(hasTestTag(HistoryTestTags.card(withMedia.id)))
        compose.onNodeWithTag(HistoryTestTags.card(withMedia.id)).performSemanticsAction(SemanticsActions.OnClick)
        waitFor(hasTestTag(HistoryTestTags.SHEET))
        // The event's decoded fields, from GET /timeline/{id}.
        waitFor(hasText("Details"))
        waitFor(hasText("Entered"))

        compose.onNode(hasContentDescription("Snapshot") and isEnabled()).performScrollTo().performClick()
        waitFor(hasTestTag(HistoryTestTags.PHOTO_VIEWER))
        compose.onNodeWithContentDescription("Close").performClick()
        compose.waitUntilDoesNotExist(hasTestTag(HistoryTestTags.PHOTO_VIEWER), TIMEOUT_MS)

        compose.onNode(hasContentDescription("Highlight")).performScrollTo().performClick()
        waitFor(hasTestTag(HistoryTestTags.CLIP_PLAYER))
        // Its length is known once the player has read the file through the bearer-carrying client.
        waitFor(hasText("/ 0:02", substring = true))
        compose.onNode(hasText("This video could not be played.")).assertDoesNotExist()
    }

    /** Scrolls to the end of History until [matcher] shows up, as the next pages arrive. */
    private fun scrollUntilShown(matcher: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(HistoryTestTags.LIST).performScrollToNode(matcher) }.isSuccess
        }
    }

    /** Every card of [day], the way Home reads a day: every page. */
    private suspend fun allCards(day: LocalDate): List<TimelineItemDto> {
        val api = TestServer.signedInApi()
        val cards = mutableListOf<TimelineItemDto>()
        var cursor: String? = null
        do {
            val page = api.getTimeline(day = day.toString(), limit = MAX_LIMIT, cursor = cursor)
            cards += page.items
            cursor = page.nextCursor
        } while (cursor != null)
        return cards
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
        const val PURA_MAX = 1002L
        const val MOCK_ZONE = "Europe/Paris"

        /** A day of the mock's week that nothing but History reads. */
        const val PAST_DAYS = 3L
        const val HISTORY_PAGE = 25
        const val MAX_LIMIT = 100
        const val TIMEOUT_MS = 30_000L
    }
}
