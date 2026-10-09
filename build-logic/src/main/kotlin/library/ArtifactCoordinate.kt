package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.GradleException

/**
 * The Maven coordinate of one resolved JAR of a release closure, as release data records it and as a Maven
 * repository lays it out.
 *
 * @property group group id
 * @property name artifact id
 * @property version resolved version; a unique snapshot keeps its timestamp, such as `26.1-20260708.090514-22`
 * @property classifier classifier of a secondary JAR, such as `linux-x86_64`, or `null`
 */
data class ArtifactCoordinate(val group: String, val name: String, val version: String, val classifier: String?) {
	/**
	 * The `group:name:version[:classifier]` module of release data.
	 */
	val module: String
		get() = listOfNotNull(group, name, version, classifier).joinToString(":")

	/**
	 * Path of the JAR below a Maven repository's root. A unique snapshot lives in its `-SNAPSHOT` folder.
	 */
	val repositoryPath: String
		get() {
			val folder = uniqueSnapshot.matchEntire(version)?.let { it.groupValues[1] + "-SNAPSHOT" } ?: version
			return "${group.replace('.', '/')}/$name/$folder/$name-$version${classifier?.let { "-$it" }.orEmpty()}.jar"
		}

	companion object {
		private val uniqueSnapshot = Regex("""(.+)-\d{8}\.\d{6}-\d+""")

		/**
		 * Describes the JAR [fileName] that Gradle resolved for the module `group:name:version`. Gradle may cache a
		 * unique snapshot under its `-SNAPSHOT` file name; the classifier is what follows the version.
		 *
		 * @throws GradleException for a changing `-SNAPSHOT` version, which cannot be pinned, and for a file that is
		 * not a JAR of the module
		 */
		fun of(group: String, name: String, version: String, fileName: String): ArtifactCoordinate {
			if (version.endsWith("-SNAPSHOT"))
				throw GradleException("$group:$name:$version is a changing snapshot; release data must name a unique snapshot version")
			if (!fileName.endsWith(".jar") || !fileName.startsWith("$name-"))
				throw GradleException("$fileName is not a JAR of $group:$name:$version")
			val stem = fileName.removePrefix("$name-").removeSuffix(".jar")
			val snapshotFolder = uniqueSnapshot.matchEntire(version)?.let { it.groupValues[1] + "-SNAPSHOT" }
			val remainder = listOfNotNull(version, snapshotFolder)
				.firstOrNull { stem == it || stem.startsWith("$it-") }
				?.let { stem.removePrefix(it) }
				?: throw GradleException("$fileName is not a JAR of $group:$name:$version")
			return ArtifactCoordinate(group, name, version, remainder.removePrefix("-").ifEmpty { null })
		}
	}
}
