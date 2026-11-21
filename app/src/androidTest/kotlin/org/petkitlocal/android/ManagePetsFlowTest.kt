package org.petkitlocal.android

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasAnyAncestor
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
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.e2e.MockOnly
import org.petkitlocal.core.e2e.MockServer
import org.petkitlocal.core.e2e.TestServer
import org.petkitlocal.core.e2e.TestServerRule
import org.petkitlocal.core.network.dto.PetCreateRequestDto
import org.petkitlocal.feature.auth.SignInTestTags
import org.petkitlocal.feature.home.HomeTestTags
import org.petkitlocal.feature.pets.PetsTestTags
import org.petkitlocal.feature.pets.edit.PetEditTestTags
import org.petkitlocal.feature.pets.photos.PetPhotosTestTags

/**
 * Managing pets end to end, against the mock server (issue #98): a pet created, renamed and
 * deleted from the app; pets added and removed elsewhere arriving live (`/ws` `pet` and
 * `pet_removed`); the "Pets for recognition" setting in proxy mode; and Add at the photo limit,
 * which stays pressable and says why. Taking or choosing a photo needs the camera app or the
 * photo picker, which these tests do not drive: the crop and the upload are covered by the
 * ViewModel and Compose tests.
 */
@MockOnly("the mock's seeded pets, its proxy-mode control and its JPEG check")
@OptIn(ExperimentalTestApi::class) // waitUntil...Exists
@RunWith(AndroidJUnit4::class)
class ManagePetsFlowTest {
    @get:Rule(order = 0)
    val server = TestServerRule()

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun aPetIsCreatedRenamedAndDeleted() {
        signIn()
        openPetsList()

        scrollList(hasTestTag(PetsTestTags.ADD))
        compose.onNodeWithTag(PetsTestTags.ADD).performClick()
        waitFor(hasTestTag(PetEditTestTags.SCREEN))
        compose.onNodeWithTag(PetEditTestTags.NAME).performTextInput("Nala")
        compose.onNodeWithTag(PetEditTestTags.WEIGHT).performTextInput("3.9")
        // The T5 recognises faces (mock-server seed); the Pura Max does not, and is not offered.
        compose.onNodeWithTag(PetEditTestTags.device(T5)).performScrollTo().performClick()
        compose.onNodeWithTag(PetEditTestTags.device(PURA_MAX)).assertDoesNotExist()
        compose.onNodeWithTag(PetEditTestTags.SAVE).performScrollTo().performClick()

        // The new pet's page opens in the editor's place.
        waitFor(hasTestTag(PetsTestTags.SCREEN))
        waitFor(hasText("Nala"))
        val created = runBlocking { TestServer.signedInApi().listPets().pets.single { it.name == "Nala" } }
        check(created.deviceIds == listOf(T5)) { "Nala's devices: ${created.deviceIds}" }

        compose.onNodeWithTag(PetsTestTags.EDIT).performClick()
        waitFor(hasTestTag(PetEditTestTags.SCREEN))
        compose.onNodeWithTag(PetEditTestTags.NAME).performTextClearance()
        compose.onNodeWithTag(PetEditTestTags.NAME).performTextInput("Nala-chan")
        compose.onNodeWithTag(PetEditTestTags.SAVE).performScrollTo().performClick()
        waitFor(hasTestTag(PetsTestTags.SCREEN))
        waitFor(hasText("Nala-chan"))

        compose.onNodeWithTag(PetsTestTags.EDIT).performClick()
        waitFor(hasTestTag(PetEditTestTags.DELETE))
        compose.onNodeWithTag(PetEditTestTags.DELETE).performScrollTo().performClick()
        val confirm = hasText("Delete") and hasAnyAncestor(isDialog())
        waitFor(confirm)
        compose.onNode(confirm).performClick()

        // Back on the list, past the deleted pet's page.
        waitFor(hasTestTag(PetsTestTags.LIST_SCREEN))
        compose.waitUntilDoesNotExist(hasTestTag(PetsTestTags.card(created.id)), TIMEOUT_MS)
    }

    @Test
    fun petsAddedAndRemovedElsewhereArriveLive() {
        signIn()
        openPetsList()
        waitFor(hasTestTag(PetsTestTags.card(TIGROU)))

        val api = runBlocking { TestServer.signedInApi() }
        val created = runBlocking { api.createPet(PetCreateRequestDto("Pistache")).pet }
        waitFor(hasTestTag(PetsTestTags.card(created.id)))

        runBlocking { api.deletePet(created.id) }
        compose.waitUntilDoesNotExist(hasTestTag(PetsTestTags.card(created.id)), TIMEOUT_MS)
    }

    @Test
    fun inProxyModeTheListOffersWhosePetsTheCamerasRecognise() {
        MockServer.control("PUT", "proxy", """{"proxy_mode":true}""")
        signIn()
        openPetsList()

        scrollList(hasTestTag(PetsTestTags.SOURCE))
        waitFor(hasText("With PetKit, the pets and photos edited here are not used for recognition."))
        compose.onNodeWithTag(PetsTestTags.source("local")).performClick()
        waitFor(hasTestTag(PetsTestTags.source("local")) and isSelectedNode())
        compose.onNodeWithTag(PetsTestTags.source("local")).assertIsSelected()
        val chosen = runBlocking { TestServer.signedInApi().getPetsSource().petsSource }
        check(chosen == "local") { "pets_source is $chosen" }
    }

    @Test
    fun addAtTheLimitStaysPressableAndSaysWhy() {
        // Mochi has two photos (mock-server seed); four more make the six a pet may have.
        runBlocking {
            val api = TestServer.signedInApi()
            repeat(4) { api.addPetFace(MOCHI, JPEG.toRequestBody("image/jpeg".toMediaType())) }
        }
        signIn()
        openPetsList()
        compose.onNodeWithTag(PetsTestTags.card(MOCHI)).performClick()
        waitFor(hasTestTag(PetsTestTags.PHOTOS))
        compose.onNodeWithTag(PetsTestTags.PAGE).performScrollToNode(hasTestTag(PetsTestTags.PHOTOS))
        compose.onNodeWithTag(PetsTestTags.PHOTOS).performClick()

        waitFor(hasText("6 photos at most."))
        compose.onNodeWithTag(PetPhotosTestTags.ADD).performScrollTo().performClick()
        waitFor(hasText("Mochi already has 6 photos", substring = true))
        compose.onNodeWithTag(PetPhotosTestTags.TIPS).assertDoesNotExist()
    }

    private fun isSelectedNode(): SemanticsMatcher = SemanticsMatcher.expectValue(SemanticsProperties.Selected, true)

    private fun signIn() {
        waitFor(hasTestTag(SignInTestTags.SCREEN))
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextClearance()
        compose.onNodeWithTag(SignInTestTags.ADDRESS).performTextInput(TestServer.address)
        compose.onNodeWithTag(SignInTestTags.PASSWORD).performTextInput(TestServer.password)
        waitFor(hasTestTag(SignInTestTags.SUBMIT) and isEnabled())
        compose.onNodeWithTag(SignInTestTags.SUBMIT).performScrollTo().performClick()
        compose.waitUntilDoesNotExist(hasTestTag(SignInTestTags.SCREEN), TIMEOUT_MS)
    }

    private fun openPetsList() {
        waitFor(hasTestTag(HomeTestTags.device(FIRST_CARD)))
        val seeAll = hasText("See all")
        compose.waitUntil(TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(HomeTestTags.LIST).performScrollToNode(seeAll) }.isSuccess
        }
        compose.onNode(seeAll).performClick()
        waitFor(hasTestTag(PetsTestTags.LIST_SCREEN))
    }

    /** Scrolls the pets list until [matcher] shows up, as the server's answers arrive. */
    private fun scrollList(matcher: SemanticsMatcher) {
        compose.waitUntil(TIMEOUT_MS) {
            runCatching { compose.onNodeWithTag(PetsTestTags.LIST).performScrollToNode(matcher) }.isSuccess
        }
    }

    private fun waitFor(matcher: SemanticsMatcher) {
        compose.waitUntilAtLeastOneExists(matcher, TIMEOUT_MS)
    }

    private companion object {
        const val FIRST_CARD = 1001L
        const val PURA_MAX = 1002L
        const val T5 = 1003L
        const val MOCHI = 1L
        const val TIGROU = 2L
        const val TIMEOUT_MS = 15_000L

        /** The start of a JPEG: all the mock checks before storing a placeholder of its own. */
        val JPEG = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0, 0x10)
    }
}
