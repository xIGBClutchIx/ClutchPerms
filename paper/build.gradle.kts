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

dependencies {
    implementation(project(":common"))
    compileOnly("io.papermc.paper:paper-api:$paperApiVersion")
    testImplementation("io.papermc.paper:paper-api:$mockBukkitPaperApiVersion")
    testImplementation("org.mockbukkit.mockbukkit:mockbukkit-v1.21:$mockBukkitVersion")
}

tasks.processResources {
    inputs.property("version", project.version)

    filesMatching("plugin.yml") {
        expand("version" to project.version)
    }
}

tasks.jar {
    dependsOn(commonProject.tasks.named("classes"))
    from(commonProject.the<SourceSetContainer>()["main"].output)
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}
