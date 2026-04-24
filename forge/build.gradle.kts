import net.minecraftforge.gradle.ForgeGradleExtension
import net.minecraftforge.gradle.MinecraftExtensionForProject
import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.the

plugins {
    java
    id("net.minecraftforge.gradle")
}

val modId = "clutchperms"
val commonProject = project(":common")
val minecraftVersion: String by project
val minecraftVersionRange: String by project
val forgeVersion: String by project
val forgeVersionRange: String by project
val modVersion = project.version.toString()

evaluationDependsOn(commonProject.path)

val minecraftExtension = extensions.getByType<MinecraftExtensionForProject>()
val forgeGradleExtension = extensions.getByType<ForgeGradleExtension>()

minecraftExtension.runs.configureEach {
    workingDir.set(layout.projectDirectory.dir("run"))
    systemProperty("eventbus.api.strictRuntimeChecks", "true")
    systemProperty("forge.enabledGameTestNamespaces", modId)
}

minecraftExtension.runs.register("server") {
    args("--nogui")
}

minecraftExtension.runs.register("client")

repositories {
    minecraftExtension.mavenizer(this)
    maven(forgeGradleExtension.forgeMaven)
    maven(forgeGradleExtension.minecraftLibsMaven)
}

dependencies {
    implementation(minecraftExtension.dependency("net.minecraftforge:forge:$minecraftVersion-$forgeVersion"))
    implementation(commonProject)
}

tasks.processResources {
    val replaceProperties =
        mapOf(
            "minecraftVersion" to minecraftVersion,
            "minecraftVersionRange" to minecraftVersionRange,
            "forgeVersion" to forgeVersion,
            "forgeVersionRange" to forgeVersionRange,
            "version" to modVersion,
        )

    inputs.properties(replaceProperties)

    filesMatching("META-INF/mods.toml") {
        expand(replaceProperties)
    }
}

tasks.jar {
    dependsOn(commonProject.tasks.named("classes"))
    from(commonProject.the<SourceSetContainer>()["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
