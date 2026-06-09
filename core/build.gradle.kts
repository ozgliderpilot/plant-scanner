// Standalone, Android-free Kotlin/JVM build that holds ALL business logic.
// It is consumed by the Android app via a Gradle composite build (see root settings.gradle.kts),
// and can be compiled + unit-tested on any JDK with no Android SDK present.

plugins {
    kotlin("jvm") version "1.9.24"
}

group = "com.nursery"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    testImplementation(kotlin("test-junit5"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.10.2")
}

// Target JVM 17 bytecode while running on whatever JDK is installed (no toolchain auto-download).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions { jvmTarget = "17" }
}

tasks.test {
    useJUnitPlatform()
    testLogging { events("passed", "failed", "skipped") }
}
