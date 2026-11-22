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
import androidx.compose.ui.test.isEnabled
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.MockServer.Certificate
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.core.ui.LiveCardTestTags
import org.petkitlocal.core.ui.live.LiveVideoTestTags
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.device.common.DeviceTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.settings.SettingsTestTags

/**
 * A server behind HTTPS with a self-signed certificate (issue #88, SPEC.md §8), against the mock's
 * HTTPS port (`--tls-port`, https://10.0.2.2:8443), whose certificate the control API switches:
 * the sign-in shows the certificate's fingerprint, trusting it signs in and everything works over
 * it (the API, `/ws`, the notification socket, live video); signing out forgets it; a renewed
 * certificate is refused and asked about again; one for another host is refused with nothing to
 * trust.
 *
 * Each test starts on sign-in with no pin, because TestServerRule signs the app out, and the pin
 * goes with the session. The mock's reset alone did not: after the renewed certificate was
 * trusted, the reset put the mock back on its first one, which the pin then refused, so the app
 * never heard that its session had ended. Only a race let it hear the 4401 the reset sends down
 * the sockets before the mock cut their TLS connections; once it lost, the app stayed on Home under
 * the changed-certificate question and every later test waited for sign-in in vain (issue #111).
 */
@MockOnly("the mock's HTTPS port and its certificates, switched through the control API")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class CertificateTrustFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aSelfSignedServerShowsItsFingerprintAndTrustingItSignsInThenSigningOutForgetsIt() {
        signInOverHttps()
        waitFor(hasTestTag(SignInTestTags.CERTIFICATE))
        assertEquals(MockServer.sha256(Certificate.PRIMARY), fingerprint(SignInTestTags.CERTIFICATE_FINGERPRINT))
        assertEquals(0, TestServer.sessions().size) // nothing reached the server

        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)

        // Everything goes over the pinned certificate: the API (Home), /ws, the notification
        // socket, and live video.
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        waitUntil("the live updates and notification sockets") {
            mockCount("sockets") >= 1 && mockCount("notification_sockets") == 1
        }
        // The camera's page plays its live video in place, once played.
        openDevice(PUROBOT)
        waitFor(hasTestTag(LiveCardTestTags.PLAY))
        compose.onNodeWithTag(LiveCardTestTags.PLAY).performClick()
        waitFor(hasTestTag(LiveVideoTestTags.BADGE) and hasAnyAncestor(hasTestTag(LiveCardTestTags.CARD)))
        waitUntil("one live socket") { mockCount("live_sockets") == 1 }

        compose.onNodeWithContentDescription("Back").performClick()
        waitFor(hasTestTag(HomeTestTags.LIST))
        signOut()

        // Signed out, the certificate is forgotten: the same server asks again.
        signInOverHttps()
        waitFor(hasTestTag(SignInTestTags.CERTIFICATE))
        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).assertExists()
    }

    @Test
    fun aRenewedCertificateIsRefusedAndAskedAboutWithBothFingerprints() {
        signInOverHttps()
        waitFor(hasTestTag(SignInTestTags.TRUST_CERTIFICATE))
        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).performScrollTo().performClick()
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))

        MockServer.useCertificate(Certificate.RENEWED) // and the mock closes the connections open
        // The app checks its session when it comes back to the foreground; its sockets reconnect.
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)

        waitFor(hasTestTag(SignInTestTags.CERTIFICATE_CHANGE))
        assertEquals(MockServer.sha256(Certificate.RENEWED), fingerprint(SignInTestTags.CERTIFICATE_FINGERPRINT))
        assertEquals(MockServer.sha256(Certificate.PRIMARY), fingerprint(SignInTestTags.CERTIFICATE_PINNED_FINGERPRINT))

        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.CERTIFICATE_CHANGE), TIMEOUT_MS)

        // Still signed in, and connected again over the new certificate.
        compose.onNodeWithTag(SignInTestTags.SCREEN).assertDoesNotExist()
        waitUntil("the notification socket again", RECONNECT_TIMEOUT_MS) { mockCount("notification_sockets") == 1 }
        assertEquals(1, TestServer.sessions().size)
    }

    @Test
    fun aCertificateForAnotherHostIsRefusedWithNothingToTrust() {
        MockServer.useCertificate(Certificate.WRONG_HOST)

        signInOverHttps()

        waitFor(hasTestTag(SignInTestTags.CERTIFICATE) and hasText("is not for 10.0.2.2", substring = true))
        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).assertDoesNotExist()
        assertTrue(TestServer.sessions().isEmpty())
    }

    /** The sign-in form, filled with the mock's HTTPS address and sent. */
    private fun signInOverHttps() {
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput(TestServer.tlsAddress)
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput(TestServer.password)
        waitFor(hasTestTag(SignInTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
    }

    private fun signOut() {
        val settings = (hasText("Settings") or hasContentDescription("Settings")) and hasClickAction()
        waitFor(settings)
        compose.onAllNodes(settings).onLast().performScrollTo().performClick()
        waitFor(hasTestTag(SettingsTestTags.SCREEN))
        compose.onNodeWithTag(SettingsTestTags.SIGN_OUT).performScrollTo().performClick()
        compose.onAllNodes(hasText("Sign out") and hasClickAction()).onLast().performClick()
        waitFor(hasTestTag(SignInTestTags.SCREEN))
    }

    /** From Home to [deviceId]'s page. */
    private fun openDevice(deviceId: Long) {
        val card = hasTestTag(HomeTestTags.device(deviceId))
        compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(card)
        compose.onNode(card).performClick()
        waitFor(hasTestTag(DeviceTestTags.STATUS))
    }

    /** The fingerprint a detail row shows, without the zero-width spaces that let it wrap. */
    private fun fingerprint(tag: String): String {
        val texts = compose.onNodeWithTag(tag).fetchSemanticsNode().config.getOrNull(SemanticsProperties.Text)
        return texts.orEmpty().last().text.replace("​", "")
    }

    /** One of the counts `GET /mock` reports. */
    private fun mockCount(name: String): Int = JSONObject(MockServer.control("GET", "")).getInt(name)

    private fun waitUntil(what: String, timeoutMs: Long = TIMEOUT_MS, condition: () -> Boolean) {
        try {
            compose.waitUntil(timeoutMs) { condition() }
        } catch (e: AssertionError) {
            throw AssertionError("Waited ${timeoutMs / MS} s for $what", e)
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val FIRST_CARD = 1001L
        const val PUROBOT = 1003L
        const val TIMEOUT_MS = 15_000L

        /** The notification socket's backoff, after the connection the mock closed, is capped at 30 s. */
        const val RECONNECT_TIMEOUT_MS = 45_000L
        const val MS = 1000
    }
}
