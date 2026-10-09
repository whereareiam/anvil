package me.whereareiam.anvil.buildlogic.segment.linkage

import java.io.File
import java.net.URI
import java.nio.file.FileSystem
import java.nio.file.FileSystems
import java.nio.file.Files
import java.util.Optional

/**
 * Classes of one JDK installation, read from its module image. Linkage manifests leave them out, and
 * resolution reads them so members inherited from JDK types still resolve.
 *
 * Tasks open the JDK of the project's toolchain, which they declare as an input, so the manifest does not depend
 * on the JDK that runs Gradle. Close the instance after use.
 */
class JdkClasses private constructor(private val image: FileSystem, private val origin: String) : AutoCloseable {
	private val headers = mutableMapOf<String, Optional<ClassHeader>>()

	/**
	 * Whether the JDK provides the class with internal name [className].
	 */
	fun contains(className: String): Boolean = header(className) != null

	/**
	 * Header of a JDK class, or `null` when the JDK does not provide it.
	 */
	fun header(className: String): ClassHeader? = headers.getOrPut(className) { Optional.ofNullable(read(className)) }.orElse(null)

	override fun close() {
		image.close()
	}

	private fun read(className: String): ClassHeader? {
		if ('/' !in className) return null
		val packageLinks = image.getPath("/packages", className.substringBeforeLast('/').replace('/', '.'))
		if (!Files.isDirectory(packageLinks)) return null
		val classFile = Files.list(packageLinks).use { modules ->
			modules.map { image.getPath("/modules", it.fileName.toString(), "$className.class") }
				.filter(Files::isRegularFile)
				.findFirst()
				.orElse(null)
		} ?: return null
		return ClassHeader.read(ClassFiles.reader(Files.readAllBytes(classFile), "$origin class $className"))
	}

	companion object {
		/**
		 * Opens the module image of the JDK installed at [javaHome].
		 */
		fun open(javaHome: File): JdkClasses = JdkClasses(
			FileSystems.newFileSystem(URI.create("jrt:/"), mapOf("java.home" to javaHome.absolutePath)),
			"JDK ${javaHome.absolutePath}",
		)
	}
}
