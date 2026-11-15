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
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlinx.coroutines.test)
}
