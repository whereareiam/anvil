package me.whereareiam.anvil.buildlogic.adapter.linkage

import me.whereareiam.anvil.buildlogic.module.ClassFileVersion
import org.gradle.api.GradleException
import org.objectweb.asm.ClassReader

/**
 * Opens class files for the linkage tools, turning ASM's refusal of a class file newer than it supports into a
 * build failure that names the class and how to fix it.
 */
internal object ClassFiles {
	/**
	 * Reads [bytes] of the class file found at [origin].
	 *
	 * @throws GradleException when ASM cannot read the class file's version
	 */
	fun reader(bytes: ByteArray, origin: String): ClassReader = try {
		ClassReader(bytes)
	} catch (exception: IllegalArgumentException) {
		val major = ClassFileVersion.major(bytes) ?: throw GradleException("$origin is not a class file", exception)
		throw GradleException(
			"$origin has class file version $major (Java ${ClassFileVersion.java(major)}), which the ASM library of the "
				+ "build logic cannot read; update 'asm' in gradle/libs.versions.toml", exception
		)
	}
}
