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
