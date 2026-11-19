// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import kotlin.time.Duration.Companion.seconds
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.SessionEndReason
import org.petkitlocal.core.domain.auth.SignInFailure
import org.petkitlocal.core.testing.FakeServerTrustRepository
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The sign-in screen in light/dark x EN/FR: the form as it starts (phone name folded), with the
 * phone name open, and with what goes wrong (an empty password pressed, an ended session, a
 * plain-HTTP warning, a wrong password counting down, a server error, local network access, a
 * refused certificate: untrusted, changed, for another host), and the changed-certificate
 * dialog. The busiest states are also taken at 130 % font scale, where the screen no longer fits and
 * scrolls from the top instead of being centred. SDK 36 for the reason given in
 * core:designsystem's ComponentScreenshotTest.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [36])
class SignInScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun form() = capture("sign_in_form") {
        SignInScreen(
            SignInUiState(address = "https://pets.example.com", phoneName = "Google Pixel 8"),
            SignInActions(),
        )
    }

    @Test
    fun errors() = capture("sign_in_errors", largeFontToo = true) {
        SignInScreen(
            SignInUiState(
                address = "http://192.168.1.20:8097",
                password = "secret",
                phoneName = "Google Pixel 8",
                plainHttpWarning = true,
                failure = SignInFailure.WrongPassword(4.seconds),
                lockedSeconds = 4,
                sessionEnded = SessionEndReason.InvalidToken,
            ),
            SignInActions(),
        )
    }

    @Test
    fun serverErrors() = capture("sign_in_server_error") {
        SignInScreen(
            SignInUiState(
                address = "https://pets.example.com",
                password = "secret",
                phoneName = "Google Pixel 8",
                failure = SignInFailure.PasswordNotSet,
            ),
            SignInActions(),
        )
    }

    @Test
    fun emptyFieldsPressed() = capture("sign_in_missing_password") {
        SignInScreen(
            SignInUiState(
                address = "https://pets.example.com",
                phoneName = "Google Pixel 8",
                failure = SignInFailure.PasswordMissing,
            ),
            SignInActions(),
        )
    }

    @Test
    fun phoneNameOpen() = capture("sign_in_phone_name_open") {
        SignInScreen(
            SignInUiState(
                address = "https://pets.example.com",
                password = "secret",
                phoneName = "Google Pixel 8",
                phoneNameExpanded = true,
            ),
            SignInActions(),
        )
    }

    @Test
    fun localNetwork() = capture("sign_in_local_network") {
        SignInScreen(
            SignInUiState(
                address = "http://192.168.1.20:8097",
                password = "secret",
                phoneName = "Google Pixel 8",
                plainHttpWarning = true,
                localNetworkRationale = true,
            ),
            SignInActions(),
        )
    }

    @Test
    fun untrustedCertificate() = capture("sign_in_certificate_untrusted", largeFontToo = true) {
        SignInScreen(
            SignInUiState(
                address = "https://petkit.home.lan",
                password = "secret",
                phoneName = "Google Pixel 8",
                failure = SignInFailure.Certificate(FakeServerTrustRepository.UNTRUSTED),
            ),
            SignInActions(),
        )
    }

    @Test
    fun changedCertificate() = capture("sign_in_certificate_changed") {
        SignInScreen(
            SignInUiState(
                address = "https://petkit.home.lan",
                password = "secret",
                phoneName = "Google Pixel 8",
                failure = SignInFailure.Certificate(FakeServerTrustRepository.CHANGED),
            ),
            SignInActions(),
        )
    }

    @Test
    fun certificateForAnotherHost() = capture("sign_in_certificate_wrong_host") {
        SignInScreen(
            SignInUiState(
                address = "https://192.168.1.20",
                password = "secret",
                phoneName = "Google Pixel 8",
                failure = SignInFailure.Certificate(FakeServerTrustRepository.WRONG_HOST),
            ),
            SignInActions(),
        )
    }

    /** The question asked over the signed-in app, as its dialog shows it (without the window's scrim). */
    @Test
    fun certificateChange() = capture("certificate_change", largeFontToo = true) {
        Box(Modifier.fillMaxSize().background(PkTheme.colors.background).padding(24.dp)) {
            CertificateChangeContent(FakeServerTrustRepository.CHANGED, onTrust = {}, onNotNow = {})
        }
    }

    /** Every state at 100 %; only the busiest ones at 130 % too. */
    private fun capture(name: String, largeFontToo: Boolean = false, content: @Composable () -> Unit) {
        assumeTrue(largeFontToo || variant.fontScale == 1f)
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme { content() }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}
