package me.whereareiam.anvil.buildlogic.library

import java.io.Serializable

/**
 * One row of a protocol library's releases file.
 *
 * @property version the library's own release identifier, such as `1.16.5-2`
 * @property module Maven coordinate that segments for this release compile against
 * @property protocol Minecraft protocol number the release speaks
 * @property minecraft every Minecraft version that speaks this protocol
 * @property verified versions covered by Anvil's live tests
 * @property java minimum Java feature release of a worker that loads this release
 * @property features optional library features the release supports
 * @property artifacts the worker's runtime closure, in classpath order
 */
data class LibraryRelease(
	val version: String,
	val module: String,
	val protocol: Int,
	val minecraft: List<MinecraftVersion>,
	val verified: List<MinecraftVersion>,
	val java: Int,
	val features: List<String>,
	val artifacts: List<ReleaseArtifact>,
) : Serializable {
	/**
	 * The release key: the highest Minecraft version that speaks this release's protocol. Segment folders
	 * and runtime segment selection name releases by this version.
	 */
	val key: MinecraftVersion
		get() = minecraft.max()

	private companion object {
		private const val serialVersionUID = 1L
	}
}
