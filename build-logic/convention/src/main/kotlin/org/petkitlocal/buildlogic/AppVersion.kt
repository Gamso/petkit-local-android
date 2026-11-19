package org.petkitlocal.buildlogic

import org.gradle.api.GradleException
import org.gradle.api.Project

/** The APK's `versionName` and `versionCode`. */
data class AppVersion(val name: String, val code: Int)

/** The Gradle property a release passes its version in: `-PappVersion=0.1.0` (release.yml, from tag `v0.1.0`). */
const val APP_VERSION_PROPERTY = "appVersion"

/**
 * Every build that is not a release. Such a build is signed with another key than a release (the
 * debug key, or CI's), so neither ever installs over the other: the code only has to be valid.
 */
const val DEV_VERSION_CODE = 1

/**
 * The app's version. A release build is given it from its tag, `-PappVersion=MAJOR.MINOR.PATCH`
 * ([parseAppVersion]); every other build (local, CI) needs no property and is a dev build, named
 * [devName] with code [DEV_VERSION_CODE].
 */
fun Project.appVersion(devName: String): AppVersion =
    providers.gradleProperty(APP_VERSION_PROPERTY).orNull?.let(::parseAppVersion)
        ?: AppVersion(devName, DEV_VERSION_CODE)

/**
 * `MAJOR.MINOR.PATCH` -> name as given, code `major * 10000 + minor * 100 + patch` (0.1.0 -> 100,
 * 1.2.3 -> 10203), so a later version always has a higher code and Android installs it as an update.
 *
 * Strict, because the code must never go backwards or collide: minor and patch 0-99, major at most
 * 209999 (codes stay under Google Play's 2,100,000,000), no leading zero (`0.01.0` would collide
 * with `0.1.0`), and no suffix: a `-rc.1` would need its own place in the code to stay below its
 * final release, and releases are cut without candidates. `0.0.0` is refused: a code must be positive.
 */
fun parseAppVersion(value: String): AppVersion {
    val match = SEMVER.matchEntire(value) ?: throw GradleException(
        "-P$APP_VERSION_PROPERTY=$value is not MAJOR.MINOR.PATCH (e.g. 0.1.0, from tag v0.1.0): " +
            "digits only, no leading zero, no suffix, minor and patch 0-99.",
    )
    val (major, minor, patch) = match.destructured.toList().map { it.toLong() }
    if (major > MAX_MAJOR) throw GradleException("-P$APP_VERSION_PROPERTY=$value: major is at most $MAX_MAJOR.")
    val code = major * MAJOR_STEP + minor * MINOR_STEP + patch
    if (code < 1) throw GradleException("-P$APP_VERSION_PROPERTY=$value: the version code would be 0.")
    return AppVersion(value, code.toInt())
}

private const val MAJOR_STEP = 10_000L
private const val MINOR_STEP = 100L
private const val MAX_MAJOR = 209_999L
private val SEMVER = Regex("""(0|[1-9][0-9]{0,5})\.(0|[1-9][0-9]?)\.(0|[1-9][0-9]?)""")
