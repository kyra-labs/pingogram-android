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
        // The TDLib AAR, and nothing else from here: the pingogram.tdlib plugin (build-logic PrebuiltAar.TDLIB)
        // downloads it into this git-ignored directory when :core:tdlib's dependencies are resolved (docs/TDLIB.md).
        exclusiveContent {
            forRepository {
                maven {
                    name = "tdlib"
                    url = uri("core/tdlib/libs/maven")
                }
            }
            filter { includeGroup("org.drinkless") }
        }
        // The call engine's AAR (NTgCalls), and nothing else from here: the pingogram.ntgcalls plugin (build-logic
        // PrebuiltAar.NTGCALLS) installs it like TDLib's; its group never comes from Maven Central
        // (docs/adr/0015-call-media-engine.md).
        exclusiveContent {
            forRepository {
                maven {
                    name = "ntgcalls"
                    url = uri("feature/calls/libs/maven")
                }
            }
            filter { includeGroup("io.github.pytgcalls") }
        }
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
include(":feature:scanner")
include(":feature:settings")
