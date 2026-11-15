plugins {
    alias(libs.plugins.petkit.android.feature)
    alias(libs.plugins.petkit.android.roborazzi)
}

dependencies {
    // Pet photos; the image loader itself (the app's OkHttp client) is configured in :app.
    implementation(libs.coil.compose)
}
