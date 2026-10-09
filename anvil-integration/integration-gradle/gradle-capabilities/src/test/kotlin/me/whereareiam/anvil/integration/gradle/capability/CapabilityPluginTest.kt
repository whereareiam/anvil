package me.whereareiam.anvil.integration.gradle.capability

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.writeText

class CapabilityPluginTest {
	@TempDir
	lateinit var projectDirectory: Path

	@Test
	fun `session plugin adds capability wiring to the anvil source set`() {
		projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"capability-fixture\"")
		projectDirectory.resolve("build.gradle.kts").writeText("""
			plugins {
				id("me.whereareiam.anvil.capability.session")
			}
			check(configurations.names.none { it in setOf("anvilCapabilities", "anvilUnits") })
			tasks.register("verify") {
				doLast {
					val dependencies = configurations.getByName("anvilImplementation").allDependencies
						.map { it.name }
					check("builtin-session" in dependencies)
				}
			}
		""".trimIndent())

		val result = GradleRunner.create()
			.withProjectDir(projectDirectory.toFile())
			.withPluginClasspath()
			.withArguments("verify")
			.build()
		assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
	}

	@Test
	fun `every built-in capability family has a plugin that installs its published bundle`() {
		val families = System.getProperty("anvil.capabilityFamilies").split(',').filter(String::isNotEmpty)
		assertTrue(families.isNotEmpty(), "The test task must pass the built-in capability families")
		val declared = declaredCapabilityPlugins()
		assertEquals((families + "default").sorted(), declared, "Capability plugins must match the built-in families")

		val expected = declared.map { plugin -> if (plugin == "default") plugin else "builtin-$plugin" }
		projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"capability-fixture\"")
		projectDirectory.resolve("build.gradle.kts").writeText(
			"plugins {\n" + declared.joinToString("") { "    id(\"me.whereareiam.anvil.capability.$it\")\n" } + "}\n" + """
			tasks.register("verify") {
				doLast {
					// The base plugin adds the Anvil API itself; the capability plugins add bundles only.
					val installed = configurations.getByName("anvilImplementation").allDependencies
						.filter { it.group == "me.whereareiam.anvil" && it.name != "api" }.map { it.name }.sorted()
					val expected = listOf(${expected.joinToString { "\"$it\"" }}).sorted()
					check(installed == expected) { "Installed ${'$'}installed instead of ${'$'}expected" }
				}
			}
		""".trimIndent())

		val result = GradleRunner.create()
			.withProjectDir(projectDirectory.toFile())
			.withPluginClasspath()
			.withArguments("verify")
			.build()
		assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
	}

	/**
	 * Names the capability plugins this module declares, read from the plugin descriptors on the test class path.
	 */
	private fun declaredCapabilityPlugins(): List<String> {
		val prefix = "me.whereareiam.anvil.capability."
		return javaClass.classLoader.getResources("META-INF/gradle-plugins").toList()
			.filter { it.protocol == "file" }
			.flatMap { Path.of(it.toURI()).listDirectoryEntries("$prefix*.properties") }
			.map { it.fileName.toString().removePrefix(prefix).removeSuffix(".properties") }
			.sorted()
	}
}
