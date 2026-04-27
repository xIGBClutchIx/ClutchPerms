import org.gradle.api.file.DuplicatesStrategy

plugins {
    id("net.fabricmc.fabric-loom")
}

val minecraftVersion: String by project
val loaderVersion: String by project
val fabricApiVersion: String by project
val fabricPermissionsApiVersion: String by project
val hikariCpVersion: String by project
val sqliteJdbcVersion: String by project
val modVersion = project.version.toString()
val bundledLibraries by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

evaluationDependsOn(":common")

dependencies {
    minecraft("com.mojang:minecraft:$minecraftVersion")
    implementation("net.fabricmc:fabric-loader:$loaderVersion")
    implementation("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")
    implementation("me.lucko:fabric-permissions-api:$fabricPermissionsApiVersion")
    include("me.lucko:fabric-permissions-api:$fabricPermissionsApiVersion")
    implementation("com.zaxxer:HikariCP:$hikariCpVersion")
    implementation("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
    bundledLibraries("com.zaxxer:HikariCP:$hikariCpVersion")
    bundledLibraries("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")

    implementation(project(":common"))
    include(project(":common"))
}

tasks.processResources {
    inputs.property("version", modVersion)

    filesMatching("fabric.mod.json") {
        expand("version" to modVersion)
    }
}

tasks.jar {
    from({
        bundledLibraries.map { zipTree(it) }
    }) {
        exclude("fabric.mod.json", "plugin.yml", "META-INF/mods.toml", "META-INF/neoforge.mods.toml")
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
        exclude("module-info.class", "META-INF/versions/**/module-info.class")
    }
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
}

tasks.test {
    dependsOn(tasks.jar)
    systemProperty("clutchperms.jar", tasks.jar.get().archiveFile.get().asFile.absolutePath)
}
