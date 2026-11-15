// Test helpers shared by every module: dispatchers, repository fakes and the screenshot matrix
// (ARCHITECTURE.md §2).
plugins {
    alias(libs.plugins.petkit.android.library)
}

dependencies {
    api(projects.core.domain)
    api(libs.kotlinx.coroutines.test)
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
    api(libs.roborazzi)
    api(libs.roborazzi.compose)
}
