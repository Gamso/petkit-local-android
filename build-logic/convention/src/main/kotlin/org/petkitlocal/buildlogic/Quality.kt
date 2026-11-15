package org.petkitlocal.buildlogic

import dev.detekt.gradle.Detekt
import dev.detekt.gradle.extensions.DetektExtension
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jlleitschuh.gradle.ktlint.KtlintExtension
import org.jlleitschuh.gradle.ktlint.reporter.ReporterType

/** ktlint, detekt and Kover on every module; all three hook into `check` themselves. */
internal fun Project.configureQuality() {
    pluginManager.apply(libs, "ktlint", "detekt", "kover")

    extensions.configure<KtlintExtension> {
        version.set(libs.version("ktlint"))
        android.set(true)
        reporters {
            reporter(ReporterType.PLAIN)
            reporter(ReporterType.SARIF)
        }
    }

    extensions.configure<DetektExtension> {
        buildUponDefaultConfig.set(true)
        parallel.set(true)
        config.setFrom(rootProject.file("config/detekt/detekt.yml"))
        basePath.set(rootProject.layout.projectDirectory)
    }
    tasks.withType<Detekt>().configureEach {
        jvmTarget.set("17")
        reports {
            html.required.set(true)
            sarif.required.set(true)
            checkstyle.required.set(false)
            markdown.required.set(false)
        }
    }

    configureModuleRules()
}
