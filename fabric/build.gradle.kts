plugins {
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project
val fabricPermissionsApiVersion: String by project
val modVersion = project.version.toString()

evaluationDependsOn(":common")

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("me.lucko:fabric-permissions-api:$fabricPermissionsApiVersion")
    include("me.lucko:fabric-permissions-api:$fabricPermissionsApiVersion")

    implementation(project(":common"))
    include(project(":common"))
}

tasks.processResources {
    inputs.property("version", modVersion)

    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}
