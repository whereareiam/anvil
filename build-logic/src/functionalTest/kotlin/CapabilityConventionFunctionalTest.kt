package me.whereareiam.anvil.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.util.jar.JarFile

/**
 * Builds a consumer of the `demo` capability family in `builtin-demo` to check what the `module-capability`
 * convention wires from the family folder: the API for compilation, the common code embedded into the root's JAR
 * and described by its sources JAR, the edition side at runtime, and the published artifact IDs of the root and
 * its members.
 */
class CapabilityConventionFunctionalTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `a family root wires its members and publishes them under the built-in prefix`() {
		val project = TestProject(directory)
		writeProject(project)

		val compile = project.dependencies(":consumer", "compileClasspath")
		assertTrue(compile.contains("project ':builtin-demo:demo-api'"), compile)
		assertFalse(compile.contains("demo-common") || compile.contains("demo-java"), compile)
		val runtime = project.dependencies(":consumer", "runtimeClasspath")
		assertTrue(runtime.contains("project ':builtin-demo:demo-api'") && runtime.contains("project ':builtin-demo:demo-java'"), runtime)
		assertFalse(runtime.contains("demo-common"), runtime)

		project.run(":builtin-demo:shadowJar", "publishAllPublicationsToFunctionalRepository")
		// The shaded JAR carries no classifier, unlike the plain, sources and Javadoc JARs beside it.
		val bundle = project.file("builtin-demo/build/libs").listFiles().orEmpty().single { it.name.endsWith("-1.0.0.jar") }
		val entries = JarFile(bundle).use { jar -> jar.entries().asSequence().map { it.name }.toSet() }
		assertTrue("demo/DemoProvider.class" in entries, entries.toString())
		assertTrue("META-INF/services/demo.Provider" in entries, entries.toString())
		assertFalse(entries.any { it.startsWith("demo/Demo.class") || it.startsWith("demo/java/") }, entries.toString())

		val published = project.file("repository-out/me/whereareiam/anvil").list().orEmpty().sorted()
		assertEquals(listOf("builtin-demo", "builtin-demo-api", "builtin-demo-java"), published)
		val sources = project.jarEntries("repository-out/me/whereareiam/anvil/builtin-demo/1.0.0/builtin-demo-1.0.0-sources.jar")
		assertTrue("demo/DemoProvider.java" in sources, sources.toString())

		val reused = project.run(":consumer:dependencies", "--configuration", "runtimeClasspath")
		assertTrue(reused.output.contains("Configuration cache entry reused"), reused.output)
	}

	@Test
	fun `a family rejects foreign members, root code beside common code, catch-all packages and own artifact IDs`() {
		val project = TestProject(directory)
		writeProject(project)

		val settings = project.file("settings.gradle.kts").readText()
		project.write("builtin-demo/demo-extra/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n")
		project.write("settings.gradle.kts", settings + "include(\":builtin-demo:demo-extra\")\n")
		val foreign = project.fail("help")
		assertTrue(foreign.output.contains("found 'demo-extra'"), foreign.output)
		project.write("settings.gradle.kts", settings)
		project.file("builtin-demo/demo-extra").deleteRecursively()

		project.write("builtin-demo/src/main/java/demo/Wiring.java", "package demo;\n\npublic final class Wiring {\n}\n")
		val rootCode = project.fail("help")
		assertTrue(rootCode.output.contains("only wires its members; move the code of its root to 'demo-common'"), rootCode.output)
		project.file("builtin-demo/src/main/java").deleteRecursively()

		project.write("builtin-demo/demo-common/src/main/java/demo/common/Shared.java", "package demo.common;\n\npublic final class Shared {\n}\n")
		val catchAll = project.fail("help")
		assertTrue(catchAll.output.contains("declares the catch-all package 'demo.common' in 'demo-common'"), catchAll.output)
		project.file("builtin-demo/demo-common/src/main/java/demo/common").deleteRecursively()

		val apiBuild = project.file("builtin-demo/demo-api/build.gradle.kts").readText()
		project.write("builtin-demo/demo-api/build.gradle.kts", apiBuild + "\ntoolkitPublish {\n\tartifactId.set(\"demo-api\")\n}\n")
		val renamed = project.fail("help")
		assertTrue(renamed.output.contains("cannot be changed any further"), renamed.output)
	}

	private fun writeProject(project: TestProject) {
		project.write("settings.gradle.kts", """
			dependencyResolutionManagement {
				repositories {
					mavenCentral()
				}
			}

			rootProject.name = "capability-consumer"
			include(":builtin-demo", ":builtin-demo:demo-api", ":builtin-demo:demo-common", ":builtin-demo:demo-java", ":consumer")
		""")
		project.write("build.gradle.kts", """
			allprojects {
				group = "me.whereareiam.anvil"
				version = "1.0.0"
				pluginManager.withPlugin("maven-publish") {
					extensions.configure<PublishingExtension> {
						repositories.maven {
							name = "functional"
							url = uri(rootDir.resolve("repository-out"))
						}
					}
				}
			}
		""")
		// Declares the protocol edition `java`, which makes `demo-java` an edition side of the family.
		project.write("anvil-player/player-protocol/protocol-java/protocol-java-api/build.gradle.kts", "")

		project.write("builtin-demo/build.gradle.kts", """
			plugins {
				id("module-capability")
			}

			description = "Demo capability family"
		""")
		project.write("builtin-demo/src/main/resources/META-INF/services/demo.Provider", "demo.DemoProvider")
		project.write("builtin-demo/demo-api/build.gradle.kts", """
			plugins {
				id("module-api")
			}

			description = "Demo capability API"
		""")
		project.write("builtin-demo/demo-api/src/main/java/demo/Demo.java", "package demo;\n\npublic interface Demo {\n}\n")
		project.write("builtin-demo/demo-common/build.gradle.kts", """
			plugins {
				id("module-java")
			}

			dependencies {
				compileOnly(project(":builtin-demo:demo-api"))
			}
		""")
		project.write("builtin-demo/demo-common/src/main/java/demo/DemoProvider.java", """
			package demo;

			public final class DemoProvider {
				public Demo create() {
					return new Demo() { };
				}
			}
		""")
		project.write("builtin-demo/demo-java/build.gradle.kts", """
			plugins {
				id("module-java")
				id("packaging-publication")
			}

			description = "Demo capability side for the Java Edition protocol"
		""")
		project.write("builtin-demo/demo-java/src/main/java/demo/java/DemoExtension.java", "package demo.java;\n\npublic final class DemoExtension {\n}\n")
		project.write("consumer/build.gradle.kts", """
			plugins {
				id("module-java")
			}

			dependencies {
				implementation(project(":builtin-demo"))
			}
		""")
	}
}
