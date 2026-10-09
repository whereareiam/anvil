package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.GradleException
import java.io.File

/**
 * The protocol libraries registered in a build. Library `X` is registered by owning
 * `anvil-protocol/protocol-X/X-releases.toml`; no other list of libraries exists.
 */
object LibraryRegistry {
	private const val familyDirectory = "anvil-protocol"
	private const val libraryDirectoryPrefix = "protocol-"
	private val identifier = Regex("[a-z][a-z0-9]*")

	/**
	 * Finds every registered library below [rootDirectory].
	 *
	 * @return releases file of each library, keyed and ordered by library id
	 */
	fun discover(rootDirectory: File): Map<String, File> = File(rootDirectory, familyDirectory)
		.listFiles { file -> file.isDirectory && file.name.startsWith(libraryDirectoryPrefix) }.orEmpty()
		.mapNotNull { directory ->
			val library = directory.name.removePrefix(libraryDirectoryPrefix)
			val releases = File(directory, "$library-releases.toml")
			if (!releases.isFile) return@mapNotNull null
			if (!identifier.matches(library))
				throw GradleException("Library id '$library' of $releases must be one lower-case word")
			library to releases
		}
		.toMap()
		.toSortedMap()

	/**
	 * Names the library a library side folder belongs to: the one registered id among its hyphen-separated
	 * words, as in `messages-mcprotocol` or `mcprotocol-client`.
	 *
	 * @throws GradleException unless exactly one registered library id is a word of [folderName]
	 */
	fun libraryOf(folderName: String, libraries: Collection<String>): String {
		val words = folderName.split('-')
		val matches = libraries.filter(words::contains)
		return matches.singleOrNull() ?: throw GradleException(
			"Library side folder '$folderName' must name exactly one registered library as a hyphen-separated word; "
				+ "registered: ${libraries.sorted()}, named: $matches"
		)
	}
}
