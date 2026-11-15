// Preferences and the Keystore-encrypted session token (ADR 0004).
plugins {
    alias(libs.plugins.petkit.android.library)
    alias(libs.plugins.petkit.android.hilt)
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    api(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.kotlinx.coroutines.test)

    // The real Android Keystore exists only on a device: KeystoreTokenCipher is tested there.
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}
