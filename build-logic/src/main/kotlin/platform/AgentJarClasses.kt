package me.whereareiam.anvil.buildlogic.platform

import me.whereareiam.anvil.buildlogic.module.ClassFileVersion
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream

/**
 * Finds the class files in an agent JAR that a given Java version would load but cannot run.
 *
 * A class file runs on the Java its version needs ([ClassFileVersion]) or newer. Multi-release entries under
 * `META-INF/versions/<n>/` load only on Java `n` or newer, so entries for a release above the checked Java are
 * skipped; `module-info` never loads from the class path and is skipped as well. A JAR nested in the agent JAR, such
 * as the one the Lumi agent loads in a class loader of its own, is checked the same way, entry by entry.
 */
object AgentJarClasses {
	private val releaseEntry = Regex("""META-INF/versions/(\d+)/.+""")

	/**
	 * Lists every class in [jar], or in a JAR nested in it, that Java [java] would load but whose class-file version
	 * needs newer Java.
	 *
	 * @return one `<jar>!<entry> needs Java <n>` line per offending class, in entry order; a nested class reads
	 * `<jar>!<nested jar>!<entry>`
	 */
	fun newerThan(jar: File, java: Int): List<String> = jar.inputStream().use { newerThan(it, jar.name, java) }

	private fun newerThan(input: InputStream, location: String, java: Int): List<String> {
		val failures = mutableListOf<String>()
		val zip = ZipInputStream(input)
		generateSequence { zip.nextEntry }.filter { !it.isDirectory }.forEach { entry ->
			val entryLocation = "$location!${entry.name}"
			if (entry.name.endsWith(".jar")) {
				failures += newerThan(zip.readBytes().inputStream(), entryLocation, java)
				return@forEach
			}
			if (!entry.name.endsWith(".class") || entry.name.endsWith("module-info.class") || !loadsOn(entry.name, java))
				return@forEach

			val required = ClassFileVersion.java(ClassFileVersion.major(zip, entryLocation))
			if (required > java) failures += "$entryLocation needs Java $required"
		}
		return failures
	}

	private fun loadsOn(name: String, java: Int): Boolean {
		val release = releaseEntry.matchEntire(name) ?: return true
		return release.groupValues[1].toInt() <= java
	}
}
