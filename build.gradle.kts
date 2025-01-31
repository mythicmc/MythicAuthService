import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
    java
    kotlin("jvm") version "2.0.10"
    id("com.gradleup.shadow") version "8.3.0"
}

group = "org.mythicmc"
version = "1.0.3"

description = "Bukkit plugin which provides a Redis API to validate credentials with TELogin."

repositories {
    mavenCentral()
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.codemc.org/repository/maven-public/") }
}

dependencies {
    implementation("redis.clients:jedis:5.2.0")
    compileOnly("net.luckperms:api:5.4")
    compileOnly("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    // TELogin and DbShare
    compileOnly(fileTree(mapOf("dir" to "lib", "include" to listOf("*.jar"))))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
}

tasks.withType<ShadowJar> {
    minimize()
    archiveClassifier.set("")
    relocate("kotlin", "org.mythicmc.mythicauthservice.kotlin")
    // Relocate Jedis and its dependencies
    relocationPrefix = "org.mythicmc.mythicauthservice.shadow"
    isEnableRelocation = true
}
