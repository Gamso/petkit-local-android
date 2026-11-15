// Room database, entities and DAOs: the local cache that is the single source of truth for
// devices, pets and timeline cards (ADR 0002). The schema of every version is exported to
// schemas/ and committed; the migration tests read it.
plugins {
    alias(libs.plugins.petkit.android.library)
    alias(libs.plugins.petkit.android.hilt)
    alias(libs.plugins.room)
}

room {
    schemaDirectory("$projectDir/schemas")
}

android {
    // The migration tests run on Robolectric and read the exported schemas as assets.
    sourceSets.getByName("test").assets.srcDir("$projectDir/schemas")
}

dependencies {
    api(libs.androidx.room.runtime)
    api(libs.kotlinx.coroutines.core)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.junit4)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.turbine)
}
