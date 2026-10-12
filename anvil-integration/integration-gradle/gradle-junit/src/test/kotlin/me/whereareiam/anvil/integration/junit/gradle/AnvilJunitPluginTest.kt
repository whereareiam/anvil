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
				val foreground = tasks.names.contains("anvilRun")
				val dependencies = configurations.getByName("anvilImplementation").allDependencies.map { it.name }
				doLast {
					check(!foreground) { "The JUnit plugin must not install anvilRun" }
					check(anvilTest.get().group == "anvil") { "anvilTest must be in the anvil group" }
					check(tasks.named("anvil").get().group == "anvil") { "anvil must be in the anvil group" }
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
	fun `ordinary test never starts live tests`() {
		write("""
			anvil {
				suites {
					register("upgrade") { includeTags("upgrade") }
				}
			}
		""")

		val unit = runner("test", "--dry-run", "-Panvil.testMode=full").build()
		assertFalse(unit.output.contains(":anvil"), unit.output)
	}

	@Test
	fun `a suite runs its tags in an AnvilTest task wired like anvilTest, which leaves them out`() {
		write("""
			anvil {
				suites {
					register("upgrade") { includeTags("upgrade") }
				}
				artifact("late", files("late.jar"))
			}

			tasks.withType<AnvilTest>().configureEach {
				systemProperty("shared.setting", "on")
			}

			tasks.withType<Test>().configureEach {
				useJUnitPlatform()
			}

			tasks.register("verify") {
				val anvilTest = tasks.named<AnvilTest>("anvilTest")
				val upgrade = tasks.named<AnvilTest>("anvilUpgradeTest")
				val arguments = upgrade.map { task -> task.jvmArgumentProviders.flatMap { it.asArguments() } }
				val defaultArguments = anvilTest.map { task -> task.jvmArgumentProviders.flatMap { it.asArguments() } }
				val classes = upgrade.map { task -> task.testClassesDirs.files }
				val defaultClasses = anvilTest.map { task -> task.testClassesDirs.files }
				val sameClasspath = upgrade.get().classpath === sourceSets["anvil"].runtimeClasspath
				val included = upgrade.map { task -> (task.options as JUnitPlatformOptions).includeTags }
				val excluded = anvilTest.map { task -> (task.options as JUnitPlatformOptions).excludeTags }
				val shared = upgrade.map { task -> task.systemProperties["shared.setting"] }
				val upgradeReport = upgrade.get().reports.html.outputLocation.get().asFile.invariantSeparatorsPath
				val late = layout.projectDirectory.file("late.jar").asFile.absolutePath
				doLast {
					check(arguments.get() == defaultArguments.get()) { "Arguments differ: " + arguments.get() + " / " + defaultArguments.get() }
					check("-Danvil.artifact.late=" + late in arguments.get()) { "Missing late artifact: " + arguments.get() }
					check(classes.get() == defaultClasses.get()) { "Classes differ: " + classes.get() }
					check(sameClasspath) { "The suite must run on the anvil runtime classpath" }
					check(included.get() == setOf("upgrade")) { "Unexpected suite tags: " + included.get() }
					check(excluded.get() == setOf("upgrade")) { "anvilTest must leave out suite tags: " + excluded.get() }
					check(shared.get() == "on") { "Shared AnvilTest settings must reach suites" }
					check(upgradeReport.endsWith("reports/tests/anvilUpgradeTest")) { "Unexpected report: " + upgradeReport }
				}
			}
		""")
		projectDirectory.resolve("late.jar").writeText("")

		val result = runner("verify", "--configuration-cache").build()
		assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
	}

	@Test
	fun `anvil runs anvilTest and every suite`() {
		write("""
			anvil {
				suites {
					register("upgrade") { includeTags("upgrade") }
				}
			}
		""")

		val all = runner("anvil", "--dry-run").build()
		assertTrue(all.output.contains(":anvilTest SKIPPED"), all.output)
		assertTrue(all.output.contains(":anvilUpgradeTest SKIPPED"), all.output)
		assertTrue(all.output.contains(":anvil SKIPPED"), all.output)
	}

	@Test
	fun `a suite without tags is refused`() {
		write("""
			anvil {
				suites {
					register("upgrade")
				}
			}
		""")

		val result = runner("anvilUpgradeTest", "--dry-run").buildAndFail()
		assertTrue(result.output.contains("Anvil suite 'upgrade' must include at least one JUnit tag"), result.output)
	}

	@Test
	fun `a suite name that cannot name a task is refused`() {
		write("""
			anvil {
				suites {
					register("database-upgrade") { includeTags("upgrade") }
				}
			}
		""")

		val result = runner("tasks").buildAndFail()
		assertTrue(result.output.contains("Anvil suite name 'database-upgrade' must start with a letter"), result.output)
	}

	private fun write(verification: String) {
		projectDirectory.resolve("settings.gradle.kts").writeText("rootProject.name = \"junit-fixture\"")
		projectDirectory.resolve("plugin.jar").writeText("")
		projectDirectory.resolve("build.gradle.kts").writeText("""
			import me.whereareiam.anvil.integration.junit.gradle.AnvilTest
			import org.gradle.api.tasks.testing.junitplatform.JUnitPlatformOptions

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
