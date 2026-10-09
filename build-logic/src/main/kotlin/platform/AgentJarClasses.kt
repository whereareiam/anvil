package me.whereareiam.anvil.buildlogic.platform

import me.whereareiam.anvil.buildlogic.jvm.ClassFileVersion
import java.io.File
import java.util.zip.ZipFile

/**
 * Finds the class files in an agent JAR that a given Java version would load but cannot run.
 *
 * A class file runs on the Java its version needs ([ClassFileVersion]) or newer. Multi-release entries under
 * `META-INF/versions/<n>/` load only on Java `n` or newer, so entries for a release above the checked Java are
 * skipped; `module-info` never loads from the class path and is skipped as well.
 */
object AgentJarClasses {
	private val releaseEntry = Regex("""META-INF/versions/(\d+)/.+""")

	/**
	 * Lists every class in [jar] that Java [java] would load but whose class-file version needs newer Java.
	 *
	 * @return one `<jar>!<entry> needs Java <n>` line per offending class, in entry order
	 */
	fun newerThan(jar: File, java: Int): List<String> = ZipFile(jar).use { zip ->
		zip.entries().asSequence()
			.filter { entry -> !entry.isDirectory && entry.name.endsWith(".class") && !entry.name.endsWith("module-info.class") }
			.filter { entry -> loadsOn(entry.name, java) }
			.mapNotNull { entry ->
				val location = "${jar.name}!${entry.name}"
				val required = zip.getInputStream(entry).use { ClassFileVersion.java(ClassFileVersion.major(it, location)) }
				if (required > java) "$location needs Java $required" else null
			}
			.toList()
	}

	private fun loadsOn(name: String, java: Int): Boolean {
		val release = releaseEntry.matchEntire(name) ?: return true
		return release.groupValues[1].toInt() <= java
	}
}
