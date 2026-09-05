import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestExceptionFormat

val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")

dependencies {
	add("testImplementation", libs.findLibrary("junit-jupiter").get())
	add("testRuntimeOnly", libs.findLibrary("junit-platform").get())
}

tasks.withType<Test>().configureEach {
	useJUnitPlatform()
	testLogging {
		events("failed", "skipped")
		exceptionFormat = TestExceptionFormat.FULL
	}
}
