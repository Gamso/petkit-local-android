plugins {
    alias(libs.plugins.petkit.android.compose)
    alias(libs.plugins.petkit.android.roborazzi)
}

dependencies {
    api(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.foundation)
    api(libs.androidx.compose.material3)
    api(libs.androidx.compose.ui)
}
