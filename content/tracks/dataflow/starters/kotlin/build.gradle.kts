plugins {
    kotlin("jvm") version "2.4.20"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    implementation(fileTree("lib"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("MainKt")
}
