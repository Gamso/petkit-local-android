package org.petkitlocal.android

import android.app.NotificationManager
import android.service.notification.StatusBarNotification
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.core.app.NotificationCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.history.HistoryTestTags

/**
 * Notifications end to end (issue #23), on a device, against the mock server's notification
 * socket (core:e2e): signed in, the service holds the socket; an event triggered through `/mock`
 * is posted as an Android notification in the phone's language, on its kind's channel, and
 * tapping it opens the right screen. A socket the server drops is reopened with the last id, and
 * what was already shown is not shown again.
 */
@MockOnly("events made through the mock's control API")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class NotificationFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager = context.getSystemService(NotificationManager::class.java)

    @Test
    fun anEventIsNotifiedAndItsNotificationOpensItsPage() {
        // TestServerRule granted Android 13's notification permission, as a user would.
        manager.cancelAll()
        signIn()
        waitForSocket()

        // A visit by Mochi on the Pura Max: a toilet notification, which opens that day's History.
        MockServer.control("POST", "devices/$PURA_MAX/visit", """{"pet_id":$MOCHI}""")
        val visit = waitForAlert("kind_toilet")
        assertEquals("Mochi used the litter box", visit.notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals("Pura Max", visit.notification.extras.getString(NotificationCompat.EXTRA_TEXT))
        visit.notification.contentIntent.send()
        waitFor(hasTestTag(HistoryTestTags.SCREEN))
        waitFor(hasTestTag(HistoryTestTags.day(LocalDate.now())))

        // The server drops the socket: the service reconnects with the last id. A device going
        // offline then is notified once, and the visit is not posted again.
        MockServer.control("POST", "notifications/close", """{"code":1013}""")
        waitForSocket()
        MockServer.control("POST", "devices/$FEEDER/offline")
        val offline = waitForAlert("kind_offline")
        assertEquals("Device offline", offline.notification.extras.getString(NotificationCompat.EXTRA_TITLE))
        assertEquals(1, alerts().count { it.notification.channelId == "kind_toilet" })

        // A condition opens the device's page: the feeder's, which says it is offline.
        offline.notification.contentIntent.send()
        waitFor(hasTestTag(DeviceTestTags.SCREEN))
        waitFor(hasText("Offline") and hasAnyAncestor(hasTestTag(DeviceTestTags.STATUS)))
    }

    private fun alerts(): List<StatusBarNotification> = manager.activeNotifications.filter { it.tag == ALERT_TAG }

    private fun waitForAlert(channel: String): StatusBarNotification {
        var found: StatusBarNotification? = null
        compose.waitUntil(TIMEOUT_MS) {
            found = alerts().lastOrNull { it.notification.channelId == channel }
            found != null
        }
        return found!!
    }

    /** The mock holds this phone's notification socket. */
    private fun waitForSocket() {
        compose.waitUntil(TIMEOUT_MS) { MockServer.control("GET", "").contains("\"notification_sockets\":1") }
    }

    private fun signIn() {
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        type(SignInTestTags.ADDRESS, TestServer.address)
        type(SignInTestTags.PASSWORD, TestServer.password)
        waitFor(hasTestTag(SignInTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
        assertTrue(MockServer.control("GET", "sessions").contains("\"id\""))
    }

    private fun type(tag: String, text: String) {
        compose.onNodeWithTag(tag).performTextClearance()
        compose.onNodeWithTag(tag).performTextInput(text)
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val PURA_MAX = 1002
        const val FEEDER = 2001
        const val MOCHI = 1
        const val ALERT_TAG = "alert"
        const val TIMEOUT_MS = 30_000L
    }
}
