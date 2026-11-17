// The notification service (ADR 0007): a foreground service holding the server's notification
// socket, the notifications it writes in the phone's language, their channels and deep links.
// It sees the domain only; the app binds the data layer behind it.
plugins {
    alias(libs.plugins.petkit.android.library)
    alias(libs.plugins.petkit.android.hilt)
}

dependencies {
    implementation(projects.core.domain)
    implementation(libs.androidx.core.ktx)

    testImplementation(libs.kotlinx.coroutines.test)
    // Notifications, channels and their text on real resources (Robolectric).
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.robolectric)
}
