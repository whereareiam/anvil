import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	`kotlin-dsl`
	`jvm-test-suite`
}

val javaVersion = libs.versions.java.asProvider().get().toInt()

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
	implementation("me.whereareiam.anvil.buildlogic:build-logic-settings")
	implementation(kotlin("gradle-plugin"))
	implementation(libs.asm)
	implementation(libs.intellij.platform.plugin)
	implementation(libs.jackson.toml)
	implementation(libs.lombok.plugin)
	implementation(libs.shadow)
	implementation(libs.toolkit.architecture)
	implementation(libs.toolkit.publish.maven)
}

// The segment conventions run the segment linkage tool of a library's common project, so the functional tests build
// the consumer's MCProtocolLib common project from the tool's own sources.
val linkageToolRoot = layout.settingsDirectory.dir("../anvil-player/player-library/library-mcprotocol/mcprotocol-common/src/main")
val linkageToolSources = fileTree(linkageToolRoot) {
	include("java/me/whereareiam/anvil/player/library/mcprotocol/segment/SegmentLinkage.java")
	include("java/me/whereareiam/anvil/player/library/mcprotocol/segment/SegmentLinkageTool.java")
	include("java/me/whereareiam/anvil/player/library/mcprotocol/segment/linkage/**")
	include("resources/META-INF/services/java.util.spi.ToolProvider")
}
// The tool's sources read Lombok's accessors in the repository's one style, which its root lombok.config sets.
val linkageToolLombokConfig = layout.settingsDirectory.file("../lombok.config")

testing {
	suites {
		named<JvmTestSuite>("test") {
			useJUnitJupiter(libs.versions.junit)
		}

		// Builds temporary consumer projects with the conventions. Library releases come from file repositories
		// that the tests generate, so the projects download only the catalog's own dependencies.
		register<JvmTestSuite>("functionalTest") {
			useJUnitJupiter(libs.versions.junit)
			dependencies {
				implementation(gradleTestKit())
				implementation(libs.asm)
			}
			targets.configureEach {
				testTask.configure {
					jvmArgumentProviders.add(VersionCatalogArgument(layout.settingsDirectory.file("../gradle/libs.versions.toml")))
					jvmArgumentProviders.add(LinkageToolSources(linkageToolRoot, linkageToolSources, linkageToolLombokConfig))
				}
			}
		}
	}
}

gradlePlugin {
	testSourceSets(sourceSets["functionalTest"])
}

tasks.withType<Test>().configureEach {
	testLogging {
		events("failed", "skipped")
		exceptionFormat = TestExceptionFormat.FULL
	}
}

tasks.named("check") {
	dependsOn(tasks.named("functionalTest"))
}

/**
 * Passes the build's version catalog to the functional tests, which copy it into their consumer projects.
 * The catalog's content is the input and its location is not, so the build cache stays relocatable.
 */
class VersionCatalogArgument(
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	val catalog: RegularFile,
) : CommandLineArgumentProvider {
	override fun asArguments(): Iterable<String> = listOf("-Danvil.buildlogic.catalog=${catalog.asFile.absolutePath}")
}

/**
 * Passes the sources of the segment linkage tool to the functional tests, which copy them into their consumer's
 * library common project: the root they are relative to, the files and the Lombok configuration they are written
 * for, which the consumer's root holds as the repository's does. The files' content is the input and their location
 * is not, so the build cache stays relocatable.
 */
class LinkageToolSources(
	@get:Internal
	val root: Directory,
	@get:InputFiles
	@get:PathSensitive(PathSensitivity.RELATIVE)
	val sources: FileTree,
	@get:InputFile
	@get:PathSensitive(PathSensitivity.NONE)
	val lombokConfig: RegularFile,
) : CommandLineArgumentProvider {
	override fun asArguments(): Iterable<String> = listOf(
		"-Danvil.buildlogic.linkageTool.root=${root.asFile.absolutePath}",
		"-Danvil.buildlogic.linkageTool.files=" + sources.files.joinToString(File.pathSeparator) { it.absolutePath },
		"-Danvil.buildlogic.linkageTool.lombokConfig=${lombokConfig.asFile.absolutePath}",
	)
}
