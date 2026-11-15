package org.petkitlocal.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.Lint
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile

/** SDK levels, Java level and lint, shared by the app and every Android library. */
internal fun Project.configureAndroidCommon(extension: CommonExtension) {
    extension.apply {
        compileSdk = libs.version("compileSdk").toInt()
        defaultConfig.minSdk = libs.version("minSdk").toInt()
        compileOptions.sourceCompatibility = JavaVersion.VERSION_17
        compileOptions.targetCompatibility = JavaVersion.VERSION_17
        configureLint(lint)
        // Robolectric reads merged resources and the manifest of the module under test.
        testOptions.unitTests.isIncludeAndroidResources = true
    }
    configureKotlinCompile()
    configureUnitTests()
}

private fun Project.configureLint(lint: Lint) {
    lint.apply {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = false
        // Version freshness is Dependabot's job (.github/dependabot.yml); as lint warnings they
        // would turn a green build red on the day a library ships, with no code change.
        disable += setOf(
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            "OldTargetApi",
        )
        lintConfig = rootProject.file("config/lint/lint.xml")
    }
}

internal fun Project.configureKotlinCompile() {
    tasks.withType<KotlinJvmCompile>().configureEach {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
            allWarningsAsErrors.set(true)
        }
    }
}

/**
 * JUnit 5 (Jupiter) for plain JVM tests, and the Vintage engine for the JUnit 4 tests that
 * Robolectric and Roborazzi need, so both kinds run in the same `test` task.
 */
internal fun Project.configureUnitTests() {
    // The two engines are the two kinds of host test (ARCHITECTURE.md §6): Jupiter runs the plain
    // JVM ones (unit, integration, contract), Vintage the JUnit 4 ones Robolectric needs (Compose
    // UI, screenshots). CI runs them in separate jobs with -Ppetkit.testEngines=<engine id>;
    // locally, and in `check`, both run.
    val engines = providers.gradleProperty("petkit.testEngines").orNull
        ?.split(',')?.map(String::trim)?.filter(String::isNotEmpty).orEmpty()
    val hasTests = file("src/test").exists()
    tasks.withType<Test>().configureEach {
        useJUnitPlatform {
            if (engines.isNotEmpty()) includeEngines(*engines.toTypedArray())
        }
        // Gradle 9 fails a test task that finds no tests, which catches a misconfigured engine.
        // Exempt: a module with no src/test yet (Hilt and KSP still add generated test sources),
        // and a run restricted to one engine, where a module may have no test of that kind.
        failOnNoDiscoveredTests.set(hasTests && engines.isEmpty())
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)
        systemProperty("user.language", "en")
        systemProperty("user.country", "US")
    }
    dependencies {
        add("testImplementation", platform(libs.lib("junit-bom")))
        add("testImplementation", libs.lib("junit-jupiter"))
        add("testRuntimeOnly", libs.lib("junit-vintage-engine"))
        add("testRuntimeOnly", libs.lib("junit-platform-launcher"))
    }
}
