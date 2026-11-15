package me.whereareiam.anvil.buildlogic.block

/**
 * The block data pins of `block-data.toml`: the PrismarineJS minecraft-data release that block data comes from, and
 * for each protocol, the minecraft-data files its block data is made of.
 *
 * @property source the pinned minecraft-data release
 * @property releases the release rows, ordered by protocol
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
	 * One protocol, whose Minecraft versions share one block registry, with its pinned files. Both files are absent
	 * when minecraft-data has no block data for any version of the protocol.
	 *
	 * @property protocol the Minecraft protocol number, such as `767`, which names the generated data
	 * @property blocks the protocol's `blocks.json`
	 * @property collisionShapes the protocol's `blockCollisionShapes.json`
	 */
	data class Release(val protocol: Int, val blocks: PinnedFile?, val collisionShapes: PinnedFile?) {
		/**
		 * Whether minecraft-data has the row's block data.
		 */
		val pinned: Boolean
			get() = blocks != null && collisionShapes != null
	}

	/**
	 * A file of the minecraft-data repository at the pinned commit, below `data/pc`, which the settings' `minecraft-data`
	 * repository resolves as the artifact [name] with the [directory] as classifier.
	 *
	 * @property path the path below the repository root, such as `data/pc/1.16.2/blocks.json`
	 * @property sha256 the checksum of the file's bytes
	 */
	data class PinnedFile(val path: String, val sha256: String) {
		private val parts = requireNotNull(PATH.matchEntire(path)) { "$path is no data/pc/<directory>/<name>.json file" }

		/**
		 * The directory below `data/pc`, such as `1.16.2`.
		 */
		val directory: String
			get() = parts.groupValues[1]

		/**
		 * The file's name without its extension, such as `blocks`.
		 */
		val name: String
			get() = parts.groupValues[2]

		companion object {
			/**
			 * The extension of every pinned file.
			 */
			const val EXTENSION = "json"

			/**
			 * The paths a pinned file may have.
			 */
			val PATH = Regex("""data/pc/([\w.-]+)/(\w+)\.json""")
		}
	}

	/**
	 * The files of the pinned releases, each once, which Gradle resolves as artifacts of [module].
	 */
	val files: List<PinnedFile>
		get() = releases.flatMap { listOfNotNull(it.blocks, it.collisionShapes) }.distinctBy { it.path }

	/**
	 * The Gradle module of the pinned commit, such as `PrismarineJS:minecraft-data:15411f05...`.
	 */
	val module: String
		get() = "${source.repository.replace('/', ':')}:${source.commit}"
}
