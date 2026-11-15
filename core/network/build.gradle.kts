plugins {
    alias(libs.plugins.petkit.android.library)
    alias(libs.plugins.petkit.android.hilt)
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    api(projects.core.domain)
    api(libs.okhttp)
    api(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    api(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.reflect)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.snakeyaml)
}

// The contract tests read the server's OpenAPI document vendored at the repository root
// (api/openapi.yaml, refreshed by scripts/update-openapi.sh). It is a declared input, so a new
// contract re-runs the tests instead of reusing a cached result.
val openApi = rootProject.layout.projectDirectory.file("api/openapi.yaml")
tasks.withType<Test>().configureEach {
    inputs.file(openApi).withPropertyName("openApi").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("petkit.openapi", openApi.asFile.absolutePath)
}
