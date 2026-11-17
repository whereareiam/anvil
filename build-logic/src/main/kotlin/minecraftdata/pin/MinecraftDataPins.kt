package me.whereareiam.anvil.buildlogic.minecraftdata.pin

/**
 * The pins of one kind's pins file, such as `block-data.toml`: the PrismarineJS minecraft-data release the data comes
 * from, and for each protocol, the minecraft-data files its data is made of.
 *
 * @property source the pinned minecraft-data release
 * @property releases the release rows, ordered by protocol
 */
data class MinecraftDataPins(val source: Source, val releases: List<Release>) {
	/**
	 * The pinned minecraft-data release.
	 *
	 * @property repository the GitHub repository, `owner/name`
	 * @property version the minecraft-data release tag
	 * @property commit the commit the tag points to, empty until the kind's pin task resolved it
	 */
	data class Source(val repository: String, val version: String, val commit: String)

	/**
	 * One protocol, whose Minecraft versions share one set of data, with its pinned files by the field that names
	 * each in the row. A row pins every file of its kind or none: none when minecraft-data has the kind's data for no
	 * version of the protocol.
	 *
	 * @property protocol the Minecraft protocol number, such as `767`, which names the generated data
	 * @property files the pinned files by field, in the kind's order, empty when the row pins none
	 */
	data class Release(val protocol: Int, val files: Map<String, PinnedFile>) {
		/**
		 * Whether minecraft-data has the row's data.
		 */
		val pinned: Boolean
			get() = files.isNotEmpty()

		/**
		 * Returns the pinned file of [field].
		 *
		 * @throws IllegalStateException when the row pins no files
		 */
		fun file(field: String): PinnedFile = checkNotNull(files[field]) { "Protocol $protocol pins no $field" }
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
		get() = releases.flatMap { it.files.values }.distinctBy { it.path }

	/**
	 * The Gradle module of the pinned commit, such as `PrismarineJS:minecraft-data:15411f05...`.
	 */
	val module: String
		get() = "${source.repository.replace('/', ':')}:${source.commit}"
}
