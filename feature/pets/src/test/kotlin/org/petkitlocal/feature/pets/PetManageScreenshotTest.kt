// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.pets

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.testing.ScreenshotVariant
import org.petkitlocal.feature.pets.edit.PetEditCallbacks
import org.petkitlocal.feature.pets.edit.PetEditScreen
import org.petkitlocal.feature.pets.edit.PetEditUiState
import org.petkitlocal.feature.pets.photos.CropImage
import org.petkitlocal.feature.pets.photos.PetPhotosCallbacks
import org.petkitlocal.feature.pets.photos.PetPhotosScreen
import org.petkitlocal.feature.pets.photos.PetPhotosUiState
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Managing pets (issue #98) in light/dark x EN/FR: the list with Add and the proxy-mode setting,
 * the editor (new, existing, with its problems, with no camera to offer), the photos (with what
 * each camera took in, at the limit, none and no camera), the tips, the crop and the review of the
 * server's warnings. The golden images are in src/test/screenshots. Photos are not fetched here:
 * their placeholders stand in; the crop shows a drawn stand-in photo.
 */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
// Phone width. SDK 36: Robolectric's SDK 37 graphics capture some images blank.
@Config(qualifiers = "w360dp-h1400dp-xhdpi", sdk = [36])
class PetManageScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun listInProxyMode() = capture("pets_list_proxy") {
        PetsScreen(PetFixtures.listInProxyMode, onRefresh = {}, navigation = PetsNavigation())
    }

    @Test
    fun listEmpty() = capture("pets_list_empty") {
        PetsScreen(PetFixtures.listEmpty, onRefresh = {}, navigation = PetsNavigation())
    }

    @Test
    fun editNew() = edit("pet_edit_new", PetFixtures.editNew)

    @Test
    fun editExisting() = edit("pet_edit_existing", PetFixtures.editMochi)

    @Test
    fun editProblems() = edit("pet_edit_problems", PetFixtures.editProblems)

    @Test
    fun editNoDevice() = edit("pet_edit_no_device", PetFixtures.editNoDevice)

    @Test
    fun photos() = photos("pet_photos", PetFixtures.photos)

    @Test
    fun photosAtLimit() = photos("pet_photos_at_limit", PetFixtures.photosAtLimit)

    @Test
    fun photosEmpty() = photos("pet_photos_empty", PetFixtures.photosEmpty)

    @Test
    fun tips() = photos("pet_photo_tips", PetFixtures.tips)

    @Test
    fun crop() = capture("pet_photo_crop") {
        val image = remember { standInPhoto() }
        PetPhotosScreen(PetFixtures.crop, CropImage.Ready(image), PetPhotosCallbacks())
    }

    @Test
    fun review() = photos("pet_photo_review", PetFixtures.review)

    private fun edit(name: String, state: PetEditUiState) = capture(name) { PetEditScreen(state, PetEditCallbacks()) }

    private fun photos(name: String, state: PetPhotosUiState) = capture(name) {
        PetPhotosScreen(state, CropImage.Loading, PetPhotosCallbacks())
    }

    private fun capture(name: String, content: @Composable () -> Unit) {
        captureRoboImage(
            filePath = "src/test/screenshots/${name}_$variant.png",
            roborazziComposeOptions = variant.options(),
        ) {
            PkTheme { content() }
        }
    }

    /** A landscape "photo" to crop: a gradient with a round shape off-centre, standing in for a head. */
    private fun standInPhoto(): ImageBitmap = ImageBitmap(PHOTO_WIDTH, PHOTO_HEIGHT).also { bitmap ->
        val canvas = Canvas(bitmap)
        val size = Size(PHOTO_WIDTH.toFloat(), PHOTO_HEIGHT.toFloat())
        val paint = Paint()
        paint.shader = null
        Brush.linearGradient(listOf(Color(0xFF6D8BA8), Color(0xFFE3D5C0)), Offset.Zero, Offset(size.width, size.height))
            .applyTo(size, paint, alpha = 1f)
        canvas.drawRect(0f, 0f, size.width, size.height, paint)
        canvas.drawCircle(
            Offset(size.width * 0.58f, size.height * 0.45f),
            size.height * 0.28f,
            Paint().apply {
                color =
                    Color(0xFFB07A3C)
            },
        )
    }

    companion object {
        private const val PHOTO_WIDTH = 800
        private const val PHOTO_HEIGHT = 600

        @JvmStatic
        @ParameterizedRobolectricTestRunner.Parameters(name = "{0}")
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.screens
    }
}
