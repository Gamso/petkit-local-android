import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.petkitlocal.buildlogic.apply
import org.petkitlocal.buildlogic.configureCompose
import org.petkitlocal.buildlogic.libs

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs, "petkit-android-library", "compose-compiler")
        extensions.configure<LibraryExtension> {
            configureCompose(this)
        }
    }
}
