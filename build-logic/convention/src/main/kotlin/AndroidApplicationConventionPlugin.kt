import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.petkitlocal.buildlogic.apply
import org.petkitlocal.buildlogic.configureAndroidCommon
import org.petkitlocal.buildlogic.configureCompose
import org.petkitlocal.buildlogic.configureManagedDevices
import org.petkitlocal.buildlogic.configureQuality
import org.petkitlocal.buildlogic.disableReleaseHostTests
import org.petkitlocal.buildlogic.libs
import org.petkitlocal.buildlogic.version

class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs, "android-application", "compose-compiler")

        extensions.configure<ApplicationExtension> {
            configureAndroidCommon(this)
            configureCompose(this)
            configureManagedDevices(this)
            defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            buildTypes.getByName("release") {
                isMinifyEnabled = true
                isShrinkResources = true
                proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
                // Until release signing arrives (issue 26), a release build is signed with the
                // debug key, so CI and developers can install and smoke-test the R8 output.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            disableReleaseHostTests()
        }
        configureQuality()
    }
}
