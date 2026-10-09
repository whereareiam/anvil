package me.whereareiam.anvil.buildlogic

import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.DataInputStream
import java.io.File

/**
 * Builds legacy Java modules to check that their main classes target the legacy Java release while tests keep the
 * build's release, that the release is verified, and that a legacy Java bundle cannot embed a newer project.
 */
class LegacyJavaConventionFunctionalTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `main classes run on the legacy Java release, tests do not, and newer classes fail the build`() {
		val project = TestProject(directory)
		writeProject(project)

		val build = project.run("build")
		assertEquals(TaskOutcome.SUCCESS, build.task(":server:checkClassRelease")?.outcome, build.output)
		// Only compilations meant for the legacy Java release get a check.
		assertNull(build.task(":server:checkTestClassRelease"), build.output)
		assertNull(build.task(":host:checkClassRelease"), build.output)
		assertEquals(55, majorVersion(project.file("server/build/classes/java/main/demo/server/Agent.class")))
		assertEquals(65, majorVersion(project.file("server/build/classes/java/test/demo/server/AgentTest.class")))
		assertTrue(project.file("server/build/reports/class-release/main.txt").readText().startsWith("1 classes checked against Java 11"))

		project.write("server/build.gradle.kts", project.file("server/build.gradle.kts").readText() + """
			tasks.compileJava {
				options.release.set(21)
			}
		""")
		val newer = project.fail(":server:check")
		assertTrue(newer.output.contains("Classes must run on Java 11 (class file version 55 or older), but these are newer:"), newer.output)
		assertTrue(newer.output.contains("demo/server/Agent.class (class file version 65, Java 21)"), newer.output)

		project.write("bundled/build.gradle.kts", project.file("bundled/build.gradle.kts").readText().replace(":server", ":host"))
		val embedded = project.fail(":bundled:shadowJar")
		assertTrue(embedded.output.contains(
			"Dependency resolution is looking for a library compatible with JVM runtime version 11, but 'project ':host'' "
				+ "is only compatible with JVM runtime version 21 or newer."
		), embedded.output)
	}

	private fun writeProject(project: TestProject) {
		project.write("settings.gradle.kts", """
			dependencyResolutionManagement {
				repositories {
					mavenCentral()
				}
			}

			rootProject.name = "legacy-java-consumer"
			include(":server", ":host", ":bundled")
		""")
		project.write("build.gradle.kts", """
			allprojects {
				group = "me.whereareiam.anvil"
				version = "1.0.0"
			}
		""")
		project.write("server/build.gradle.kts", """
			plugins {
				id("module-java")
				id("module-java-legacy")
			}
		""")
		project.write("server/src/main/java/demo/server/Agent.java", "package demo.server;\n\npublic final class Agent {\n}\n")
		project.write("server/src/test/java/demo/server/AgentTest.java", """
			package demo.server;

			import org.junit.jupiter.api.Test;

			class AgentTest {
				@Test
				void loads() {
					new Agent();
				}
			}
		""")
		project.write("host/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n")
		project.write("host/src/main/java/demo/host/Host.java", "package demo.host;\n\npublic final class Host {\n}\n")
		project.write("bundled/build.gradle.kts", """
			plugins {
				id("module-java")
				id("module-java-legacy")
				id("packaging-shaded-jar")
			}

			dependencies {
				embedded(project(":server"))
			}
		""")
	}

	private fun majorVersion(file: File): Int = DataInputStream(file.inputStream()).use { input ->
		input.readInt()
		input.readUnsignedShort()
		input.readUnsignedShort()
	}
}
