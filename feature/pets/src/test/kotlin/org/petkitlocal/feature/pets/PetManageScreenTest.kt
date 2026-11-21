package org.petkitlocal.feature.pets

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
import org.petkitlocal.core.domain.pets.PetsSource
import org.petkitlocal.feature.pets.edit.PetEditCallbacks
import org.petkitlocal.feature.pets.edit.PetEditScreen
import org.petkitlocal.feature.pets.edit.PetEditTestTags
import org.petkitlocal.feature.pets.photos.CropImage
import org.petkitlocal.feature.pets.photos.PetPhotosCallbacks
import org.petkitlocal.feature.pets.photos.PetPhotosScreen
import org.petkitlocal.feature.pets.photos.PetPhotosTestTags
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Compose UI test (Robolectric) of managing pets: the list's additions, the editor, the photos. */
@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "w411dp-h2400dp")
// The crop draws a real bitmap.
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class PetManageScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val calls = mutableListOf<String>()

    private val editCallbacks = PetEditCallbacks(
        onSave = { calls += "save" },
        onDelete = { calls += "delete" },
        onToggleDevice = { calls += "device:$it" },
        onConfirmDelete = { calls += "confirm" },
    )

    private val photoCallbacks = PetPhotosCallbacks(
        onAdd = { calls += "add" },
        onDelete = { calls += "delete:$it" },
        onTakePhoto = { calls += "camera" },
        onChoosePhoto = { calls += "gallery" },
        onKeep = { calls += "keep" },
        onRetake = { calls += "retake" },
        onEditPet = { calls += "edit" },
        onUseSquare = { calls += "square:${it.side}" },
    )

    @Test
    fun `the list offers to add a pet, and in proxy mode whose pets the cameras recognise`() {
        compose.setContent {
            PkTheme {
                PetsScreen(
                    PetFixtures.listInProxyMode,
                    onRefresh = {},
                    navigation = PetsNavigation(onAddPet = { calls += "add" }),
                    onPetsSource = { calls += "source:$it" },
                )
            }
        }

        compose.onNodeWithTag(PetsTestTags.LIST).performScrollToNode(hasTestTag(PetsTestTags.SOURCE))
        compose.onNodeWithText("With PetKit, the pets and photos edited here are not used for recognition.")
            .assertIsDisplayed()
        compose.onNodeWithTag(PetsTestTags.source("petkit")).assertIsSelected()
        compose.onNodeWithTag(PetsTestTags.source("local")).performClick()
        compose.onNodeWithTag(PetsTestTags.LIST).performScrollToNode(hasTestTag(PetsTestTags.ADD))
        compose.onNodeWithTag(PetsTestTags.ADD).performClick()

        assertEquals(listOf("source:${PetsSource.Local}", "add"), calls)
    }

    @Test
    fun `outside proxy mode the setting is not shown`() {
        compose.setContent { PkTheme { PetsScreen(PetFixtures.list, onRefresh = {}, navigation = PetsNavigation()) } }

        compose.onNodeWithTag(PetsTestTags.SOURCE).assertDoesNotExist()
    }

    @Test
    fun `the editor offers the cameras, each a checkbox, and Save stays enabled with problems`() {
        compose.setContent { PkTheme { PetEditScreen(PetFixtures.editProblems, editCallbacks) } }

        compose.onNodeWithText("Give it a name.").assertIsDisplayed()
        compose.onNodeWithText("Enter a weight above 0 and up to 100 kg.").assertIsDisplayed()
        compose.onNodeWithText("The server cannot be reached. Nothing was changed.").assertIsDisplayed()
        compose.onNodeWithTag(PetEditTestTags.device(1003)).assertIsOff().performClick()
        compose.onNodeWithTag(PetEditTestTags.SAVE).assertIsEnabled().performClick()
        compose.onNodeWithTag(PetEditTestTags.DELETE).assertDoesNotExist()

        assertEquals(listOf("device:1003", "save"), calls)
    }

    @Test
    fun `an existing pet can be deleted, after a confirmation`() {
        compose.setContent {
            PkTheme { PetEditScreen(PetFixtures.editMochi.copy(confirmingDelete = true), editCallbacks) }
        }

        compose.onNodeWithTag(PetEditTestTags.device(1003)).assertIsOn()
        compose.onNodeWithText("Delete Mochi?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()

        assertEquals(listOf("confirm"), calls)
    }

    @Test
    fun `each photo says which camera took it in, and Add at the limit stays pressable with the reason`() {
        compose.setContent { PkTheme { PetPhotosScreen(PetFixtures.photosAtLimit, CropImage.Loading, photoCallbacks) } }

        compose.onAllNodesWithText("Recognised by Purobot Max Pro 2").assertCountEquals(2)
        compose.onNodeWithText("Waiting for EverSweet Ultra AI").assertExists()
        compose.onNodeWithText("Mochi already has 2 photos", substring = true).assertExists()
        compose.onNodeWithTag(PetPhotosTestTags.ADD).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Delete photo 2").performClick()

        assertEquals(listOf("add", "delete:12"), calls)
    }

    @Test
    fun `a pet without a camera that recognises faces says so, with the way to choose them`() {
        compose.setContent { PkTheme { PetPhotosScreen(PetFixtures.photosEmpty, CropImage.Loading, photoCallbacks) } }

        compose.onNodeWithText("No camera that recognises faces is assigned to Tigrou", substring = true).assertExists()
        compose.onNodeWithText("Choose its cameras").performClick()

        assertEquals(listOf("edit"), calls)
    }

    @Test
    fun `the tips lead to the camera or the gallery`() {
        compose.setContent { PkTheme { PetPhotosScreen(PetFixtures.tips, CropImage.Loading, photoCallbacks) } }

        compose.onNodeWithText("Two pets").assertExists()
        compose.onNodeWithTag(PetPhotosTestTags.TIPS).performScrollToNode(hasTestTag(PetPhotosTestTags.TAKE))
        compose.onNodeWithTag(PetPhotosTestTags.TAKE).performClick()
        compose.onNodeWithTag(PetPhotosTestTags.CHOOSE).performClick()

        assertEquals(listOf("camera", "gallery"), calls)
    }

    @Test
    fun `the crop sends its square, and says why the last one was refused`() {
        compose.setContent {
            PkTheme { PetPhotosScreen(PetFixtures.crop, CropImage.Ready(ImageBitmap(800, 600)), photoCallbacks) }
        }

        compose.onNodeWithText("The square is 180 pixels wide", substring = true).assertExists()
        compose.onNodeWithTag(PetPhotosTestTags.CROP).performScrollToNode(hasTestTag(PetPhotosTestTags.USE))
        compose.onNodeWithTag(PetPhotosTestTags.USE).performClick()

        // The photo just covers the frame: the square is its full height.
        assertEquals(listOf("square:600"), calls)
    }

    @Test
    fun `the server's warnings are said in words, with Keep anyway and Retake`() {
        compose.setContent { PkTheme { PetPhotosScreen(PetFixtures.review, CropImage.Loading, photoCallbacks) } }

        compose.onNodeWithText("It looks blurred.").assertIsDisplayed()
        compose.onNodeWithText("It is too dark.").assertIsDisplayed()
        compose.onNodeWithTag(PetPhotosTestTags.KEEP).performClick()
        compose.onNodeWithTag(PetPhotosTestTags.RETAKE).performClick()

        assertEquals(listOf("keep", "retake"), calls)
    }
}
