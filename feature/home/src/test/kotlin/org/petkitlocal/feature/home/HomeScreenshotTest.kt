// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.home

import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Home in light/dark x EN/FR x 100 %/130 % font scale (docs/DESIGN.md), with every family of the
 * mock household on screen, offline over the last known data, with nothing to show, and with the
 * generations of a codename side by side. The
 * golden images are in src/test/screenshots; `./gradlew recordRoborazziDebug` re-records them.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// A phone-width window tall enough for the whole household. SDK 36 for the same reason as
// ComponentScreenshotTest: Robolectric's SDK 37 graphics capture some images blank.
@Config(qualifiers = "w360dp-h2400dp-xhdpi", sdk = [36])
class HomeScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun content() = capture("home_content", HomeFixtures.content)

    @Test
    fun offline() {
        if (variant.fontScale == 1f) capture("home_offline", HomeFixtures.offline)
    }

    @Test
    fun unreachable() {
        if (variant.fontScale == 1f) capture("home_unreachable", HomeFixtures.unreachableEmpty)
    }

    /** Each generation under its own name and photo, a generation without one under its glyph. */
    @Test
    @Config(qualifiers = "w360dp-h900dp-xhdpi", sdk = [36])
    fun generations() {
        if (variant.fontScale == 1f) capture("home_generations", HomeFixtures.generations)
    }

    @Test
    fun notificationsBlocked() {
        if (variant.fontScale == 1f) capture("home_notifications_blocked", HomeFixtures.notificationsBlocked)
    }

    private fun capture(name: String, state: HomeUiState) {
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme { HomeScreen(state, onRefresh = {}, navigation = HomeNavigation()) }
        }
    }

    companion object {
        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.all
    }
}
