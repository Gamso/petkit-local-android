// A standalone build: the mock server is not a module of the app's Gradle project. It has its own
// wrapper and version catalog (gradle/libs.versions.toml in this directory), so it can be built,
// run and published as a container without the Android SDK.
pluginManagement {
    repositories {
        // Google's mirror of Maven Central first: the same files (dependency verification checks
        // their hashes), and CI runners are rarely refused by it, where Maven Central answers 429.
        maven {
            name = "MavenCentralGoogleMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        // Google's mirror of Maven Central first: the same files (dependency verification checks
        // their hashes), and CI runners are rarely refused by it, where Maven Central answers 429.
        maven {
            name = "MavenCentralGoogleMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
    }
}

rootProject.name = "petkit-local-mock-server"
