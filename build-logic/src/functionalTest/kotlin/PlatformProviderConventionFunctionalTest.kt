package me.whereareiam.anvil.buildlogic

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

/**
 * Builds a consumer project whose `provider` declares the agent it installs and the agent's minimum Java in its
 * versions data, and checks the agent's compiled classes against that declaration. The `build-platforms` settings
 * plugin refuses agent data in a project without the convention.
 */
class PlatformProviderConventionFunctionalTest {
	@TempDir
	lateinit var project: File

	@Test
	fun `checks the installed agent against the declared minimum Java`() {
		writeProject()

		val tooNew = runner(":provider:check").buildAndFail()
		assertTrue(tooNew.output.contains("The platform agent needs newer Java than demo-versions.toml declares "
			+ "([agent] minimumJava = 11)"), tooNew.output)
		assertTrue(tooNew.output.contains("agent-1.0.0.jar!demo/agent/Agent.class needs Java 21"), tooNew.output)

		write("agent/build.gradle.kts", """
			plugins {
				id("module-java")
			}

			javaRelease {
				main.set(11)
			}
		""")
		val compatible = runner(":provider:check").build()
		assertEquals(TaskOutcome.SUCCESS, compatible.task(":provider:checkAgentJava")?.outcome, compatible.output)
		assertEquals("agent minimum Java: 11\nagent release: 11\nchecked: agent-1.0.0.jar\n",
			file("provider/build/reports/platform/agent-java.txt").readText())

		write("provider/src/main/resources/demo/demo-versions.toml", versionData("[agent]\nminimumJava = 17\n"))
		val belowDeclaration = runner(":provider:checkAgentJava").buildAndFail()
		assertTrue(belowDeclaration.output.contains("demo-versions.toml declares [agent] minimumJava = 17, but the agent "
			+ "targets Java 11"), belowDeclaration.output)

		write("provider/src/main/resources/demo/demo-versions.toml", versionData(""))
		val undeclared = runner(":provider:checkAgentJava").buildAndFail()
		assertTrue(undeclared.output.contains("demo-versions.toml declares no [agent] minimumJava, but the provider "
			+ "installs agent-1.0.0.jar"), undeclared.output)

		write("provider/src/main/resources/demo/demo-versions.toml", versionData("[agent]\nminimumJava = 11\n"))
		write("provider/build.gradle.kts", "plugins {\n\tid(\"module-platform-provider\")\n}\n")
		val unchecked = runner(":provider:checkAgentJava").buildAndFail()
		assertTrue(unchecked.output.contains("demo-versions.toml declares [agent] minimumJava = 11, but the provider "
			+ "names no platformAgent dependency to check it against"), unchecked.output)

		write("provider/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n")
		val unconventional = runner("help").buildAndFail()
		assertTrue(unconventional.output.contains(":provider ships platform version data [demo/demo-versions.toml] but "
			+ "does not apply id(\"module-platform-provider\")"), unconventional.output)
	}

	private fun versionData(agent: String): String = agent + """

		[[java]]
		minimum = 11
		preferred = 11
	""".trimIndent()

	private fun writeProject() {
		File(System.getProperty("anvil.buildlogic.catalog")).copyTo(file("gradle/libs.versions.toml"))
		write("gradle.properties", "org.gradle.configuration-cache=true\n")
		write("settings.gradle.kts", """
			plugins {
				id("build-platforms")
			}

			dependencyResolutionManagement {
				repositories {
					mavenCentral()
				}
			}

			rootProject.name = "platform-consumer"
			include(":agent", ":provider")
		""")
		write("build.gradle.kts", """
			allprojects {
				group = "demo"
				version = "1.0.0"
			}
		""")
		write("agent/build.gradle.kts", "plugins {\n\tid(\"module-java\")\n}\n")
		write("agent/src/main/java/demo/agent/Agent.java", "package demo.agent;\n\npublic final class Agent {\n}\n")
		write("provider/build.gradle.kts", """
			plugins {
				id("module-platform-provider")
			}

			dependencies {
				platformAgent(project(":agent"))
			}
		""")
		write("provider/src/main/resources/demo/demo-versions.toml", """
			[agent]
			minimumJava = 11

			[[java]]
			minimum = 11
			preferred = 11
		""")
	}

	private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
		.withProjectDir(project)
		.withPluginClasspath()
		.withArguments(*arguments, "--stacktrace")
		.forwardOutput()

	private fun file(path: String): File = File(project, path)

	private fun write(path: String, text: String) {
		file(path).apply {
			parentFile.mkdirs()
			writeText(text.trimIndent() + "\n")
		}
	}
}
