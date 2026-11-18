// Without this block the plugins applied here (kotlin-dsl and its Kotlin dependencies) resolve from
// the Gradle Plugin Portal alone, which redirects to Maven Central: on CI that answers 429 and the
// build dies before anything compiles. Same order as the root build: the mirror first.
pluginManagement {
    repositories {
        maven {
            name = "MavenCentralGoogleMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        // Google's mirror of Maven Central first: the same files (dependency verification checks
        // their hashes), and CI runners are rarely refused by it, where Maven Central answers 429.
        maven {
            name = "MavenCentralGoogleMirror"
            url = uri("https://maven-central.storage-download.googleapis.com/maven2/")
        }
        mavenCentral()
        gradlePluginPortal()
    }
    versionCatalogs {
        create("libs") {
            from(files("../gradle/libs.versions.toml"))
        }
    }
}

rootProject.name = "build-logic"
include(":convention")
