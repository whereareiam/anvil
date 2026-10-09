package me.whereareiam.anvil.buildlogic.library.pin

import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.util.HexFormat

/**
 * SHA-256 checksums in the lower-case hex form that release data pins.
 */
internal object Checksums {
	/**
	 * Hashes the remaining bytes of [input] without closing it.
	 */
	fun sha256(input: InputStream): String {
		val digest = MessageDigest.getInstance("SHA-256")
		val buffer = ByteArray(64 * 1024)
		while (true) {
			val read = input.read(buffer)
			if (read < 0) break
			digest.update(buffer, 0, read)
		}
		return HexFormat.of().formatHex(digest.digest())
	}

	/**
	 * Hashes [file].
	 */
	fun sha256(file: File): String = file.inputStream().use(::sha256)
}
