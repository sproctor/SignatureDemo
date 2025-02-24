import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.compose)
    alias(libs.plugins.compose.compiler)
}

group = "com.seanproctor"
version = "1.0-SNAPSHOT"

repositories {
    google()
    mavenCentral()
}

kotlin {
    jvmToolchain {
        languageVersion = JavaLanguageVersion.of(17)
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
    implementation(compose.desktop.currentOs)
    implementation(libs.signaturepad)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.coroutines.swing)
}

tasks.wrapper {
    gradleVersion = "8.10.2"
}

compose.desktop {
    application {
        mainClass = "MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "WacomDemo"
            packageVersion = "1.0.0"
        }
        if (System.getProperty("os.name").lowercase().contains("windows")) {
            jvmArgs.add("-Djava.library.path=$projectDir/jniLibs/windows-amd64")
        } else {
            jvmArgs.add("-Djava.library.path=$projectDir/jniLibs/linux-amd64")
        }
    }
}
