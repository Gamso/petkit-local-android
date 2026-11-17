package org.petkitlocal.android.licences

import androidx.test.core.app.ApplicationProvider
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.petkitlocal.core.domain.settings.OpenSourceComponent
import org.robolectric.RobolectricTestRunner

/**
 * The licences list this very build generated, read from the app's resources as the app reads it
 * (Robolectric: the parser uses Android's org.json), against the direct release dependencies Gradle
 * resolved (app/build.gradle.kts writes them for this test). Every licence comes with its text: the
 * app reads it offline, and Apache 2.0 wants the text to travel with the APK.
 */
@RunWith(RobolectricTestRunner::class)
class AboutLibrariesRepositoryTest {
    private val repository = AboutLibrariesRepository(ApplicationProvider.getApplicationContext())
    private val components: List<OpenSourceComponent> = runBlocking { repository.components() }

    private val directReleaseDependencies: Set<String> =
        File(checkNotNull(System.getProperty("petkit.directReleaseDependencies")) { "run through Gradle" })
            .readLines().filter { it.isNotBlank() }.toSet()

    @Test
    fun theListIsExactlyTheDirectReleaseDependenciesAndTheBundledAssets() {
        val shipped = components.map { it.id }.toSet()
        assertEquals(directReleaseDependencies, shipped - BUNDLED_ASSETS)
        assertTrue(shipped.containsAll(BUNDLED_ASSETS))
        // Transitive, debug-only and test-only artifacts stay out.
        assertTrue("com.squareup.okio:okio" !in shipped)
        assertTrue("androidx.compose.ui:ui-tooling" !in shipped)
        assertTrue("junit:junit" !in shipped)
    }

    @Test
    fun theDirectDependenciesAreThoseTheModulesDeclare() {
        assertTrue(
            directReleaseDependencies.containsAll(listOf("com.squareup.okhttp3:okhttp", "androidx.room:room-runtime")),
        )
        assertTrue(directReleaseDependencies.none { it.endsWith("-bom") })
    }

    @Test
    fun everyComponentHasALicenceAndEveryLicenceItsFullText() {
        assertEquals(emptyList<String>(), components.filter { it.licences.isEmpty() }.map { it.id })
        val withoutText = components.filter { c -> c.licences.any { it.text.isNullOrBlank() } }.map { it.id }
        assertEquals(emptyList<String>(), withoutText)
    }

    @Test
    fun nothingFromGooglePlayServicesIsShipped() {
        assertEquals(emptyList<String>(), components.map { it.id }.filter { it.startsWith("com.google.android.gms:") })
    }

    @Test
    fun theAppsOwnLicenceIsTheRepositorysMitLicence() {
        val licence = runBlocking { repository.appLicence() }
        assertEquals("MIT License", licence.name)
        // The module's test working directory is app/: the repository's LICENSE is one level up.
        assertEquals(File("../LICENSE").readText(), licence.text)
    }

    private companion object {
        val BUNDLED_ASSETS =
            setOf("petkitlocal.font:inter", "petkitlocal.font:plus-jakarta-sans", "petkitlocal.icons:material-symbols")
    }
}
