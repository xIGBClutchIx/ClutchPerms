plugins {
    java
    id("net.neoforged.moddev")
}

val modId = "clutchperms"
val commonProject = project(":common")
val minecraftVersion: String by project
val minecraftVersionRange: String by project
val neoForgeVersion: String by project
val neoForgeVersionRange: String by project
val modVersion = project.version.toString()

evaluationDependsOn(commonProject.path)

neoForge {
    version = neoForgeVersion

    runs {
        register("server") {
            server()
            programArgument("--nogui")
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }

        register("client") {
            client()
            systemProperty("neoforge.enabledGameTestNamespaces", modId)
        }
    }

    mods {
        register(modId) {
            sourceSet(sourceSets.main.get())
        }
    }
}

dependencies {
    implementation(commonProject)
    add("jarJar", commonProject)
}

tasks.processResources {
    val replaceProperties =
        mapOf(
            "minecraftVersion" to minecraftVersion,
            "minecraftVersionRange" to minecraftVersionRange,
            "neoForgeVersion" to neoForgeVersion,
            "neoForgeVersionRange" to neoForgeVersionRange,
            "version" to modVersion,
        )

    inputs.properties(replaceProperties)

    filesMatching("META-INF/neoforge.mods.toml") {
        expand(replaceProperties)
    }
}
