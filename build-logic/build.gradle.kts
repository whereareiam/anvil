import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    `kotlin-dsl`
}

val javaVersion = 21

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
    toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
    }
}

repositories {
    mavenLocal()
    gradlePluginPortal()
    mavenCentral()
    maven("https://registry.whereareiam.me/maven/packages")
}

dependencies {
	implementation(kotlin("gradle-plugin"))
	implementation(libs.lombok.plugin)
    implementation(libs.shadow)
    implementation(libs.toolkit.architecture)
    implementation(libs.toolkit.publish.maven)
}
