package me.whereareiam.anvil.buildlogic.download

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Checksums in the lower-case hex form that pins name: SHA-256 for release data and minecraft-data, SHA-1 for the
 * files Mojang's version metadata names.
 */
internal object Checksums {
	/**
	 * Hashes the remaining bytes of [input] with SHA-256 without closing it.
	 */
	fun sha256(input: InputStream): String = digest("SHA-256", input)

	/**
	 * Hashes [file] with SHA-256.
	 */
	fun sha256(file: File): String = file.inputStream().use(::sha256)

	/**
	 * Hashes the remaining bytes of [input] with SHA-1 without closing it.
	 */
	fun sha1(input: InputStream): String = digest("SHA-1", input)

	/**
	 * Hashes [file] with SHA-1.
	 */
	fun sha1(file: File): String = file.inputStream().use(::sha1)

	private fun digest(algorithm: String, input: InputStream): String {
		val digest = MessageDigest.getInstance(algorithm)
		val buffer = ByteArray(64 * 1024)
		while (true) {
			val read = input.read(buffer)
			if (read < 0) break
			digest.update(buffer, 0, read)
		}
		return HexFormat.of().formatHex(digest.digest())
	}
}
