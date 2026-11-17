// RoborazziComposeOptions is how Roborazzi varies locale, night mode and font scale.
@file:OptIn(ExperimentalRoborazziApi::class)

package org.petkitlocal.feature.settings.licences

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.captureRoboImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.designsystem.theme.PkTheme
import org.petkitlocal.core.domain.settings.GetOpenSourceProjectsUseCase
import org.petkitlocal.core.domain.settings.OpenSourceLicence
import org.petkitlocal.core.domain.settings.OpenSourceLicencesRepository
import org.petkitlocal.core.domain.settings.OpenSourceProjects
import org.petkitlocal.core.testing.ScreenshotVariant
import org.robolectric.ParameterizedRobolectricTestRunner
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The licences list: the ViewModel on a fixed list, and the screen (Robolectric). */
@OptIn(ExperimentalCoroutinesApi::class) // Dispatchers.setMain
@RunWith(RobolectricTestRunner::class)
class LicencesTest {
    @get:Rule
    val compose = createComposeRule()

    private val dispatcher = StandardTestDispatcher()
    private val repository = object : OpenSourceLicencesRepository {
        override suspend fun components() = LicencesPreviewData.components

        override suspend fun appLicence() = OpenSourceLicence("MIT License", "text")
    }
    private val calls = mutableListOf<String>()
    private val actions = LicencesActions(
        onBack = { calls += "back" },
        onProjectClick = { calls += "project ${it.name}" },
        onOpen = { calls += "open ${it.id}" },
        onDismiss = { calls += "dismiss" },
    )

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `projects of several components unfold, and a single one opens its licence`() = runTest(dispatcher) {
        val viewModel = LicencesViewModel(GetOpenSourceProjectsUseCase(repository))
        testScheduler.advanceUntilIdle()
        val projects = checkNotNull(viewModel.uiState.value.projects)
        assertEquals(
            listOf(OpenSourceProjects.ANDROIDX_NAME, "coil", "Inter (font)", "okhttp"),
            projects.map {
                it.name
            },
        )

        val androidx = projects.first()
        viewModel.onProjectClick(androidx)
        assertEquals(setOf(androidx.name), viewModel.uiState.value.expanded)
        viewModel.onProjectClick(androidx)
        assertEquals(emptySet<String>(), viewModel.uiState.value.expanded)

        viewModel.onProjectClick(projects.last())
        assertEquals(LicencesPreviewData.okhttp, viewModel.uiState.value.opened)
        viewModel.onDismiss()
        assertNull(viewModel.uiState.value.opened)
    }

    @Test
    fun `a project says its licences, and how many components when several`() {
        compose.setContent { PkTheme { LicencesScreen(LicencesPreviewData.unfolded, actions) } }

        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("AndroidX and Jetpack Compose").assertExists()
        compose.onNodeWithText("3 components · Apache License 2.0").assertExists()
        compose.onNodeWithText("2.8.5").assertExists() // an unfolded component's version
        compose.onNodeWithTag(LicencesTestTags.component("androidx.room:room-runtime")).performClick()
        compose.onNodeWithTag(LicencesTestTags.LIST).performScrollToNode(hasTestTag(LicencesTestTags.project("okhttp")))
        compose.onNodeWithTag(LicencesTestTags.project("okhttp")).performClick()

        assertEquals(listOf("back", "open androidx.room:room-runtime", "project okhttp"), calls)
    }

    @Test
    fun `an opened component shows its licence text in a dialog`() {
        val state = LicencesUiState(projects = LicencesPreviewData.projects, opened = LicencesPreviewData.okhttp)
        compose.setContent { PkTheme { LicencesScreen(state, actions) } }

        compose.onNodeWithText("TERMS AND CONDITIONS", substring = true).assertExists()
        compose.onNodeWithTag(LicencesTestTags.CLOSE).performClick()
        assertEquals(listOf("dismiss"), calls)
    }

    @Test
    @Config(qualifiers = "fr")
    fun `the list is in French`() {
        compose.setContent { PkTheme { LicencesScreen(LicencesPreviewData.unfolded, actions) } }
        compose.onNodeWithText("Licences open source").assertExists()
        compose.onNodeWithText("AndroidX et Jetpack Compose").assertExists()
        compose.onNodeWithText("3 composants · Apache License 2.0").assertExists()
    }
}

/** The list (AndroidX unfolded) and a licence dialog, in light/dark x EN/FR. SDK 36: see core:designsystem. */
@RunWith(ParameterizedRobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = "w360dp-h800dp-xhdpi", sdk = [36])
class LicencesScreenshotTest(private val variant: ScreenshotVariant) {
    @Test
    fun list() = capture("licences") { LicencesScreen(LicencesPreviewData.unfolded, LicencesActions()) }

    @Test
    fun licence() = capture("licence_dialog") {
        // The dialog's card as it sits over the screen; a real window does not render here.
        Box(
            Modifier.fillMaxSize().background(PkTheme.colors.background).padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            LicenceDialogContent(LicencesPreviewData.okhttp.name, LicencesPreviewData.okhttp.licences, onDismiss = {})
        }
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
        fun variants(): List<ScreenshotVariant> = ScreenshotVariant.screens
    }
}
