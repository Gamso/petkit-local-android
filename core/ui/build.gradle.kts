// Shared screen pieces, formatting with resources (weights, durations, dates), and the keyed
// translation of the labels the server sends (SPEC.md §7).
plugins {
    alias(libs.plugins.petkit.android.compose)
}

dependencies {
    api(projects.core.designsystem)
    // The domain's models: a shared piece shows a timeline card or a clip as the domain has it.
    implementation(projects.core.domain)
    // Thumbnails; the image loader itself (the app's OkHttp client) is configured in :app.
    implementation(libs.coil.compose)
    // Android's permission prompt, asked the same way from every screen (NotificationPermission.kt).
    implementation(libs.androidx.activity.compose)
    // The live player's interface and pieces, shared by the live screen and a camera page's live
    // card; the player itself (ExoPlayer on core:network's sources) is built in :app.
    api(libs.media3.common)
}
