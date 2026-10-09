package me.whereareiam.anvil.buildlogic

import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.util.jar.JarFile

/**
 * A temporary consumer build that applies the conventions under test from the plugin classpath and uses the
 * build's own version catalog, so the conventions see the same `libs` as the Anvil build.
 */
class TestProject(val directory: File) {
	init {
		File(System.getProperty("anvil.buildlogic.catalog")).copyTo(file("gradle/libs.versions.toml"), overwrite = true)
		write("gradle.properties", "org.gradle.configuration-cache=true\n")
	}

	/**
	 * Resolves [path] in the project.
	 */
	fun file(path: String): File = File(directory, path)

	/**
	 * Writes [text] to [path], trimming the indentation of a raw string.
	 */
	fun write(path: String, text: String) {
		file(path).apply {
			parentFile.mkdirs()
			writeText(text.trimIndent() + "\n")
		}
	}

	/**
	 * Runs a build that must succeed.
	 */
	fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

	/**
	 * Runs a build that must fail.
	 */
	fun fail(vararg arguments: String): BuildResult = runner(*arguments).buildAndFail()

	/**
	 * Prints the resolved dependencies of a configuration.
	 */
	fun dependencies(projectPath: String, configuration: String): String =
		run("$projectPath:dependencies", "--configuration", configuration).output

	/**
	 * Reads a text entry of a JAR.
	 */
	fun jarText(jar: String, entry: String): String = JarFile(file(jar)).use { archive ->
		archive.getInputStream(archive.getJarEntry(entry) ?: error("$entry is missing from $jar")).reader().readText()
	}

	/**
	 * Lists the entry names of a JAR.
	 */
	fun jarEntries(jar: String): List<String> = JarFile(file(jar)).use { archive -> archive.entries().toList().map { it.name } }

	/**
	 * Reads the main attributes of a JAR's manifest.
	 */
	fun manifest(jar: String): Map<String, String> = JarFile(file(jar)).use { archive ->
		archive.manifest.mainAttributes.entries.associate { (name, value) -> name.toString() to value.toString() }
	}

	private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
		.withProjectDir(directory)
		.withPluginClasspath()
		.withArguments(*arguments, "--stacktrace")
		.forwardOutput()
}
