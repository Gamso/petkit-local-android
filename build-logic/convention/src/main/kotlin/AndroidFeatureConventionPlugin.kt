import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project
import org.petkitlocal.buildlogic.apply
import org.petkitlocal.buildlogic.lib
import org.petkitlocal.buildlogic.libs

/**
 * A `feature:*` module: Compose screens, Hilt ViewModels, type-safe routes. It sees the domain,
 * the design system and the shared UI pieces, and nothing of the data layer
 * (docs/ARCHITECTURE.md §2, enforced by `checkModuleRules`).
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(
            libs,
            "petkit-android-compose",
            "petkit-android-hilt",
            "kotlin-serialization",
        )

        dependencies {
            add("implementation", project(":core:domain"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:ui"))

            add("implementation", libs.lib("androidx-compose-material3"))
            add("implementation", libs.lib("androidx-hilt-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-lifecycle-runtime-compose"))
            add("implementation", libs.lib("androidx-lifecycle-viewmodel-compose"))
            add("implementation", libs.lib("androidx-navigation-compose"))
            add("implementation", libs.lib("kotlinx-serialization-json"))

            add("testImplementation", project(":core:testing"))
            add("testImplementation", libs.lib("kotlinx-coroutines-test"))
            add("testImplementation", libs.lib("turbine"))
        }
    }
}
