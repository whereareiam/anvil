package me.whereareiam.anvil.buildlogic.module

import org.gradle.api.GradleException
import java.io.DataInputStream
import java.io.InputStream

/**
 * Reads the version of a class file from its header. A class file of major version `m` needs Java `m - 44` or newer,
 * so Java 11 runs class files up to version 55. The class release checks and the platform agent check share this
 * one reader.
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
	 * The Java feature release that class files of [major] version need.
	 */
	fun java(major: Int): Int = major - javaOffset

	/**
	 * The newest class file major version that Java [release] runs.
	 */
	fun newestMajor(release: Int): Int = release + javaOffset
}
