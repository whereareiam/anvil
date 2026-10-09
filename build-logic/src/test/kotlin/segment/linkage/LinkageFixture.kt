package me.whereareiam.anvil.buildlogic.segment.linkage

import org.junit.jupiter.api.Assertions.assertEquals
import java.io.File
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.tools.ToolProvider

/**
 * A tiny library compiled in two releases, a project-dependency port, and a segment compiled against the
 * first release that implements the port. The later release drops `Api.removed()`, makes `Api.count()` an
 * instance method, narrows `Api.narrowed()` to protected and turns the `Handler` interface into a class.
 *
 * The fixture opens the JDK that runs the tests; close it after use.
 */
class LinkageFixture(private val directory: File) : AutoCloseable {
	val jdk: JdkClasses = JdkClasses.open(File(System.getProperty("java.home")))
	val firstRelease: File
	val laterRelease: File
	val port: File
	val segment: File

	init {
		val base = "package lib; public class Base { public void inherited() {} protected void helper() {} }"
		val listener = "package lib; public interface Listener { default void onEvent() {} }"
		firstRelease = compile(
			"first",
			emptyList(),
			base,
			listener,
			"package lib; public interface Handler { void handle(); }",
			"""
			package lib;
			public class Api extends Base {
				public static final String NAME = "api";
				public static String version = "1";
				public void kept() {}
				public void removed() {}
				public static int count() { return 0; }
				public void narrowed() {}
			}
			""",
		)
		laterRelease = jar(compile(
			"later",
			emptyList(),
			base,
			listener,
			"package lib; public abstract class Handler { public abstract void handle(); }",
			"""
			package lib;
			public class Api extends Base {
				public static String version = "2";
				public void kept() {}
				public int count() { return 0; }
				protected void narrowed() {}
			}
			""",
		))
		port = compile("port", listOf(firstRelease), "package demo.port; public interface Port { String describe(lib.Handler handler); }")
		segment = compile(
			"segment",
			listOf(firstRelease, port),
			"""
			package demo.lib.v1_0;
			import java.util.List;
			public final class Adapter extends lib.Base implements demo.port.Port, lib.Listener {
				@Override
				public String describe(lib.Handler handler) {
					lib.Api api = new lib.Api();
					api.kept();
					api.removed();
					api.inherited();
					api.narrowed();
					helper();
					onEvent();
					handler.handle();
					Runnable task = api::kept;
					task.run();
					return List.of(lib.Api.NAME, lib.Api.version, String.valueOf(lib.Api.count() + api.hashCode())).toString();
				}
			}
			""",
		)
	}

	/**
	 * What the segment links against outside the JDK, its own package and the port's package, as the segment
	 * manifest records it.
	 */
	fun externalReferences(): Set<LinkageReference> {
		val collector = LinkageCollector()
		segment.walkTopDown().filter { it.name.endsWith(".class") }.forEach { collector.add(it.readBytes(), it.path) }
		val buildPackages = (collector.classNames + ClassIndex.of(listOf(port), jdk).classNames).map { it.substringBeforeLast('/') }.toSet()
		return collector.externalReferences(ClassIndex.of(listOf(firstRelease, port), jdk))
			.filterNot { it.owner.substringBeforeLast('/') in buildPackages || jdk.contains(it.owner) }
			.toSet()
	}

	override fun close() {
		jdk.close()
	}

	/**
	 * Compiles [sources] into a class directory named [name] against [classpath].
	 */
	fun compile(name: String, classpath: List<File>, vararg sources: String): File {
		val sourceRoot = File(directory, "$name-sources")
		val output = File(directory, name)
		output.mkdirs()
		val files = sources.map { source ->
			val text = source.trimIndent()
			val packageName = Regex("""package ([\w.]+);""").find(text)!!.groupValues[1]
			val typeName = Regex("""(?:class|interface) (\w+)""").find(text)!!.groupValues[1]
			File(sourceRoot, packageName.replace('.', '/') + "/$typeName.java").apply {
				parentFile.mkdirs()
				writeText(text)
			}
		}
		val arguments = listOf("-d", output.path, "--release", "21") +
			(if (classpath.isEmpty()) emptyList() else listOf("-classpath", classpath.joinToString(File.pathSeparator))) +
			files.map(File::getPath)
		assertEquals(0, ToolProvider.getSystemJavaCompiler().run(null, null, null, *arguments.toTypedArray()), "javac failed for $name")
		return output
	}

	private fun jar(classes: File): File {
		val jar = File(directory, "${classes.name}.jar")
		JarOutputStream(jar.outputStream()).use { output ->
			classes.walkTopDown().filter(File::isFile).forEach { file ->
				output.putNextEntry(JarEntry(file.relativeTo(classes).invariantSeparatorsPath))
				file.inputStream().use { it.copyTo(output) }
				output.closeEntry()
			}
		}
		return jar
	}
}
