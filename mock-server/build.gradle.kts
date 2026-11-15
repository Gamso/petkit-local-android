plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
    application
}

group = "io.github.gamso.petkitlocal"
version = "0.1.0"

kotlin {
    jvmToolchain(25)
    compilerOptions {
        allWarningsAsErrors.set(true)
    }
}

application {
    mainClass.set("io.github.gamso.petkitlocal.mock.MainKt")
    applicationName = "petkit-local-mock"
}

/** The server's contract, vendored at the repo root by scripts/update-openapi.sh. */
val openApiContract = layout.projectDirectory.file("../api/openapi.yaml")

tasks.processResources {
    // Served at /app/v1/openapi.yaml, and the schema every test validates responses against.
    from(openApiContract)
}

dependencies {
    implementation(platform(libs.ktor.bom))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.websockets)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    runtimeOnly(libs.logback.classic)

    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.websockets)
    testImplementation(libs.json.schema.validator)
    testImplementation(libs.jackson.dataformat.yaml)
    testRuntimeOnly(libs.logback.classic)
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

tasks.named<JavaExec>("run") {
    // `./gradlew run --args="--port=9000"`; environment variables work too (README).
    standardInput = System.`in`
}

ktlint {
    version.set(libs.versions.ktlint.cli)
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom(files("detekt.yml"))
}
