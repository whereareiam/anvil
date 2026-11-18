package me.whereareiam.anvil.buildlogic.edition

import org.gradle.api.GradleException
import java.io.File

/**
 * The editions of Minecraft that simulated players speak: each `edition-<edition>` folder below `anvil-edition`, such
 * as `edition-java`. An edition is one API family whose members are grouped by what each part is, such as
 * `java-protocol/protocol-api` or `java-data/data-block`, and a built-in capability family adapts itself to an edition
 * with its `<feature>-<edition>` side.
 */
object Editions {
	private const val DIRECTORY = "anvil-edition"
	private const val PREFIX = "edition-"

	/**
	 * Names the editions of the build at [rootDirectory].
	 *
	 * @param rootDirectory root project directory
	 * @return edition names, ordered by name
	 */
	fun discover(rootDirectory: File): List<String> = File(rootDirectory, DIRECTORY)
		.listFiles { file -> file.isDirectory && file.name.startsWith(PREFIX) }.orEmpty()
		.map { directory -> directory.name.removePrefix(PREFIX) }
		.sorted()

	/**
	 * Names the project of an edition's protocol API, such as
	 * `:anvil-edition:edition-java:java-protocol:protocol-api` for `java`.
	 *
	 * @param edition edition name
	 * @return the API project's path
	 */
	fun apiPath(edition: String): String = ":$DIRECTORY:$PREFIX$edition:$edition-protocol:protocol-api"

	/**
	 * Tells whether the project at [projectPath] lies below `anvil-edition` and so belongs to an edition.
	 *
	 * @param projectPath Gradle project path
	 */
	fun contains(projectPath: String): Boolean = projectPath.startsWith(":$DIRECTORY:")

	/**
	 * Names the API family of the edition member at [projectPath], the edition's folder, such as
	 * `:anvil-edition:edition-java` for `:anvil-edition:edition-java:java-data:data-block`.
	 *
	 * @param projectPath Gradle project path of an edition member
	 * @return the family's project path
	 * @throws GradleException when the project is not an edition member
	 */
	fun family(projectPath: String): String = member(projectPath).let { (edition, _) -> ":$DIRECTORY:$edition" }

	/**
	 * Derives the published artifact ID of the edition member at [projectPath] from its folders: the edition's folder
	 * and the member's, such as `edition-java-data-block` for `:anvil-edition:edition-java:java-data:data-block`.
	 *
	 * @param projectPath Gradle project path of an edition member
	 * @return the artifact ID
	 * @throws GradleException when the project is not an edition member
	 */
	fun artifactId(projectPath: String): String = member(projectPath).let { (edition, member) -> "$edition-$member" }

	private fun member(projectPath: String): Pair<String, String> {
		val segments = projectPath.removePrefix(":").split(':')
		if (segments.size != 4 || segments[0] != DIRECTORY || !isMember(segments[1], segments[2], segments[3]))
			throw GradleException("Edition member '$projectPath' must be :$DIRECTORY:$PREFIX<edition>:<edition>-<part>:<part>-<member>")

		return segments[1] to segments[3]
	}

	private fun isMember(edition: String, part: String, member: String): Boolean =
		edition.startsWith(PREFIX) && part.startsWith(edition.removePrefix(PREFIX) + "-")
			&& member.startsWith(part.substringAfter('-') + "-")
}
