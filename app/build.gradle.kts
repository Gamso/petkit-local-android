plugins {
    alias(libs.plugins.petkit.android.application)
    alias(libs.plugins.petkit.android.hilt)
    alias(libs.plugins.kotlin.serialization)
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

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(projects.core.testing)

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
