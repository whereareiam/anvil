package me.whereareiam.anvil.buildlogic.adapter

import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import javax.inject.Inject

/**
 * The `segment { }` block of a segment's build file.
 *
 * A release usually changes only a few of the classes a segment uses, so a segment may extend an earlier segment of
 * its side folder and override only what changed:
 *
 * ```kotlin
 * segment {
 *     extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v12111)
 * }
 * ```
 *
 * The segment then compiles against the classes of its base, and of the base's own base, embeds them into its JAR
 * and lists their references in its linkage manifest, so the worker still loads one segment per side and the
 * linkage check covers every class the segment brings.
 */
abstract class SegmentExtension @Inject constructor(
	private val dependencies: DependencyHandler,
	private val segmentPath: String,
	private val segment: SegmentName,
) {
	private var base: String? = null

	/**
	 * Makes [base], an earlier segment of the same side folder, this segment's base.
	 *
	 * @throws GradleException when the segment already has a base, or [base] is not an earlier sibling segment
	 */
	fun extends(base: ProjectDependency) {
		extends(base.path, base)
	}

	/**
	 * Makes the segment at [path], an earlier segment of the same side folder, this segment's base, for builds
	 * without type-safe project accessors.
	 *
	 * @throws GradleException when the segment already has a base, or [path] is not an earlier sibling segment
	 */
	fun extends(path: String) {
		extends(path, dependencies.project(mapOf("path" to path)))
	}

	private fun extends(basePath: String, base: Any) {
		this.base?.let { throw GradleException("Segment $segmentPath already extends $it and cannot also extend $basePath") }
		if (basePath.substringBeforeLast(':') != segmentPath.substringBeforeLast(':'))
			throw GradleException("Segment $segmentPath can extend only a segment of its own side folder, not $basePath")
		if (SegmentName.parse(basePath.substringAfterLast(':')).since >= segment.since)
			throw GradleException("Segment $segmentPath can extend only an earlier segment, not $basePath")

		this.base = basePath
		dependencies.add(BASE_CONFIGURATION, base)
	}

	companion object {
		/**
		 * Dependency scope holding a segment's base.
		 */
		const val BASE_CONFIGURATION = "segmentBase"

		/**
		 * Usage of the variant through which a segment offers its classes, and those of its own base, to the segment
		 * that extends it.
		 */
		const val CLASSES_USAGE = "anvil-segment-classes"
	}
}
