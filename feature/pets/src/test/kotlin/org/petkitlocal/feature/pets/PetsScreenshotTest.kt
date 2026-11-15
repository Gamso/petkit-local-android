// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.pets

import androidx.compose.runtime.Composable
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
 * The pet screens in light/dark x EN/FR, the busiest state also at 130 % font scale: a pet with a
 * month of weights, six months of a pet added since, a pet never weighed, the server unreachable
 * with nothing cached, and the list. The golden images are in src/test/screenshots. Photos are not
 * fetched here: the pet's initial stands in.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone width. SDK 36: Robolectric's SDK 37 graphics capture some images blank.
@Config(qualifiers = "w360dp-h1400dp-xhdpi", sdk = [36])
class PetsScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun content() = capture("pet_content") { PetScreen(PetFixtures.content, PetCallbacks()) }

    @Test
    fun halfYear() = screen("pet_half_year") { PetScreen(PetFixtures.halfYear, PetCallbacks()) }

    @Test
    fun sparse() = screen("pet_sparse") { PetScreen(PetFixtures.sparse, PetCallbacks()) }

    @Test
    fun failed() = screen("pet_failed") { PetScreen(PetFixtures.failed, PetCallbacks()) }

    @Test
    fun list() = screen("pets_list") { PetsScreen(PetFixtures.list, onRefresh = {}, navigation = PetsNavigation()) }

    /** The other states at 100 % only: the busiest one carries the 130 % check. */
    private fun screen(name: String, content: @Composable () -> Unit) {
        if (variant.fontScale == 1f) capture(name, content)
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
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
