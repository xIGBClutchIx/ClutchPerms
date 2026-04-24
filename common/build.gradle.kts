plugins {
    `java-library`
}

val gsonVersion: String by project

dependencies {
    implementation("com.google.code.gson:gson:$gsonVersion")
}
