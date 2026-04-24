import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.kotlin.dsl.the

plugins {
    java
}

val commonProject = project(":common")
val paperApiVersion: String by project
val mockBukkitVersion: String by project
val mockBukkitPaperApiVersion: String by project
val slf4jVersion = "2.0.16"
val pluginVersion = project.version.toString()

evaluationDependsOn(commonProject.path)

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("io.papermc.paper:paper-api:$mockBukkitPaperApiVersion")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:$mockBukkitVersion")
    testRuntimeOnly("org.slf4j:slf4j-nop:$slf4jVersion")
}

tasks.processResources {
    inputs.property("version", pluginVersion)

    filesMatching("plugin.yml") {
        expand("version" to pluginVersion)
    }
}

tasks.jar {
    dependsOn(commonProject.tasks.named("classes"))
    from(commonProject.the<SourceSetContainer>()["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
