pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories {
        google {
            content {
                includeGroupByRegex("com[.]android.*")
                includeGroupByRegex("com[.]google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "petkit-local-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:domain")
include(":core:data")
include(":core:network")
include(":core:database")
include(":core:datastore")
include(":core:designsystem")
include(":core:ui")
include(":core:testing")
include(":core:e2e")
include(":feature:auth")
include(":feature:home")
include(":feature:device")
include(":feature:history")
include(":feature:pets")
include(":feature:live")
include(":feature:settings")
include(":push")
