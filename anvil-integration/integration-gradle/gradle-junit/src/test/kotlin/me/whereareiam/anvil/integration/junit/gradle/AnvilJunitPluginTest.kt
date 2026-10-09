package me.whereareiam.anvil.integration.junit.gradle

import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.io.path.writeText

class AnvilJunitPluginTest {
	@TempDir
	lateinit var projectDirectory: Path

	@Test
	fun `registers anvilTest on the anvil source set without foreground tooling`() {
		write("""
			tasks.register("verify") {
				val anvilTest = tasks.named<Test>("anvilTest")
				val arguments = anvilTest.map { task -> task.jvmArgumentProviders.flatMap { it.asArguments() } }
				val classes = anvilTest.map { task -> task.testClassesDirs.files.map { it.invariantSeparatorsPath } }
				val foreground = tasks.names.contains("anvilScenario")
				val dependencies = configurations.getByName("anvilImplementation").allDependencies.map { it.name }
				doLast {
					check(!foreground) { "The JUnit plugin must not install anvilScenario" }
					check("junit" in dependencies) { "Missing JUnit integration: " + dependencies }
					check(classes.get().all { it.contains("/classes/java/anvil") }) { "Unexpected classes: " + classes.get() }
					val plugin = layout.projectDirectory.file("plugin.jar").asFile.absolutePath
					check("-Danvil.artifact.plugin=" + plugin in arguments.get()) { "Missing artifact: " + arguments.get() }
					check("-Danvil.eula.accepted=true" in arguments.get()) { "Missing EULA: " + arguments.get() }
				}
			}
		""")

		val result = runner("verify").build()
		assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
	}

	@Test
	fun `ordinary test includes anvilTest only in full test mode`() {
		write("")

		val unit = runner("test", "--dry-run").build()
		assertFalse(unit.output.contains(":anvilTest"), unit.output)

		val full = runner("test", "--dry-run", "-Panvil.testMode=full").build()
		assertTrue(full.output.contains(":anvilTest SKIPPED"), full.output)
	}

	private fun write(verification: String) {
		projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"junit-fixture\"")
		projectDirectory.resolve("plugin.jar").writeText("")
		projectDirectory.resolve("build.gradle.kts").writeText("""
			plugins {
				id("me.whereareiam.anvil.junit")
			}

			anvil {
				acceptEula()
				artifact("plugin", files("plugin.jar"))
			}
		""".trimIndent() + "\n" + verification.trimIndent())
	}

	private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
		.withProjectDir(projectDirectory.toFile())
		.withPluginClasspath()
		.withArguments(*arguments, "-PanvilVersion=0.0.1-test", "--stacktrace")
}
