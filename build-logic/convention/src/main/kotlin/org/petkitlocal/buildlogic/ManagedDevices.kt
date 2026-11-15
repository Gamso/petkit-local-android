package org.petkitlocal.buildlogic

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice.PageAlignment
import org.gradle.api.Project

/**
 * The emulators the instrumented tests run on (ARCHITECTURE.md §6), as Gradle Managed Devices:
 * Gradle downloads the system image, creates the emulator, boots it, runs the tests and shuts it
 * down. One task per device and variant, e.g. `./gradlew :app:pixel6Api37DebugAndroidTest`.
 */
internal fun Project.configureManagedDevices(extension: CommonExtension) {
    val targetSdk = libs.version("targetSdk").toInt()
    val minSdk = libs.version("minSdk").toInt()
    extension.testOptions.managedDevices.localDevices.apply {
        // The target SDK on an Automated Test Device image: AOSP without the apps and services a
        // test never uses, so it boots faster and needs less memory. CI runs this one
        // (.github/workflows/e2e.yml).
        create("pixel6Api$targetSdk") {
            device = PHONE
            sdkVersion = targetSdk
            systemImageSource = "aosp-atd"
            require64Bit = true
            testedAbi = hostAbi
            // ATD images use 4 KB pages (16 KB ones exist only as google_apis_ps16k). Said here,
            // because AGP cannot tell it from the SDK version and warns at every configuration.
            pageAlignment = PageAlignment.FORCE_4KB_PAGES
        }
        // The minimum SDK. There is no ATD image below API 30, so it is a Google APIs image. The
        // nightly e2e run and developers use it; pull requests do not wait for it.
        create("pixel6Api$minSdk") {
            device = PHONE
            sdkVersion = minSdk
            systemImageSource = "google"
            require64Bit = true
            testedAbi = hostAbi
        }
    }
}

private const val PHONE = "Pixel 6"

/**
 * The app has no native code, so any ABI tests it; the host's own runs without translation. AGP
 * defaults to x86_64 today and to arm64-v8a from AGP 10, which an x86_64 CI runner cannot run.
 * (AGP 9.5.1's setup task still prints its "does not specify a testedAbi" notice.)
 */
private val hostAbi: String =
    if (System.getProperty("os.arch") in setOf("aarch64", "arm64")) "arm64-v8a" else "x86_64"
