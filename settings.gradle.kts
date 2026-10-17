pluginManagement {
    includeBuild("build-logic")
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
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
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
    }
}

rootProject.name = "pingogram"

// Also a regular included build, so `libs.pingogram.detekt.rules` resolves to build-logic/detekt-rules.
includeBuild("build-logic")

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":benchmark")

include(":core:common")
include(":core:data")
include(":core:datastore")
include(":core:domain")
include(":core:navigation")
include(":core:notifications")
include(":core:tdlib")
include(":core:testing")
include(":core:ui")

include(":feature:auth")
include(":feature:chat")
include(":feature:chats")
include(":feature:contacts")
include(":feature:media")
include(":feature:profile")
include(":feature:settings")
