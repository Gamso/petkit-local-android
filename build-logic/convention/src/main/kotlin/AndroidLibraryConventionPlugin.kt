import com.android.build.api.dsl.LibraryExtension
import com.android.build.api.variant.LibraryAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.petkitlocal.buildlogic.apply
import org.petkitlocal.buildlogic.configureAndroidCommon
import org.petkitlocal.buildlogic.configureQuality
import org.petkitlocal.buildlogic.disableReleaseHostTests
import org.petkitlocal.buildlogic.libs
import org.petkitlocal.buildlogic.moduleNamespace

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs, "android-library")

        extensions.configure<LibraryExtension> {
            configureAndroidCommon(this)
            namespace = moduleNamespace
        }
        extensions.configure<LibraryAndroidComponentsExtension> {
            disableReleaseHostTests()
        }
        configureQuality()
    }
}
