package me.whereareiam.anvil.buildlogic.capability

import java.io.File

/**
 * The protocol editions simulated players speak: each `protocol-<edition>` folder below `anvil-player/player-protocol`,
 * such as `java`. A built-in capability family adapts itself to an edition with its `<feature>-<edition>` side.
 */
object ProtocolEditions {
	private const val DIRECTORY = "anvil-player/player-protocol"
	private const val PREFIX = "protocol-"

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
	 * `:anvil-player:player-protocol:protocol-java:protocol-java-api` for `java`.
	 *
	 * @param edition edition name
	 * @return the API project's path
	 */
	fun apiPath(edition: String): String =
		":" + DIRECTORY.replace('/', ':') + ":$PREFIX$edition:$PREFIX$edition-api"
}
