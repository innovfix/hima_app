pluginManagement {
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
//        maven {
//            url = uri("https://maven.juspay.in/jp-build-packages/hyper-sdk/")
//        }
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
        maven(url = "<https://jitpack.io>")

        maven { url = uri("https://maven.zohodl.com") }


        maven {
            url = uri("https://phonepe.mycloudrepo.io/public/repositories/phonepe-intentsdk-android")
        }

        // Snap App Ads Kit
        maven { url = uri("https://storage.googleapis.com/snap-kit-production/maven/") }

        mavenCentral()

//        maven {
//            url = uri("https://maven.juspay.in/jp-build-packages/hyper-sdk/")
//        }

//        maven { url= uri("https://storage.zego.im/maven") }   // <- Add this line.
    }

    }

rootProject.name = "hima"
include(":app")
 