import me.whereareiam.anvil.buildlogic.module.CheckSharedTestDoubles
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

// Contracts whose test doubles are shared through test fixtures: a test uses the fixture, never a fake of its own. The
// modules that own a contract or its fixture come before the fixture, so their own tests of it are exempt.
data class SharedTestDouble(val fixture: String, val owners: Set<String>)

val sharedTestDoubleContracts = mapOf(
	"me.whereareiam.anvil.protocol.api.channel.ProtocolChannel"
		to SharedTestDouble("player-api's TestProtocolChannel", setOf(":anvil-player:player-api")),
	"me.whereareiam.anvil.capability.protocol.api.player.ProtocolPlayerCapabilityContext"
		to SharedTestDouble("capability-player-api's TestProtocolPlayerCapabilityContext",
			setOf(":anvil-capability:capability-player:capability-player-api")),
	"me.whereareiam.anvil.protocol.api.worker.PlayerBindingContext"
		to SharedTestDouble("player-worker's TestWorkerPlayer or protocol-java-worker's TestJavaPlayer",
			setOf(":anvil-player:player-api", ":anvil-player:player-worker")),
	"me.whereareiam.anvil.protocol.api.worker.OperationRegistry"
		to SharedTestDouble("player-worker's TestOperations", setOf(":anvil-player:player-api", ":anvil-player:player-worker")),
	"me.whereareiam.anvil.protocol.java.api.JavaProtocol"
		to SharedTestDouble("protocol-java-worker's TestJavaPlayer", setOf(
			":anvil-player:player-protocol:protocol-java:protocol-java-api",
			":anvil-player:player-protocol:protocol-java:protocol-java-worker",
		)),
)

val sharedTestDoubles = tasks.register<CheckSharedTestDoubles>("checkSharedTestDoubles") {
	group = LifecycleBasePlugin.VERIFICATION_GROUP
	description = "Verifies that tests use the shared test doubles instead of implementing their contracts themselves."
	classes.from(testing.suites.withType<JvmTestSuite>().map { suite -> suite.sources.output.classesDirs })
	contracts.putAll(sharedTestDoubleContracts.filterValues { project.path !in it.owners }.mapValues { it.value.fixture })
	report.set(layout.buildDirectory.file("reports/test-doubles/shared-test-doubles.txt"))
}

tasks.named("check") {
	dependsOn(sharedTestDoubles)
}
