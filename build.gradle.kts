plugins {
    kotlin("jvm") version "2.1.10" apply false
    kotlin("multiplatform") version "2.1.10" apply false
    id("io.ktor.plugin") version "3.2.2" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.1.10" apply false
}

subprojects {
    repositories {
        mavenCentral()
        maven { url = uri("https://maven.pkg.jetbrains.space/kotlin/p/kotlin/kotlin-js-wrappers") }
    }

    group = "dev.ixor"
    version = "0.0.1"
}
