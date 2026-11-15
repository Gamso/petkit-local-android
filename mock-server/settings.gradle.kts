// A standalone build: the mock server is not a module of the app's Gradle project. It has its own
// wrapper and version catalog (gradle/libs.versions.toml in this directory), so it can be built,
// run and published as a container without the Android SDK.
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
    }
}

rootProject.name = "petkit-local-mock-server"
