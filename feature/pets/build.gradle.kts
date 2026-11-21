plugins {
    alias(libs.plugins.petkit.android.feature)
    alias(libs.plugins.petkit.android.roborazzi)
}

dependencies {
    // Reference face photos; the image loader itself (the app's OkHttp client) is configured in :app.
    implementation(libs.coil.compose)
    // The camera and the photo picker, and the file a photo is taken into (FileProvider).
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
}
