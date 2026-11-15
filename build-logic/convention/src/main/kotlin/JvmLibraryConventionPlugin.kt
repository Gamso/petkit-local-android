import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile
import org.petkitlocal.buildlogic.apply
import org.petkitlocal.buildlogic.configureKotlinCompile
import org.petkitlocal.buildlogic.configureQuality
import org.petkitlocal.buildlogic.configureUnitTests
import org.petkitlocal.buildlogic.libs

/** A pure Kotlin/JVM module, with no Android in it (core:domain). */
class JvmLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project): Unit = with(target) {
        pluginManager.apply(libs, "kotlin-jvm")
        extensions.configure<JavaPluginExtension> {
            sourceCompatibility = JavaVersion.VERSION_17
            targetCompatibility = JavaVersion.VERSION_17
        }
        configureKotlinCompile()
        // The build runs on JDK 25; compile against the Java 17 API too, not only to its
        // bytecode, so a JDK 18+ method cannot slip into a module Android 10 runs.
        tasks.withType<KotlinJvmCompile>().configureEach {
            compilerOptions.freeCompilerArgs.add("-Xjdk-release=17")
        }
        configureUnitTests()
        configureQuality()
    }
}
