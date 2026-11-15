// Test helpers shared by every module: dispatchers now, repository fakes and OpenAPI fixtures as
// the repositories arrive (ARCHITECTURE.md §2).
plugins {
    alias(libs.plugins.petkit.android.library)
}

dependencies {
    api(projects.core.domain)
    api(libs.kotlinx.coroutines.test)
    api(platform(libs.junit.bom))
    api(libs.junit.jupiter)
}
