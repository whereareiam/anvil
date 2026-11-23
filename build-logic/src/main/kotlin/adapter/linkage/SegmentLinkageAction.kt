package me.whereareiam.anvil.buildlogic.adapter.linkage

import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters
import java.io.PrintWriter
import java.io.StringWriter
import java.util.ServiceLoader
import java.util.spi.ToolProvider

/**
 * Runs one command of a library's segment linkage tool in a class loader of the library's own classes, and writes
 * what the tool prints to [Parameters.answer].
 */
abstract class SegmentLinkageAction : WorkAction<SegmentLinkageAction.Parameters> {
	/**
	 * The command to run and where its answer goes.
	 */
	interface Parameters : WorkParameters {
		/**
		 * The tool's arguments: a command and its options.
		 */
		val arguments: ListProperty<String>

		/**
		 * File receiving what the tool prints.
		 */
		val answer: RegularFileProperty
	}

	override fun execute() {
		val tool = ServiceLoader.load(ToolProvider::class.java, javaClass.classLoader).firstOrNull { it.name() == toolName }
			?: throw GradleException("The segment linkage tool '$toolName' is not on the library's class path")
		val errors = StringWriter()
		val arguments = parameters.arguments.get()
		val status = PrintWriter(parameters.answer.get().asFile.bufferedWriter()).use { out ->
			PrintWriter(errors).use { err -> tool.run(out, err, *arguments.toTypedArray()) }
		}
		if (status != 0) throw GradleException("Segment linkage '${arguments.first()}' failed: ${errors.toString().trim()}")
	}

	internal companion object {
		/**
		 * Name of the `java.util.spi.ToolProvider` that a library's common project provides for its segments.
		 */
		const val toolName = "anvil-segment-linkage"
	}
}
