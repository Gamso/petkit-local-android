// Repository implementations: Room as the single source of truth, REST and WebSocket feeding it
// (ADR 0002), and the session (ADR 0004).
plugins {
    alias(libs.plugins.petkit.android.library)
    alias(libs.plugins.petkit.android.hilt)
}

dependencies {
    api(projects.core.domain)
    implementation(projects.core.network)
    implementation(projects.core.database)
    implementation(projects.core.datastore)

    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.turbine)
}
