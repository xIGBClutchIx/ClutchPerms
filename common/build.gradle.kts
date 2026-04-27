plugins {
    `java-library`
}

val gsonVersion: String by project
val brigadierVersion: String by project
val hikariCpVersion: String by project
val sqliteJdbcVersion: String by project

dependencies {
    api("com.mojang:brigadier:$brigadierVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
    implementation("com.zaxxer:HikariCP:$hikariCpVersion")
    testRuntimeOnly("org.xerial:sqlite-jdbc:$sqliteJdbcVersion")
    testRuntimeOnly("org.slf4j:slf4j-nop:2.0.16")
}
