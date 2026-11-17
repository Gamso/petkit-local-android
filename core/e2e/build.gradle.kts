// Support for the instrumented end-to-end tests (docs/ARCHITECTURE.md §6): which server they run
// against (the mock or the real petkit-local) and where it is, the mock's control API (`/mock`),
// and API clients built from the app's own core:network. Test code only: a module adds it with
// `androidTestImplementation(projects.core.e2e)`.
plugins {
    alias(libs.plugins.petkit.android.library)
}

dependencies {
    api(projects.core.network)
    api(libs.junit4)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.test.runner)
}
