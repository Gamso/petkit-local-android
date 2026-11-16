plugins {
    alias(libs.plugins.petkit.android.feature)
    alias(libs.plugins.petkit.android.roborazzi)
}

dependencies {
    // Thumbnails and photos; the image loader itself (the app's OkHttp client) is configured in :app.
    implementation(libs.coil.compose)
    // The clip player's surface and state; the player itself is built in :app (ClipPlayerFactory).
    implementation(libs.media3.common)
    implementation(libs.media3.ui.compose)
}
