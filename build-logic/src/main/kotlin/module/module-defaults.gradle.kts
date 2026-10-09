import me.whereareiam.anvil.buildlogic.module.JavaRelease
import me.whereareiam.anvil.buildlogic.module.catalog
import me.whereareiam.anvil.buildlogic.module.featureVersion
import me.whereareiam.anvil.buildlogic.module.library
import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
	`java-library`
	id("me.whereareiam.toolkit.architecture")
}

// Compilation and test defaults of every JVM module, whatever its source language. Java modules apply `module-java`,
// which adds Lombok; Kotlin-only modules, such as Gradle plugins, apply this convention directly.
val javaVersion = catalog.featureVersion("java")
val javaRelease = extensions.create<JavaRelease>("javaRelease")
javaRelease.main.convention(javaVersion)
javaRelease.legacy.convention(catalog.featureVersion("java-legacy"))

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

tasks.withType<JavaCompile>().configureEach {
	options.release.set(javaVersion)
	options.encoding = "UTF-8"
	options.compilerArgs.add("-parameters")
}

// Registered after the general rule, so the main release wins for compileJava only.
tasks.named<JavaCompile>("compileJava") {
	options.release.set(javaRelease.main)
}

plugins.withId("org.jetbrains.kotlin.jvm") {
	extensions.configure<KotlinJvmProjectExtension> {
		compilerOptions.jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
	}
}

dependencies {
	compileOnly(catalog.library("annotations"))

	testCompileOnly(catalog.library("annotations"))
	testImplementation(catalog.library("junit-jupiter"))
	testRuntimeOnly(catalog.library("junit-platform"))
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	testLogging {
		events("failed", "skipped")
		exceptionFormat = TestExceptionFormat.FULL
	}
}
