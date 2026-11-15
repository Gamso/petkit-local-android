package org.petkitlocal.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.variant.AndroidComponentsExtension
import com.android.build.api.variant.HasHostTestsBuilder
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies

/** Compose, its tooling, and Robolectric Compose UI tests (ARCHITECTURE.md §6). */
internal fun Project.configureCompose(extension: CommonExtension) {
    extension.buildFeatures.compose = true
    dependencies {
        val bom = platform(libs.lib("androidx-compose-bom"))
        add("implementation", bom)
        add("testImplementation", bom)
        add("implementation", libs.lib("androidx-compose-ui-tooling-preview"))
        add("debugImplementation", libs.lib("androidx-compose-ui-tooling"))
        // createComposeRule() launches an empty ComponentActivity declared by this manifest.
        add("debugImplementation", libs.lib("androidx-compose-ui-test-manifest"))
        add("testImplementation", libs.lib("androidx-compose-ui-test-junit4"))
        add("testImplementation", libs.lib("robolectric"))
        // Robolectric 4.18 brings Espresso 3.5, whose event injection reflects
        // InputManager.getInstance(), gone in SDK 37; Compose UI tests on the target SDK need
        // a newer one.
        add("testImplementation", libs.lib("androidx-test-espresso-core"))
        add("testImplementation", libs.lib("junit4"))
    }
}

/** Host (JVM) tests run on the debug variant only; the release variant is proven by its build. */
internal fun AndroidComponentsExtension<*, *, *>.disableReleaseHostTests() {
    beforeVariants(selector().withBuildType("release")) { variant ->
        (variant as? HasHostTestsBuilder)?.hostTests?.values?.forEach { it.enable = false }
    }
}
