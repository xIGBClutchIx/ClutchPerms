pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
        maven("https://maven.minecraftforge.net/") {
            name = "MinecraftForge"
        }
        gradlePluginPortal()
        mavenCentral()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
    id("net.fabricmc.fabric-loom") version "1.16-SNAPSHOT" apply false
    id("net.minecraftforge.gradle") version "7.0.25" apply false
    id("net.neoforged.moddev") version "2.0.141" apply false
}

rootProject.name = "clutchperms"

include("common")
include("paper")
include("fabric")
include("neoforge")
include("forge")
