plugins {
    alias(libs.plugins.petkit.jvm.library)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.javax.inject)

    testImplementation(libs.kotlinx.coroutines.test)
}

// SettingCatalogTest reads the mock's catalog of petkit-local's entities, generated from
// petkit-local's own definitions: every settable key there must be placed, or knowingly in Other.
val mockCatalog = rootProject.layout.projectDirectory.file("mock-server/src/main/resources/catalog.json")
tasks.withType<Test>().configureEach {
    inputs.file(mockCatalog).withPropertyName("mockCatalog").withPathSensitivity(PathSensitivity.NONE)
    systemProperty("petkit.catalog", mockCatalog.asFile.absolutePath)
}
