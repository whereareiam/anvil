package me.whereareiam.anvil.buildlogic.data

import me.whereareiam.anvil.buildlogic.library.MinecraftVersion

/**
 * The block data pins of `block-data.toml`: the PrismarineJS minecraft-data release that block facts come from, and
 * for each group of Minecraft versions that share a protocol, the minecraft-data files its block data is made of.
 *
 * @property source the pinned minecraft-data release
 * @property releases the release rows, ordered by key
 */
data class BlockDataPins(val source: Source, val releases: List<Release>) {
	/**
	 * The pinned minecraft-data release.
	 *
	 * @property repository the GitHub repository, `owner/name`
	 * @property version the minecraft-data release tag
	 * @property commit the commit the tag points to, empty until `pinBlockData` resolved it
	 */
	data class Source(val repository: String, val version: String, val commit: String)

	/**
	 * Minecraft versions that speak one protocol, and so share one block registry, with their pinned files. Both
	 * files are absent when minecraft-data has no block data for any of the versions.
	 *
	 * @property minecraft the Minecraft versions of the row
	 * @property blocks the `blocks.json` of the versions
	 * @property collisionShapes the `blockCollisionShapes.json` of the versions
	 */
	data class Release(val minecraft: List<MinecraftVersion>, val blocks: PinnedFile?, val collisionShapes: PinnedFile?) {
		/**
		 * The highest Minecraft version of the row, which names its generated data.
		 */
		val key: MinecraftVersion
			get() = minecraft.max()

		/**
		 * Whether minecraft-data has the row's block data.
		 */
		val pinned: Boolean
			get() = blocks != null && collisionShapes != null
	}

	/**
	 * A file of the minecraft-data repository at the pinned commit.
	 *
	 * @property path the path below the repository root, such as `data/pc/1.16.2/blocks.json`
	 * @property sha256 the checksum of the file's bytes
	 */
	data class PinnedFile(val path: String, val sha256: String)
}
