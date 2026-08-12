pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://repo.papermc.io/repository/maven-public/") { name = "Paper" }
        maven("https://maven.enginehub.org/repo/") { name = "EngineHub" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
        mavenCentral()
        gradlePluginPortal()
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.9.7"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    // Loom + loom-back-compat are only applied in the `fabric` branch. Declaring them
    // here (apply false) puts them on the classpath via pluginManagement repositories
    // so node buildscripts can resolve them without their own repository blocks.
    id("dev.kikugie.loom-back-compat") version "0.4.2" apply false
    id("net.fabricmc.fabric-loom") version "1.17-SNAPSHOT" apply false
    id("net.fabricmc.fabric-loom-remap") version "1.17-SNAPSHOT" apply false
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        maven("https://repo.papermc.io/repository/maven-public/") { name = "Paper" }
        maven("https://maven.enginehub.org/repo/") { name = "EngineHub" }
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        mavenCentral()
    }
}

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) {
        // Supported Minecraft versions. Add "26.1" and "1.21.11" in later phases.
        versions("26.2")
        vcsVersion = "26.2"

        branch("common")
        branch("fabric")
        branch("paper")
    }
}

rootProject.name = "safe-zone"
