import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
	`kotlin-dsl`
	`jvm-test-suite`
}

// The main build logic depends on these settings conventions by this coordinate.
group = "me.whereareiam.anvil.buildlogic"

val javaVersion = libs.versions.java.asProvider().get().toInt()

java {
	toolchain.languageVersion.set(JavaLanguageVersion.of(javaVersion))
}

kotlin {
	compilerOptions {
		jvmTarget.set(JvmTarget.fromTarget(javaVersion.toString()))
	}
}

testing {
	suites {
		named<JvmTestSuite>("test") {
			useJUnitJupiter(libs.versions.junit)
		}
	}
}

tasks.withType<Test>().configureEach {
	testLogging {
		events("failed", "skipped")
		exceptionFormat = TestExceptionFormat.FULL
	}
}
