import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.petkitlocal.buildlogic.apply
import org.petkitlocal.buildlogic.lib
import org.petkitlocal.buildlogic.libs

/**
 * Roborazzi screenshot tests on Robolectric. The golden images live in `src/test/screenshots`
 * and are committed; `check` verifies against them and `recordRoborazziDebug` rewrites them.
 */
class AndroidRoborazziConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs, "roborazzi")
        extensions.configure<RoborazziExtension> {
            outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
        }
        dependencies {
            add("testImplementation", libs.lib("roborazzi"))
            add("testImplementation", libs.lib("roborazzi-compose"))
            add("testImplementation", libs.lib("roborazzi-junit-rule"))
        }
        tasks.named("check") { dependsOn("verifyRoborazziDebug") }
    }
}
