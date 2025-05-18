pluginManagement {
    includeBuild("autoregister")
    repositories {
        maven { url = uri("") }
        gradlePluginPortal()
        google()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = uri("") }
        google()
        mavenCentral()
    }
}

rootProject.name = "AutoRegisterL"
include(":app", ":app_lib", ":app_lib_interface")