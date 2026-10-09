package me.whereareiam.anvil.buildlogic.module

import org.gradle.api.GradleException
import java.io.DataInputStream
import java.io.InputStream

/**
 * Reads the version of a class file from its header. A class file of major version `m` needs Java `m - 44` or newer,
 * so Java 11 runs class files up to version 55. The class release checks, the platform agent check and the linkage
 * tools share this one reader.
 */
object ClassFileVersion {
	private const val magic = 0xCAFEBABE.toInt()
	private const val javaOffset = 44

	/**
	 * Reads the major version of the class file starting at [input].
	 *
	 * @param location where the class file was read, for the failure message
	 * @throws GradleException when [input] does not start with a class-file header
	 */
	fun major(input: InputStream, location: String): Int {
		val header = DataInputStream(input)
		if (header.readInt() != magic) throw GradleException("$location is not a class file")
		header.readUnsignedShort()
		return header.readUnsignedShort()
	}

	/**
	 * Reads the major version of the class file [bytes], or `null` when they do not start with a class-file header.
	 */
	fun major(bytes: ByteArray): Int? {
		if (bytes.size < 8) return null
		val header = (0 until 4).fold(0) { value, index -> (value shl 8) or (bytes[index].toInt() and 0xFF) }
		if (header != magic) return null
		return ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
	}

	/**
	 * The Java feature release that class files of [major] version need.
	 */
	fun java(major: Int): Int = major - javaOffset

	/**
	 * The newest class file major version that Java [release] runs.
	 */
	fun newestMajor(release: Int): Int = release + javaOffset
}
