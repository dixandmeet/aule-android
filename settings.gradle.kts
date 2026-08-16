pluginManagement {
    // build-logic porte les plugins de convention. Il se construit avant tout le
    // reste, ce qui permet aux modules de n'écrire que `alias(libs.plugins.aule…)`.
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
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
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

rootProject.name = "aule-android"

enableFeaturePreview("TYPESAFE_PROJECT_ACCESSORS")

include(":app")
include(":core:common")
include(":core:geo")
include(":core:model")
include(":core:network")
include(":core:designsystem")
include(":core:location")
include(":core:map")
include(":data")
include(":feature:map")
include(":feature:auth")
