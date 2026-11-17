package org.petkitlocal.android

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
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
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.history.HistoryTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.pets.PetsTestTags

/**
 * The pet screens end to end, against the mock server: Home's pet chip opens that pet's page,
 * read from `GET /pets/{id}` with its weighed visits from the timeline; the switcher moves to the
 * other pet; a recent visit opens History narrowed to that pet; Home's "See all" opens the list.
 */
@MockOnly("the mock's pets and their visits")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class PetsFlowTest {
    /** The mock cuts days in its own time zone; the phone shows the same days in the same one. */
    @get:Rule(order = 0)
    val zone = DevicePagesFlowTest.TimeZoneRule(MOCK_ZONE)

    @get:Rule(order = 1)
    val server = TestServerRule()

    @get:Rule(order = 2)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aPetChipOpensItsPageTheSwitcherMovesAndAVisitOpensItsHistory() {
        val tigrouVisit = runBlocking { TestServer.signedInApi().getPet(TIGROU).pet.recentVisits.first() }

        signIn()
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val chip = hasTestTag(HomeTestTags.pet(MOCHI))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(chip)
        compose.onNode(chip).performClick()

        waitFor(hasTestTag(PetsTestTags.SCREEN))
        // Mochi has reference faces and a learned weight (mock-server seed).
        waitFor(hasText("Recognition: face and weight"))
        waitFor(hasText("learned from its visits", substring = true))
        // Her weighed visits, read from the timeline, draw the chart.
        scrollUntilShown(hasTestTag(PetsTestTags.CHART))

        scrollUntilShown(hasTestTag(PetsTestTags.switch(TIGROU)))
        compose.onNodeWithTag(PetsTestTags.switch(TIGROU)).performClick()
        waitFor(hasText("Recognition: weight"))
        val visit = hasTestTag(PetsTestTags.visit(tigrouVisit.id))
        scrollUntilShown(visit)
        compose.onNode(visit).performSemanticsAction(SemanticsActions.OnClick)

        waitFor(hasTestTag(HistoryTestTags.SCREEN))
        waitFor(hasContentDescription("Show the cards of: Tigrou"))
        waitFor(hasTestTag(HistoryTestTags.card(tigrouVisit.id)))
    }

    @Test
    fun seeAllOpensTheListAndACardOpensThatPet() {
        signIn()
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val seeAll = hasText("See all")
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(seeAll)
        compose.onNode(seeAll).performClick()

        waitFor(hasTestTag(PetsTestTags.LIST_SCREEN))
        waitFor(hasTestTag(PetsTestTags.card(TIGROU)))
        compose.onNodeWithTag(PetsTestTags.card(TIGROU)).performClick()

        waitFor(hasTestTag(PetsTestTags.SCREEN))
        waitFor(hasText("Recognition: weight"))
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

    /** Scrolls the pet page until [matcher] shows up, as the server's answers arrive. */
    private fun scrollUntilShown(matcher: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(PetsTestTags.PAGE).performScrollToNode(matcher) }.isSuccess
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val FIRST_CARD = 1001L
        const val MOCHI = 1L
        const val TIGROU = 2L
        const val MOCK_ZONE = "Europe/Paris"
        const val TIMEOUT_MS = 30_000L
    }
}
