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

rootProject.name = "Rovia"

include(":app")

include(":core:model")
include(":core:library-api")
include(":core:playback-api")
include(":core:ui")

include(":data:media-store")
include(":data:database")

include(":playback:media3")

include(":feature:home")
include(":feature:search")
include(":feature:library")
include(":feature:player")
include(":feature:settings")