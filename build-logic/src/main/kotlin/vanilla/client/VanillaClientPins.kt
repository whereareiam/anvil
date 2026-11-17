package me.whereareiam.anvil.buildlogic.vanilla.client

import java.io.Serializable

/**
 * The pins of `vanilla-clients.toml`: for each Minecraft release the harness runs, the family harness that runs it
 * and Mojang's files of its vanilla client, each by URL and SHA-1.
 *
 * @property manifest the URL of Mojang's version manifest, which `pinVanillaClients` finds each release in
 * @property releases the release rows, in the file's order
 */
data class VanillaClientPins(val manifest: String, val releases: List<Release>) {
	/**
	 * One Minecraft release. A row pins its version metadata and client JAR together, or neither until the pin task
	 * ran; it pins the client's mappings exactly when Mojang ships the client obfuscated.
	 *
	 * @property minecraft the Minecraft version, such as `1.21.11`
	 * @property harness the family harness that runs it, the harness's source set, such as `v1_21_11`
	 * @property metadata Mojang's version metadata, which names the client's libraries by SHA-1
	 * @property client the client JAR
	 * @property mappings the client's mappings, `null` for a client that ships unobfuscated
	 */
	data class Release(
		val minecraft: String,
		val harness: String,
		val metadata: PinnedFile?,
		val client: PinnedFile?,
		val mappings: PinnedFile?,
	) : Serializable {
		/**
		 * Whether the pin task resolved the release.
		 */
		val pinned: Boolean
			get() = metadata != null && client != null
	}

	/**
	 * A file Mojang serves, by its URL and the SHA-1 of its bytes.
	 *
	 * @property url where Mojang serves it
	 * @property sha1 the SHA-1 of its bytes in lower-case hex
	 */
	data class PinnedFile(val url: String, val sha1: String) : Serializable

	/**
	 * Returns the row of [minecraft], or `null` when the file lists none.
	 */
	fun release(minecraft: String): Release? = releases.firstOrNull { it.minecraft == minecraft }
}
