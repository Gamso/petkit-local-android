package org.petkitlocal.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.component.PkConfirmTestTags
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.MockServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.device.fountain.FountainTestTags
import org.petkitlocal.feature.device.litter.PageTestTags
import org.petkitlocal.feature.home.HomeTestTags

/**
 * The fountain page end to end, against the mock server: the EverSweet Ultra AI's pump follows a
 * button over `/ws` and a new drink reaches its pet's count; the EverSweet 3 Pro, relayed by the
 * YumShare Dual-Hopper (MQTT), names its relay and takes a confirmed filter reset, its filter
 * pill following the server.
 */
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class FountainFlowTest {
    @get:Rule(order = 0)
    val mock = MockServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun theUltraAiFollowsItsPumpAndItsDrinksLive() {
        signIn()
        open(ULTRA_AI)
        waitFor(hasText("EverSweet Ultra AI"))
        waitFor(hasContentDescription("Pump, Running"))

        // Reset Circulation Pump can be undone: behind More but sent at once. The mock stops the
        // pump for a few seconds, and the pill follows both reports.
        compose.onNodeWithTag(DeviceTestTags.SHEET_MORE).performClick()
        compose.onNode(
            hasText("Reset Circulation Pump") and hasAnyAncestor(hasTestTag(DeviceTestTags.ACTION_SHEET)),
        ).performClick()
        waitFor(hasTestTag(DeviceTestTags.FEEDBACK) and hasText("Sent to the device."))
        waitFor(hasContentDescription("Pump, Stopped"))
        waitFor(hasContentDescription("Pump, Running"))

        // Mochi drinks: the camera recognises her, and her count of the day goes up.
        val mochi = hasTestTag(FountainTestTags.pet(MOCHI))
        compose.onNodeWithTag(PageTestTags.LIST).performScrollToNode(mochi)
        val before = drinksOf(mochi)
        MockServer.control("POST", "devices/$ULTRA_AI/drink", """{"pet_id": $MOCHI, "duration_sec": 20}""")
        compose.waitUntil(TIMEOUT_MS) { drinksOf(mochi) > before }
    }

    @Test
    fun aBluetoothFountainNamesItsRelayAndTakesAConfirmedFilterReset() {
        signIn()
        open(EVERSWEET_3_PRO)
        waitFor(hasText("Reports through YumShare Dual-Hopper"))
        waitFor(hasContentDescription("Water level, OK"))

        // The filter's pill explains it and offers its reset, which cannot be undone.
        val filter = hasContentDescription("Filter, ", substring = true)
        waitFor(filter)
        compose.onNode(filter).performClick()
        compose.onNode(hasText("Reset Filter") and hasAnyAncestor(isDialog())).performClick()
        waitFor(hasText("Reset Filter?"))
        // The confirm button takes taps once the dialog has been open a moment.
        val confirm = hasTestTag(PkConfirmTestTags.CONFIRM) and hasText("Reset Filter")
        waitFor(confirm)
        compose.onNode(confirm).performClick()

        // The relay is on MQTT: sent, and the fountain's next report shows a new filter.
        waitFor(hasTestTag(DeviceTestTags.FEEDBACK) and hasText("Sent to the device."))
        waitFor(hasContentDescription("Filter, 100 %"))
    }

    /** The number of drinks a pet's row of the Today card says, "3 drinks · 1 yesterday". */
    private fun drinksOf(row: SemanticsMatcher): Int {
        val node = compose.onNode(row).fetchSemanticsNode()
        val text = node.config.getOrNull(SemanticsProperties.Text).orEmpty().joinToString(" ") { it.text }
        return Regex("""(\d+) drinks?""").find(text)?.groupValues?.get(1)?.toInt() ?: 0
    }

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
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput(MockServer.baseUrl.toString().removeSuffix("/"))
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput(MockServer.PASSWORD)
        waitFor(hasTestTag(SignInTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val FIRST_CARD = 1001L
        const val ULTRA_AI = 3001L
        const val EVERSWEET_3_PRO = 3002L
        const val MOCHI = 1L

        // A page's first refresh reads every device's detail and two days of the timeline.
        const val TIMEOUT_MS = 30_000L
    }
}
