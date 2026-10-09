package me.whereareiam.anvil.buildlogic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Builds a Gradle plugin module and a shaded agent to check what the packaging conventions stamp into JARs, and
 * that a published module must describe itself.
 */
class PackagingConventionsFunctionalTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `descriptors and manifests carry the module version and published modules need a description`() {
		val project = TestProject(directory)
		writeProject(project)

		project.run(":plugin:jar", ":agent:jar", ":agent:shadowJar")

		assertEquals("1.2.3", project.manifest("plugin/build/libs/plugin-1.2.3.jar")["Implementation-Version"])
		listOf("agent/build/libs/agent-1.2.3-plain.jar", "agent/build/libs/agent-1.2.3.jar").forEach { jar ->
			assertEquals("1.2.3", project.manifest(jar)["Implementation-Version"], jar)
		}
		val agent = "agent/build/libs/agent-1.2.3.jar"
		assertEquals("name: Agent\nversion: 1.2.3\n", project.jarText(agent, "plugin.yml"))
		assertEquals("name: Agent\nversion: 1.2.3\n", project.jarText(agent, "bungee.yml"))
		assertEquals("version=1.2.3\n", project.jarText(agent, "META-INF/anvil/plugin.properties"))
		assertEquals("{\"id\":\"agent\",\"version\":\"1.2.3\"}\n", project.jarText(agent, "velocity-plugin.json"))
		assertEquals("unrelated: \${version}\n", project.jarText(agent, "config.yml"))

		// A module that sets its own version after applying the convention stamps that version.
		project.run(":versioned:jar")
		val versioned = "versioned/build/libs/versioned-2.0.0.jar"
		assertEquals("2.0.0", project.manifest(versioned)["Implementation-Version"])
		assertEquals("version=2.0.0\n", project.jarText(versioned, "META-INF/anvil/plugin.properties"))

		// A wiring-only bundle publishes what it embeds: consumers compile against its shaded JAR, and its sources and
		// Javadoc JARs hold the embedded project's code, with Lombok's members documented.
		project.run(":wiring:sourcesJar", ":wiring:javadocJar", ":user:compileJava")
		assertTrue("demo/common/Greeter.java" in project.jarEntries("wiring/build/libs/wiring-1.2.3-sources.jar"))
		val javadoc = project.jarText("wiring/build/libs/wiring-1.2.3-javadoc.jar", "demo/common/Greeter.html")
		assertTrue(javadoc.contains("getName()"), javadoc)

		project.write("published/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n\tid(\"packaging-publication\")\n}\n")
		val undescribed = project.fail("help")
		assertTrue(undescribed.output.contains(":published is published and must declare a description"), undescribed.output)
	}

	private fun writeProject(project: TestProject) {
		project.write("settings.gradle.kts", """
			dependencyResolutionManagement {
				repositories {
					mavenCentral()
				}
			}

			rootProject.name = "packaging-consumer"
			include(":plugin", ":agent", ":published", ":versioned", ":common", ":wiring", ":user")
		""")
		project.write("build.gradle.kts", """
			allprojects {
				group = "me.whereareiam.anvil"
				version = "1.2.3"
			}
		""")
		project.write("plugin/build.gradle.kts", """
			plugins {
				id("module-gradle-plugin")
			}

			description = "Demo Gradle plugin"
		""")
		project.write("agent/build.gradle.kts", """
			plugins {
				id("module-java")
				id("packaging-shaded-jar")
				id("packaging-version-stamp")
			}
		""")
		project.write("agent/src/main/java/demo/agent/Agent.java", "package demo.agent;\n\npublic final class Agent {\n}\n")
		project.write("agent/src/main/resources/plugin.yml", "name: Agent\nversion: \${version}\n")
		project.write("agent/src/main/resources/bungee.yml", "name: Agent\nversion: \${version}\n")
		project.write("agent/src/main/resources/META-INF/anvil/plugin.properties", "version=\${version}\n")
		project.write("agent/src/main/resources/velocity-plugin.json", "{\"id\":\"agent\",\"version\":\"\${version}\"}\n")
		project.write("agent/src/main/resources/config.yml", "unrelated: \${version}\n")
		project.write("published/build.gradle.kts", """
			plugins {
				id("module-java")
				id("packaging-publication")
			}

			description = "Demo published module"
		""")
		project.write("versioned/build.gradle.kts", """
			plugins {
				id("module-java")
				id("packaging-version-stamp")
			}

			version = "2.0.0"
		""")
		project.write("versioned/src/main/resources/META-INF/anvil/plugin.properties", "version=\${version}\n")

		project.write("common/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n")
		project.write("common/src/main/java/demo/common/Greeter.java", """
			package demo.common;

			/**
			 * Greets by name.
			 */
			@lombok.Value
			public class Greeter {
				/**
				 * Name to greet.
				 */
				String name;
			}
		""")
		project.write("wiring/build.gradle.kts", """
			plugins {
				id("module-java")
				id("packaging-publication")
				id("packaging-shaded-root")
			}

			description = "Demo wiring bundle"

			dependencies {
				embedded(project(":common")) { isTransitive = false }
			}
		""")
		project.write("user/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n\ndependencies {\n\timplementation(project(\":wiring\"))\n}\n")
		project.write("user/src/main/java/demo/user/User.java", """
			package demo.user;

			public final class User {
				public String greet() {
					return new demo.common.Greeter("Alice").getName();
				}
			}
		""")
	}
}
