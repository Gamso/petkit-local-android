package org.petkitlocal.feature.auth

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import kotlin.time.Duration.Companion.seconds
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.AddressProblem
import org.petkitlocal.core.domain.auth.SignInFailure
import org.petkitlocal.core.testing.FakeServerTrustRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Compose UI tests (Robolectric): each state shows its message in place, and every intent arrives. */
@RunWith(RobolectricTestRunner::class)
class SignInScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()
    private val actions = SignInActions(
        onAddressChange = { calls += "address:$it" },
        onPasswordChange = { calls += "password:$it" },
        onPhoneNameChange = { calls += "name:$it" },
        onTogglePasswordVisibility = { calls += "toggle" },
        onTogglePhoneName = { calls += "phone name" },
        onSubmit = { calls += "submit" },
        onAllowLocalNetwork = { calls += "allow local network" },
        onOpenAppSettings = { calls += "app settings" },
        onTrustCertificate = { calls += "trust certificate" },
    )
    private val filled = SignInUiState(address = "https://pets.example.com", password = "demo", phoneName = "Pixel 8")

    private fun show(state: SignInUiState) = compose.setContent { PkTheme { SignInScreen(state, actions) } }

    @Test
    fun `typing and tapping reach their callbacks`() {
        show(SignInUiState(phoneNameExpanded = true))

        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput("pets.example.com")
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput("demo")
        compose.onNodeWithTag(SignInTestTags.PHONE_NAME).performScrollTo().performTextInput("Pixel")
        compose.onNodeWithContentDescription("Show password").performClick()

        // The screen is stateless: a field the state does not update reports its reset too.
        assertEquals(
            listOf("address:pets.example.com", "password:demo", "name:Pixel", "toggle"),
            calls.filterNot { it.endsWith(":") },
        )
    }

    @Test
    fun `the welcome names the app, as the screen's heading`() {
        show(SignInUiState())
        compose.onNode(hasText("Petkit Local") and isHeading()).assertExists()
        compose.onNodeWithText("Your pets' devices, on your own server.").assertExists()
    }

    @Test
    fun `an empty form can still be sent, so it can say what is missing`() {
        show(SignInUiState())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().assertIsEnabled().performClick()
        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `a missing password is said under the password`() {
        show(filled.copy(password = "", failure = SignInFailure.PasswordMissing))
        compose.onNodeWithText("Enter the password.").assertExists()
    }

    @Test
    fun `the phone's name is one line, which opens to the field`() {
        var state by mutableStateOf(filled)
        compose.setContent { PkTheme { SignInScreen(state, actions) } }

        compose.onNodeWithText("Phone name: Pixel 8").assertExists()
        compose.onNodeWithTag(SignInTestTags.PHONE_NAME).assertDoesNotExist()
        compose.onNodeWithTag(SignInTestTags.PHONE_NAME_ROW).performScrollTo().performClick()
        assertEquals(listOf("phone name"), calls)

        state = state.copy(phoneNameExpanded = true)
        compose.onNodeWithTag(SignInTestTags.PHONE_NAME).performScrollTo().assertExists()
        compose.onNodeWithText("Shown in the panel's list of signed-in phones.").assertExists()
    }

    @Test
    fun `the eye says what it will do`() {
        var state by mutableStateOf(filled)
        compose.setContent { PkTheme { SignInScreen(state, actions) } }
        compose.onNodeWithContentDescription("Show password").assertExists()
        state = state.copy(passwordVisible = true)
        compose.onNodeWithContentDescription("Hide password").assertExists()
    }

    @Test
    fun `while a request is in flight the button waits`() {
        show(filled.copy(submitting = true))
        compose.onNodeWithTag(SignInTestTags.SUBMIT).assertIsNotEnabled().assertTextContains("Signing in…")
    }

    @Test
    fun `a complete form is sent`() {
        show(filled)
        compose.onNodeWithTag(SignInTestTags.SUBMIT).assertIsEnabled().performClick()
        assertEquals(listOf("submit"), calls)
    }

    @Test
    fun `an address problem is said under the address`() {
        show(filled.copy(failure = SignInFailure.InvalidAddress(AddressProblem.HttpsRequired)))
        compose.onNodeWithText("HTTPS is required outside your home network.").assertExists()
    }

    @Test
    fun `plain HTTP on the LAN is warned about`() {
        show(filled.copy(address = "http://192.168.1.20:8097", plainHttpWarning = true))
        compose.onNodeWithText("Plain HTTP", substring = true).assertExists()
    }

    @Test
    fun `a wrong password is said under the password, and the button counts down`() {
        show(filled.copy(failure = SignInFailure.WrongPassword(2.seconds), lockedSeconds = 2))

        compose.onNodeWithText("Wrong password.").assertExists()
        compose.onNodeWithTag(SignInTestTags.SUBMIT).assertIsNotEnabled().assertTextContains("Try again in 2 seconds")
    }

    @Test
    fun `one second left is singular`() {
        show(filled.copy(failure = SignInFailure.LockedOut(1.seconds), lockedSeconds = 1))
        compose.onNodeWithTag(SignInTestTags.SUBMIT).assertTextContains("Try again in 1 second")
    }

    @Test
    fun `each server-side failure has its own message`() {
        val messages = mapOf(
            SignInFailure.NotPetkitLocal to "No petkit-local server answers",
            SignInFailure.PasswordNotSet to "Phone app access",
            SignInFailure.IncompatibleServer(2) to "version 2",
            SignInFailure.LockedOut(null) to "Too many wrong passwords",
            SignInFailure.Unreachable("timeout") to "cannot be reached",
            SignInFailure.ServerError(503, null) to "HTTP 503",
            SignInFailure.ServerError(null, "password too long") to "password too long",
        )
        var state by mutableStateOf<SignInFailure?>(null)
        compose.setContent { PkTheme { SignInScreen(filled.copy(failure = state), actions) } }
        messages.forEach { (failure, text) ->
            state = failure
            compose.onNodeWithTag(SignInTestTags.ERROR).assertTextContains(text, substring = true)
        }
    }

    @Test
    fun `local network access is asked for with its reason, and a refusal points to the settings`() {
        var state by mutableStateOf(filled.copy(address = "http://10.0.2.2:8097", localNetworkRationale = true))
        compose.setContent { PkTheme { SignInScreen(state, actions) } }

        compose.onNodeWithTag(SignInTestTags.LOCAL_NETWORK).assertTextContains("home network", substring = true)
        compose.onNodeWithText("Allow local network access").performScrollTo().performClick()

        state = state.copy(localNetworkRationale = false, failure = SignInFailure.LocalNetworkDenied)
        compose.onNodeWithTag(
            SignInTestTags.ERROR,
        ).assertTextContains("Local network access was refused", substring = true)
        compose.onNodeWithText("Open Android settings").performScrollTo().performClick()

        assertEquals(listOf("allow local network", "app settings"), calls)
    }

    @Test
    fun `a refused certificate shows what to check, and trusting it is one tap`() {
        show(filled.copy(failure = SignInFailure.Certificate(FakeServerTrustRepository.UNTRUSTED)))

        compose.onNodeWithTag(SignInTestTags.CERTIFICATE)
            .assertTextContains("not signed by an authority this phone trusts", substring = true)
        compose.onNodeWithTag(SignInTestTags.CERTIFICATE_FINGERPRINT)
            .assertTextContains(breakableFingerprint(FakeServerTrustRepository.CERTIFICATE.sha256), substring = true)
        compose.onNodeWithText("CN=petkit.home.lan", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("petkit.home.lan", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(SignInTestTags.ERROR).assertDoesNotExist()
        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).performScrollTo()
            .assertTextContains("Trust this certificate").performClick()

        assertEquals(listOf("trust certificate"), calls)
    }

    @Test
    fun `a changed certificate shows both fingerprints and offers the new one`() {
        show(filled.copy(failure = SignInFailure.Certificate(FakeServerTrustRepository.CHANGED)))

        compose.onNodeWithTag(SignInTestTags.CERTIFICATE_FINGERPRINT)
            .assertTextContains(breakableFingerprint(FakeServerTrustRepository.RENEWED.sha256), substring = true)
        compose.onNodeWithTag(SignInTestTags.CERTIFICATE_PINNED_FINGERPRINT)
            .assertTextContains(breakableFingerprint(FakeServerTrustRepository.CERTIFICATE.sha256), substring = true)
        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).assertTextContains("Trust the new certificate")
    }

    @Test
    fun `a certificate for another host says so and offers nothing`() {
        show(filled.copy(failure = SignInFailure.Certificate(FakeServerTrustRepository.WRONG_HOST)))

        compose.onNodeWithTag(SignInTestTags.CERTIFICATE)
            .assertTextContains("This certificate is not for 192.168.1.20", substring = true)
        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).assertDoesNotExist()
    }

    @Test
    fun `the changed-certificate dialog trusts or puts off`() {
        compose.setContent {
            PkTheme {
                CertificateChangeContent(
                    FakeServerTrustRepository.CHANGED,
                    onTrust = { calls += "trust" },
                    onNotNow = { calls += "not now" },
                )
            }
        }

        compose.onNode(hasText("Server certificate changed") and isHeading()).assertExists()
        compose.onNodeWithTag(SignInTestTags.CERTIFICATE_PINNED_FINGERPRINT)
            .assertTextContains(breakableFingerprint(FakeServerTrustRepository.CERTIFICATE.sha256), substring = true)
        compose.onNodeWithTag(SignInTestTags.TRUST_CERTIFICATE).performScrollTo().performClick()
        compose.onNodeWithTag(SignInTestTags.CERTIFICATE_NOT_NOW).performScrollTo().performClick()

        assertEquals(listOf("trust", "not now"), calls)
    }

    @Test
    fun `an ended session is explained`() {
        show(SignInUiState(sessionEnded = SessionEndReason.PasswordRemoved))
        compose.onNodeWithTag(SignInTestTags.SESSION_ENDED)
            .assertTextContains("the password was removed in the panel", substring = true)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `everything is in French, plurals included`() {
        show(filled.copy(failure = SignInFailure.WrongPassword(3.seconds), lockedSeconds = 3))

        compose.onNodeWithText("Les appareils de vos animaux, sur votre propre serveur.").assertExists()
        compose.onNodeWithText("Mot de passe incorrect.").assertExists()
        compose.onNodeWithText("Nom du téléphone : Pixel 8").assertExists()
        compose.onNodeWithContentDescription("Afficher le mot de passe").assertExists()
        compose.onNodeWithTag(SignInTestTags.SUBMIT).assertTextContains("Réessayer dans 3 secondes")
    }
}
