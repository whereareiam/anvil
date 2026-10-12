import me.whereareiam.anvil.buildlogic.module.JavaRelease
import me.whereareiam.anvil.buildlogic.module.SharedImplementations
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

// A shared implementation reaches an implementation's main compilation only through this configuration, outside the
// configurations the toolkit's architecture verifier reads, because implementations otherwise depend only on APIs;
// verifySharedImplementations confines it instead. Tests see it like any implementation dependency.
val sharedImplementation = configurations.dependencyScope(SharedImplementations.configuration)
val sharedImplementationClasspath = configurations.resolvable("sharedImplementationClasspath") {
	extendsFrom(sharedImplementation.get())
	attributes {
		attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage.JAVA_API))
		attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.LIBRARY))
		attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements.CLASSES))
		attribute(Bundling.BUNDLING_ATTRIBUTE, objects.named(Bundling.EXTERNAL))
		attributeProvider(TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE, javaRelease.main)
	}
}

sourceSets.named("main") {
	compileClasspath += files(sharedImplementationClasspath)
}

configurations.named("testImplementation") {
	extendsFrom(sharedImplementation.get())
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
