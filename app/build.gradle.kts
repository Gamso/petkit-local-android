import java.util.regex.Pattern
import org.petkitlocal.buildlogic.DirectDependenciesReport
import org.petkitlocal.buildlogic.directExternalDependencies

plugins {
    alias(libs.plugins.petkit.android.application)
    alias(libs.plugins.petkit.android.hilt)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.aboutlibraries.android)
}

android {
    namespace = "org.petkitlocal.android"

    defaultConfig {
        applicationId = "org.petkitlocal.android"
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // English and French only (SPEC.md §7): drops the other languages libraries bring along.
    androidResources {
        localeFilters += listOf("en", "fr")
    }
}

/*
 * The open-source licences in About (issue #24), generated into `res/raw/aboutlibraries.json` of each
 * variant and read by the app offline. Listed: only the DIRECT dependencies of the release build, the
 * external libraries our own modules declare on their release runtime classpath, as Gradle resolved
 * them (`directExternalDependencies`, build-logic), never what those bring along and nothing debug-
 * or test-only; every other artifact the plugin collects is excluded. Plus what ships without being a
 * dependency: the fonts and the Material Symbols of core:designsystem (`config/aboutlibraries`).
 *
 * The build never goes to the network for them (offlineMode): `config/aboutlibraries` holds the full
 * texts (the SPDX text of Apache 2.0, every direct dependency's licence today, and core:designsystem's
 * own font and icon licences). A dependency under any other licence fails the build until its text is
 * added there and allowed below.
 */
val directReleaseDependencies = directExternalDependencies("releaseRuntimeClasspath")
val bundledAssets =
    listOf("petkitlocal.font:inter", "petkitlocal.font:plus-jakarta-sans", "petkitlocal.icons:material-symbols")

aboutLibraries {
    offlineMode = true
    collect {
        configPath = file("config/aboutlibraries")
        fetchRemoteLicense = false
        fetchRemoteFunding = false
    }
    export {
        excludeFields.addAll("developers", "funding", "description", "organization")
    }
    library {
        // One pattern matching every id that is not a direct release dependency or a bundled asset.
        exclusionPatterns.set(
            directReleaseDependencies.map { ids ->
                val kept = (ids + bundledAssets).joinToString("|") { Pattern.quote(it) }
                setOf(Pattern.compile("^(?!(?:$kept)$).*"))
            },
        )
    }
    license {
        strictMode = com.mikepenz.aboutlibraries.plugin.StrictMode.FAIL
        allowedLicenses.addAll(
            "Apache-2.0",
            "OFL-1.1",
            "Inter-OFL-1.1",
            "PlusJakartaSans-OFL-1.1",
            "MaterialSymbols-Apache-2.0",
        )
    }
}

// AboutLibrariesRepositoryTest compares the shipped list with the direct release dependencies.
val directReleaseDependenciesReport = tasks.register<DirectDependenciesReport>("directReleaseDependencies") {
    dependencies.set(directReleaseDependencies)
    output.set(layout.buildDirectory.file("reports/licences/direct-release-dependencies.txt"))
}
tasks.withType<Test>().configureEach {
    val report = directReleaseDependenciesReport.flatMap { it.output }
    inputs.file(report).withPropertyName("directReleaseDependencies").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("petkit.directReleaseDependencies", report.get().asFile.absolutePath)
}

dependencies {
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.network)
    implementation(projects.core.designsystem)
    implementation(projects.core.ui)
    implementation(projects.feature.auth)
    implementation(projects.feature.home)
    implementation(projects.feature.device)
    implementation(projects.feature.history)
    implementation(projects.feature.pets)
    implementation(projects.feature.live)
    implementation(projects.feature.settings)
    implementation(projects.push)

    // Reads the open-source licences list the AboutLibraries plugin generates into res/raw.
    implementation(libs.aboutlibraries.core)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    // History's clip player, reading through core:network's authenticated data source.
    implementation(libs.media3.exoplayer)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)
    // AboutLibrariesRepositoryTest reads the generated licences list from the merged resources.
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)

    // End-to-end tests on an emulator (src/androidTest), against the mock server: Gradle Managed
    // Devices run them (build-logic ManagedDevices.kt, .github/workflows/e2e.yml).
    androidTestImplementation(projects.core.e2e)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    // Compose's test library brings Espresso 3.5, whose event injection reflects
    // InputManager.getInstance(), gone in SDK 37.
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
}
