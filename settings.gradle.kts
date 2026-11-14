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
rootProject.name = "LumeNotes"
include(
    ":app",
    ":core-model",
    ":core-database",
    ":core-data",
    ":core-ui",
    ":feature-editor",
    ":feature-search",
    ":feature-drawing",
    ":feature-settings"
)
