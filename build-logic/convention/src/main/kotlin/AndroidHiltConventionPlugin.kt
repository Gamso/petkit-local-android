import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.petkitlocal.buildlogic.apply
import org.petkitlocal.buildlogic.lib
import org.petkitlocal.buildlogic.libs

class AndroidHiltConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs, "ksp", "hilt")
        dependencies {
            add("implementation", libs.lib("hilt-android"))
            add("ksp", libs.lib("hilt-compiler"))
        }
    }
}
