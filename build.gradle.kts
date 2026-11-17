// Every plugin is declared here once, unapplied, so the convention plugins in build-logic and
// every module load the same version from the same classloader.
plugins {
    alias(libs.plugins.aboutlibraries.android) apply false
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.compose.compiler) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.roborazzi) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.kover)
}

// One coverage report for the whole app (ARCHITECTURE.md §6): `./gradlew koverHtmlReport`.
dependencies {
    // Every module; `:core` and `:feature` are only folders and have no build of their own.
    subprojects.filter { it.buildFile.exists() }.forEach { kover(project(it.path)) }
}
