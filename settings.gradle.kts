pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "plant-scanner"

// The pure-Kotlin logic lives in its own standalone build; pull it in as a composite build.
// The :app module depends on "com.nursery:core" and Gradle substitutes it with this included build.
includeBuild("core")

include(":app")
