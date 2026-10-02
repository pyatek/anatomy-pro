rootProject.name = "AnatomyPro"

pluginManagement {
    includeBuild("build-logic")

    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google {
            mavenContent {
                includeGroupAndSubgroups("androidx")
                includeGroupAndSubgroups("com.android")
                includeGroupAndSubgroups("com.google")
            }
        }
        mavenCentral()
    }
}

include(":androidApp")
include(":ios-renderer")
include(":shared")
include(":shared:core-model")
include(":shared:core-data")
include(":shared:core-data-fake")
include(":shared:feature-atlas")
include(":shared:feature-search")
include(":shared:feature-settings")
include(":shared:renderer-api")
include(":shared:renderer-filament")
include(":shared:core-designsystem")
include(":shared:core-navigation")