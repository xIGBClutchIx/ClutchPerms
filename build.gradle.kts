import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.api.plugins.BasePluginExtension
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.provider.ProviderFactory
import org.gradle.api.tasks.Sync
import org.gradle.api.tasks.bundling.AbstractArchiveTask
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.TestDescriptor
import org.gradle.api.tasks.testing.TestListener
import org.gradle.api.tasks.testing.TestOutputEvent
import org.gradle.api.tasks.testing.TestOutputListener
import org.gradle.api.tasks.testing.TestResult
import org.gradle.api.tasks.testing.TestResult.ResultType
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    base
    id("com.diffplug.spotless") version "8.4.0"
}

fun ProviderFactory.booleanGradleProperty(name: String, defaultValue: Boolean) = gradleProperty(name).map { it.equals("true", ignoreCase = true) }.orElse(defaultValue)

class TestLogPalette(private val enabled: Boolean) {
    fun task(text: String) = style("\u001B[1;36m", text)

    fun started(text: String) = style("\u001B[36m", text)

    fun passed(text: String) = style("\u001B[32m", text)

    fun skipped(text: String) = style("\u001B[33m", text)

    fun failed(text: String) = style("\u001B[31m", text)

    fun dim(text: String) = style("\u001B[2m", text)

    private fun style(code: String, text: String): String = if (enabled) "$code$text\u001B[0m" else text
}

fun formatTestName(descriptor: TestDescriptor): String {
    val className = descriptor.className?.substringAfterLast('.')
    val displayName = descriptor.displayName.takeUnless { it == className }

    return listOfNotNull(className, displayName).joinToString(" > ").ifEmpty { descriptor.displayName }
}

group = "me.clutchy.clutchperms"
version = "0.1.0-SNAPSHOT"

val eclipseJavaFormatterConfig = rootProject.file("eclipse-java-formatter.xml")
val sharedArtifactsDirectory = rootProject.layout.buildDirectory
val distributableProjects = listOf(project(":paper"), project(":fabric"), project(":neoforge"), project(":forge"))
val defaultColoredTestLogging =
    gradle.startParameter.consoleOutput != ConsoleOutput.Plain &&
        System.getenv("TERM") != "dumb"
// Use -PverboseTests=false to disable per-test lifecycle logs and captured stdout/stderr.
val verboseTestLogging = providers.booleanGradleProperty("verboseTests", true)
// Use -PcolorTests=false to disable ANSI colors in the custom test logger.
val coloredTestLogging = providers.booleanGradleProperty("colorTests", defaultColoredTestLogging)

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
        maven("https://maven.neoforged.net/releases") {
            name = "NeoForge"
        }
        maven("https://maven.minecraftforge.net/") {
            name = "MinecraftForge"
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
            // MockBukkit currently brings Byte Buddy code that triggers JDK 24+/25 Unsafe warnings during test bootstrap.
            jvmArgs("--sun-misc-unsafe-memory-access=allow")

            val verboseLifecycleLogging = verboseTestLogging.get()
            val palette = TestLogPalette(coloredTestLogging.get())
            val testTaskPath = path

            testLogging {
                exceptionFormat = TestExceptionFormat.FULL
                showExceptions = true
                showCauses = true
                showStackTraces = true

                if (!verboseLifecycleLogging) {
                    events(TestLogEvent.FAILED, TestLogEvent.SKIPPED)
                }
            }

            addTestListener(
                object : TestListener {
                    override fun beforeSuite(suite: TestDescriptor) = Unit

                    override fun beforeTest(testDescriptor: TestDescriptor) {
                        if (!verboseLifecycleLogging) {
                            return
                        }

                        logger.lifecycle("${palette.task(testTaskPath)} > ${formatTestName(testDescriptor)} ${palette.started("STARTED")}")
                    }

                    override fun afterTest(testDescriptor: TestDescriptor, result: TestResult) {
                        if (!verboseLifecycleLogging) {
                            return
                        }

                        val testLabel = "${palette.task(testTaskPath)} > ${formatTestName(testDescriptor)}"

                        when (result.resultType) {
                            ResultType.SUCCESS -> logger.lifecycle("$testLabel ${palette.passed("PASSED")}")

                            ResultType.SKIPPED -> logger.lifecycle("$testLabel ${palette.skipped("SKIPPED")}")

                            ResultType.FAILURE -> {
                                logger.lifecycle("$testLabel ${palette.failed("FAILED")}")
                                result.exceptions.filterNotNull().forEach { throwable ->
                                    logger.lifecycle(palette.failed(throwable.stackTraceToString().trimEnd()))
                                }
                            }
                        }
                    }

                    override fun afterSuite(suite: TestDescriptor, result: TestResult) {
                        if (suite.parent != null) {
                            return
                        }

                        val durationMillis = result.endTime - result.startTime
                        val runCount = palette.dim("${result.testCount} run")
                        val passedCount = if (result.successfulTestCount > 0) palette.passed("${result.successfulTestCount} passed") else palette.dim("0 passed")
                        val failedCount = if (result.failedTestCount > 0) palette.failed("${result.failedTestCount} failed") else palette.dim("0 failed")
                        val skippedCount = if (result.skippedTestCount > 0) palette.skipped("${result.skippedTestCount} skipped") else palette.dim("0 skipped")
                        logger.lifecycle(
                            "${palette.task("Test summary for $testTaskPath")}: $runCount, $passedCount, $failedCount, $skippedCount in ${palette.dim("${durationMillis}ms")}",
                        )
                    }
                },
            )

            if (verboseLifecycleLogging) {
                addTestOutputListener(
                    object : TestOutputListener {
                        override fun onOutput(testDescriptor: TestDescriptor, event: TestOutputEvent) {
                            val output = event.message.trimEnd()
                            if (output.isEmpty()) {
                                return
                            }

                            val streamLabel =
                                when (event.destination) {
                                    TestOutputEvent.Destination.StdOut -> palette.dim("[stdout]")
                                    TestOutputEvent.Destination.StdErr -> palette.failed("[stderr]")
                                }
                            val testLabel = "${palette.task(testTaskPath)} > ${formatTestName(testDescriptor)}"
                            output.lineSequence().filter { it.isNotBlank() }.forEach { line ->
                                logger.lifecycle("$testLabel $streamLabel $line")
                            }
                        }
                    },
                )
            }
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
