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
import org.petkitlocal.buildlogic.disableDeviceTestsWithoutSources
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
                // Signed with the debug key unless a key is injected (-Pandroid.injected.signing.*),
                // so developers and CI can install and smoke-test the R8 output. Only release.yml
                // injects the release key; no keystore is ever committed.
                signingConfig = signingConfigs.getByName("debug")
            }
        }
        extensions.configure<ApplicationAndroidComponentsExtension> {
            disableReleaseHostTests()
            disableDeviceTestsWithoutSources(target)
        }
        configureQuality()
    }
}
