pluginManagement {
    val versionKotlin: String by settings
    val versionGmazzoBuildconfig: String by settings
    val versionTouchportal: String by settings

    plugins {
        kotlin("jvm") version versionKotlin
        kotlin("kapt") version versionKotlin
        kotlin("plugin.serialization") version versionKotlin // Serialization Plugin should match Kotlin Version

        id("com.github.gmazzo.buildconfig") version versionGmazzoBuildconfig
        id("com.christophecvb.touchportal.plugin-packager") version versionTouchportal
    }

    repositories {
        mavenLocal()
        mavenCentral()
        gradlePluginPortal()
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.5.0"
}

rootProject.name = "bleatkan"

include("lib")

