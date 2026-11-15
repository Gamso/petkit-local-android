package org.petkitlocal.feature.history

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isPopup
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.DomainError
import org.petkitlocal.core.domain.model.TimelineCategory
import org.petkitlocal.core.testing.Household
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless History screen and its detail sheet. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class HistoryScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val callbacks = HistoryCallbacks(
        onBack = { calls += "back" },
        onSelectDay = { calls += "day:$it" },
        onShiftWeek = { calls += "week:$it" },
        onCategory = { calls += "category:${it?.key}" },
        onDevice = { calls += "device:$it" },
        onPet = { calls += "pet:$it" },
        onLoadMore = { calls += "more" },
        onRetry = { calls += "retry" },
        onOpenEntry = { calls += "open:${it.item.id}" },
        onRetryEvent = { calls += "retry-event" },
        onCloseSheet = { calls += "close" },
        onOpenPhoto = { calls += "photo:${it.kind}" },
        onOpenClip = { calls += "clip:${it.kind}" },
    )

    private fun show(state: HistoryUiState) {
        compose.setContent { PkTheme { HistoryScreen(state, callbacks) } }
    }

    private fun showSheet(sheet: EventSheetState) {
        compose.setContent { PkTheme { EventSheetContent(sheet, Household.zone, callbacks) } }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(HistoryTestTags.LIST).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `a card says when, who, where, how heavy and how long, and opens its detail`() {
        show(HistoryFixtures.state())

        // Weights take the locale's own spacing before the unit.
        scrollTo("Purobot Max Pro 2 · 4:00 · 4.3")
        compose.onNodeWithText("Purobot Max Pro 2 · 4:00 · 4.3", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(HistoryTestTags.card(12)).performClick()
        // A feeder's card: its kind by key, no pet, the device.
        scrollTo("Fresh Element Solo")
        compose.onNodeWithText("Fresh Element Solo", substring = true).assertIsDisplayed()

        assertEquals(listOf("open:12"), calls)
    }

    @Test
    fun `the strip picks a day and moves a week back, with no week after today's`() {
        show(HistoryFixtures.state())

        compose.onNodeWithText("History").assertIsDisplayed()
        compose.onNodeWithText("Today").assertIsDisplayed()
        compose.onNodeWithTag(HistoryTestTags.day(Household.today.minusDays(2))).performClick()
        compose.onNodeWithContentDescription("Previous week").performClick()
        compose.onNodeWithContentDescription("Next week").assertDoesNotExist()
        compose.onNodeWithContentDescription("Back").performClick()

        assertEquals(listOf("day:${Household.today.minusDays(2)}", "week:-1", "back"), calls)
    }

    @Test
    fun `an earlier week can go forward again`() {
        show(HistoryFixtures.state().copy(weekEnd = Household.today.minusDays(7)))

        compose.onNodeWithContentDescription("Next week").performClick()

        assertEquals(listOf("week:1"), calls)
    }

    @Test
    fun `a chip filters by kind, and the pickers by device and pet`() {
        show(HistoryFixtures.state())

        compose.onNodeWithTag(HistoryTestTags.filter("toileting")).performScrollTo().performClick()
        compose.onNodeWithTag(HistoryTestTags.filter("all")).performScrollTo().performClick()
        compose.onNodeWithContentDescription("Show the history of: All devices").performScrollTo().performClick()
        compose.onNode(hasText("Pura Max") and hasAnyAncestor(isPopup())).performClick()
        compose.onNodeWithContentDescription("Show the cards of: All pets").performScrollTo().performClick()
        compose.onNode(hasText("Tigrou") and hasAnyAncestor(isPopup())).performClick()

        assertEquals(listOf("category:toileting", "category:null", "device:1002", "pet:2"), calls)
    }

    @Test
    fun `opened on a device, its picker names it and its chip shows selected`() {
        show(HistoryFixtures.filtered)

        compose.onNodeWithContentDescription("Show the history of: Purobot Max Pro 2").assertExists()
        compose.onNodeWithTag(HistoryTestTags.card(12)).assertIsDisplayed()
        assertTrue(HistoryFixtures.filtered.query.category == TimelineCategory.Toileting)
    }

    @Test
    fun `the end of the list asks for the next page`() {
        show(HistoryFixtures.state(paging = HistoryPaging(firstLoaded = true, next = null)))
        scrollTo("Pura Max · 1:00 · 6.1")
        compose.waitForIdle()

        assertTrue("more" in calls)
    }

    @Test
    fun `a page that failed under the list says so and retries`() {
        show(HistoryFixtures.offline)

        compose.onNodeWithText("Server unreachable. Showing the last known data.").assertExists()
        scrollTo("The next cards could not be loaded.")
        compose.onNodeWithContentDescription("Retry").performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `nothing cached and the server unreachable is an error with a retry`() {
        show(HistoryFixtures.failed)

        compose.onNodeWithText("The server cannot be reached.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `an empty day says so`() {
        show(HistoryFixtures.empty)

        compose.onNodeWithText("Nothing happened on this day.").assertIsDisplayed()
    }

    @Test
    fun `an empty day under filters says the filters`() {
        show(HistoryFixtures.empty.copy(query = HistoryFixtures.filtered.query))

        compose.onNodeWithText("Nothing on this day with these filters.").assertIsDisplayed()
    }

    @Test
    fun `while the first page loads over an empty cache, skeletons stand in`() {
        show(HistoryFixtures.loading)

        compose.onNodeWithContentDescription("Loading", substring = true).assertExists()
    }

    @Test
    fun `the sheet shows the event's named fields first, the others on demand, and its media`() {
        showSheet(HistoryFixtures.sheet)

        compose.onNodeWithText("Mochi").assertIsDisplayed()
        compose.onNodeWithText("Pet weight").assertExists()
        compose.onNodeWithText("4.31 kg").assertExists()
        compose.onNodeWithText("Cat vocalised").assertExists()
        compose.onNodeWithText("Yes").assertExists()
        compose.onNodeWithText("AES key").assertDoesNotExist()
        compose.onNodeWithText("Show 2 more fields").performClick()
        compose.onNodeWithText("AES key").assertExists()

        compose.onNodeWithContentDescription("Waste check").performClick()
        compose.onNodeWithContentDescription("Full recording").performClick()
        compose.onNodeWithText("Close").performClick()

        assertEquals(listOf("photo:Waste", "clip:Playback", "close"), calls)
    }

    @Test
    fun `a sheet whose event failed to load says so and retries, and a recording on its way is told`() {
        val pending = HistoryFixtures.entries().first { it.item.id == 16L }
        showSheet(EventSheetState(pending, loading = false, error = DomainError.Server(503, null, null)))

        compose.onNodeWithText("The recording is still being put together.").assertExists()
        compose.onNodeWithText("The details of this event could not be loaded.").assertExists()
        compose.onNodeWithText("Retry").performClick()

        assertEquals(listOf("retry-event"), calls)
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h2400dp")
    fun `in French, the fields and their values are translated by key`() {
        showSheet(HistoryFixtures.sheet)

        compose.onNodeWithText("Poids de l’animal").assertExists()
        compose.onNodeWithText("4,31 kg").assertExists()
        compose.onNodeWithText("Miaulements entendus").assertExists()
        compose.onNodeWithText("Oui").assertExists()
    }

    @Test
    @Config(qualifiers = "fr-w411dp-h2400dp")
    fun `in French, the screen and its chips are translated`() {
        show(HistoryFixtures.state())
        compose.onNodeWithText("Historique").assertIsDisplayed()
        compose.onNodeWithText("Aujourd’hui").assertIsDisplayed()
        compose.onNodeWithTag(HistoryTestTags.filter("toileting")).assertExists()
        compose.onNodeWithText("Toilettes").assertExists()
    }
}
