package me.whereareiam.anvil.buildlogic.library

import java.io.Serializable

/**
 * One row of a protocol library's releases file.
 *
 * @property version the library's own release identifier, such as `1.16.5-2`
 * @property edition opaque identifier of the Minecraft edition the release implements
 * @property module Maven coordinate that segments for this release compile against
 * @property protocols the native protocol number of each listed Minecraft version
 * @property minecraft every Minecraft version of the edition the release speaks
 * @property verified versions covered by Anvil's live tests
 * @property java minimum Java feature release of a worker that loads this release
 * @property features optional library features the release supports
 * @property artifacts the worker's runtime closure, in classpath order
 */
data class LibraryRelease(
	val version: String,
	val edition: String,
	val module: String,
	val protocols: Map<MinecraftVersion, Int>,
	val minecraft: List<MinecraftVersion>,
	val verified: List<MinecraftVersion>,
	val java: Int,
	val features: List<String>,
	val artifacts: List<ReleaseArtifact>,
) : Serializable {
	/**
	 * The release key: the highest Minecraft version the release speaks. Segment folders
	 * and runtime segment selection name releases by this version.
	 */
	val key: MinecraftVersion
		get() = minecraft.max()

	private companion object {
		private const val serialVersionUID = 2L
	}
}
