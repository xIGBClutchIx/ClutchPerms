plugins {
    `java-library`
}

val gsonVersion: String by project
val brigadierVersion: String by project

dependencies {
    api("com.mojang:brigadier:$brigadierVersion")
    implementation("com.google.code.gson:gson:$gsonVersion")
}
