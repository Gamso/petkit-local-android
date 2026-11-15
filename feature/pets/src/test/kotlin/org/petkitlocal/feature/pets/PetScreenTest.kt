package org.petkitlocal.feature.pets

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
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
import org.petkitlocal.core.domain.pets.WeightRange
import org.petkitlocal.core.testing.Household
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI test (Robolectric) of the stateless pets list and pet page. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
class PetScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val callbacks = PetCallbacks(
        onBack = { calls += "back" },
        onSelectPet = { calls += "pet:$it" },
        onRange = { calls += "range:$it" },
        onRetry = { calls += "retry" },
        onRetryWeights = { calls += "retry-weights" },
        onOpenHistory = { calls += "history:$it" },
    )

    private fun show(state: PetUiState) {
        compose.setContent { PkTheme { PetScreen(state, callbacks) } }
    }

    private fun scrollTo(text: String) {
        compose.onNodeWithTag(PetsTestTags.PAGE).performScrollToNode(hasText(text, substring = true))
    }

    @Test
    fun `the page says who the pet is, its weight, today's visits and its last visit`() {
        show(PetFixtures.content)

        compose.onNodeWithText("Recognition: face and weight").assertIsDisplayed()
        compose.onNodeWithText("Compared with 4.3\u00A0kg, learned from its visits").assertIsDisplayed()
        compose.onNodeWithText("4.3\u00A0kg").assertIsDisplayed()
        compose.onNodeWithText("Median of recent visits").assertIsDisplayed()
        compose.onNodeWithText("3").assertIsDisplayed()
        compose.onNodeWithText("Today · Pura Max").assertIsDisplayed()
        compose.onNodeWithTag(PetsTestTags.switch(1)).assertIsSelected()
    }

    @Test
    fun `the switcher, the range and the visits lead where they should`() {
        show(PetFixtures.content)

        compose.onNodeWithTag(PetsTestTags.switch(2)).performClick()
        scrollTo("6 months")
        compose.onNodeWithText("6 months").performClick()
        scrollTo("Recent visits")
        compose.onNodeWithTag(PetsTestTags.PAGE).performScrollToNode(hasText("See all in History"))
        compose.onNodeWithTag(PetsTestTags.visit(202)).performClick()
        compose.onNodeWithText("See all in History").performClick()
        compose.onNodeWithContentDescription("Back").performClick()

        val yesterday = Household.today.minusDays(1)
        assertEquals(
            listOf("pet:2", "range:${WeightRange.HalfYear}", "history:$yesterday", "history:null", "back"),
            calls,
        )
    }

    @Test
    fun `the chart says what a point is, and from when it has data`() {
        show(PetFixtures.halfYear)

        scrollTo("One point per week")
        compose.onNodeWithText("One point per week: the median of its weighed visits.").assertIsDisplayed()
        compose.onNodeWithText("No weighed visit before", substring = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Weight chart: 10 points", substring = true).assertIsDisplayed()
    }

    @Test
    fun `a pet never read today and never weighed says so rather than showing zeros`() {
        show(PetFixtures.sparse)

        compose.onNodeWithText("Recognition: weight").assertIsDisplayed()
        compose.onNodeWithText("Compared with 6.0\u00A0kg, the weight entered in petkit-local").assertIsDisplayed()
        compose.onNodeWithText("No weighed visit yet").assertIsDisplayed()
        compose.onNodeWithText("Not updated today").assertIsDisplayed()
        compose.onNodeWithText("No visit yet").assertIsDisplayed()
        scrollTo("No weighed visit in the last 30 days.")
        scrollTo("No visit in the last 7 days.")
    }

    @Test
    fun `weights that could not be read say so, with a retry`() {
        show(PetFixtures.sparse.copy(weightsError = DomainError.Network()))

        scrollTo("The visits could not be read.")
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retry-weights"), calls)
    }

    @Test
    fun `nothing cached and the server unreachable is the error view with its retry`() {
        show(PetFixtures.failed)
        compose.onNodeWithText("The server cannot be reached.").assertIsDisplayed()
        compose.onNodeWithText("Retry").performClick()
        assertEquals(listOf("retry"), calls)
    }

    @Test
    fun `a pet the server no longer has says so`() {
        show(PetFixtures.gone)
        compose.onNodeWithText("This pet is no longer on the server.").assertIsDisplayed()
    }

    @Test
    fun `the list shows each pet's weight and today's visits, and opens its page`() {
        val opened = mutableListOf<String>()
        compose.setContent {
            PkTheme {
                PetsScreen(
                    PetFixtures.list,
                    onRefresh = {},
                    navigation = PetsNavigation(onBack = { opened += "back" }, onOpenPet = { opened += "pet:$it" }),
                )
            }
        }

        compose.onNodeWithText("4.3\u00A0kg · 3 visits today").assertIsDisplayed()
        compose.onNodeWithText("6.0\u00A0kg · 1 visit today").assertIsDisplayed()
        compose.onNodeWithTag(PetsTestTags.card(2)).performClick()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("pet:2", "back"), opened)
    }

    @Test
    fun `an empty household says where pets are added`() {
        compose.setContent { PkTheme { PetsScreen(PetsUiState(pets = emptyList()), {}, PetsNavigation()) } }
        compose.onNodeWithText("No pets yet. Add them in petkit-local's panel.").assertIsDisplayed()
    }
}
