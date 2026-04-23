import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
    id("com.diffplug.spotless") version "8.4.0"
}

group = "me.clutchy.clutchperms"
version = "0.1.0-SNAPSHOT"

val eclipseJavaFormatterConfig = rootProject.file("eclipse-java-formatter.xml")
val sharedArtifactsDirectory = rootProject.layout.buildDirectory
val distributableProjects = listOf(project(":paper"), project(":fabric"))

allprojects {
    group = rootProject.group
    version = rootProject.version

    repositories {
        mavenCentral()
        maven("https://repo.papermc.io/repository/maven-public/") {
            name = "PaperMC"
        }
        maven("https://maven.fabricmc.net/") {
            name = "Fabric"
        }
    }
}

subprojects {
    pluginManager.withPlugin("base") {
        extensions.configure(BasePluginExtension::class.java) {
            archivesName.set("${rootProject.name}-${project.name}")
        }
    }

    pluginManager.withPlugin("java") {
        extensions.configure(JavaPluginExtension::class.java) {
            toolchain.languageVersion.set(JavaLanguageVersion.of(25))
            withSourcesJar()
        }

        tasks.withType(JavaCompile::class.java).configureEach {
            options.release.set(25)
        }

        dependencies.add("testImplementation", "org.junit.jupiter:junit-jupiter:${property("junitVersion")}")
        dependencies.add(
            "testRuntimeOnly",
            "org.junit.platform:junit-platform-launcher:${property("junitPlatformVersion")}",
        )

        tasks.withType(Test::class.java).configureEach {
            useJUnitPlatform()
        }
    }
}

val collectJars by tasks.registering(Sync::class) {
    group = "build"
    description = "Copies distributable runtime jars into the root build directory."
    into(sharedArtifactsDirectory)

    distributableProjects.forEach { subproject ->
        dependsOn(subproject.tasks.named("jar"))
        from(subproject.layout.buildDirectory.dir("libs")) {
            include("*.jar")
            exclude("*-sources.jar")
        }
    }
}

spotless {
    java {
        target("**/*.java")
        targetExclude("**/build/**")
        eclipse().configFile(eclipseJavaFormatterConfig)
        importOrder("java", "javax", "org", "com", "io", "me", "\\#")
        removeUnusedImports()
        trimTrailingWhitespace()
        endWithNewline()
    }

    kotlinGradle {
        target("*.gradle.kts", "**/*.gradle.kts")
        targetExclude("**/build/**")
        ktlint().editorConfigOverride(
            mapOf(
                "max_line_length" to "180",
            ),
        )
    }

    format("misc") {
        target(
            "*.md",
            ".editorconfig",
            ".gitignore",
            ".vscode/*.json",
            "**/*.properties",
            "**/*.yml",
            "**/*.yaml",
            "**/*.json",
        )
        targetExclude("**/build/**")
        trimTrailingWhitespace()
        endWithNewline()
    }
}

tasks.named("check") {
    dependsOn("spotlessCheck")
}

tasks.named("build") {
    finalizedBy(collectJars)
}
