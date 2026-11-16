plugins {
    alias(libs.plugins.petkit.android.feature)
    alias(libs.plugins.petkit.android.roborazzi)
}

dependencies {
    // The live player's interface and state; the player itself is built in :app (LivePlayerFactory).
    implementation(libs.media3.common)
}
