package me.whereareiam.anvil.buildlogic.adapter

import org.gradle.api.GradleException
import org.gradle.api.artifacts.ProjectDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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
 *
 * A base class that no longer links against the segment's release is replaced by a class of the segment's own, which
 * the segment names explicitly; the replaced class then leaves the segment's JAR and its linkage manifest:
 *
 * ```kotlin
 * segment {
 *     extends(projects.anvilPlayer.playerLibrary.libraryMcprotocol.mcprotocolJava.v12111)
 *     replaces("me.whereareiam.anvil.protocol.mcprotocol.client.v1_21_11.InteractionPackets")
 * }
 * ```
 */
abstract class SegmentExtension @Inject constructor(
	private val dependencies: DependencyHandler,
	private val segmentPath: String,
	private val segment: SegmentName,
	objects: ObjectFactory,
) {
	/**
	 * Path of the segment this one extends, absent until [extends] names it.
	 */
	val base: Property<String> = objects.property(String::class.java)

	/**
	 * Binary names of the base classes this segment replaces, which the convention leaves out of the classes it takes
	 * from the base chain.
	 */
	val replaced: SetProperty<String> = objects.setProperty(String::class.java).empty()

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

	/**
	 * Replaces base classes with classes of this segment's own: the segment no longer carries [classes] nor their
	 * nested classes, and the build fails when one of them is not a class of the base chain or a class the segment
	 * still takes from its base uses it. The segment's adapter then names its own class instead.
	 *
	 * @param classes binary names of top-level base classes, such as `a.b.v1_21_11.InteractionPackets`
	 * @throws GradleException when the segment extends no base or a name is not a binary class name
	 */
	fun replaces(vararg classes: String) {
		if (!base.isPresent) throw GradleException("Segment $segmentPath replaces base classes but extends no segment")
		classes.firstOrNull { !BINARY_NAME.matches(it) }?.let {
			throw GradleException("Segment $segmentPath can replace only a top-level class by its binary name, not $it")
		}

		replaced.addAll(*classes)
	}

	private fun extends(basePath: String, base: Any) {
		this.base.orNull?.let {
			throw GradleException("Segment $segmentPath already extends $it and cannot also extend $basePath")
		}
		if (basePath.substringBeforeLast(':') != segmentPath.substringBeforeLast(':'))
			throw GradleException("Segment $segmentPath can extend only a segment of its own side folder, not $basePath")
		if (SegmentName.parse(basePath.substringAfterLast(':')).since >= segment.since)
			throw GradleException("Segment $segmentPath can extend only an earlier segment, not $basePath")

		this.base.set(basePath)
		this.base.disallowChanges()
		dependencies.add(BASE_CONFIGURATION, base)
	}

	companion object {
		/**
		 * A top-level class's binary name: package segments, then a class name without the `$` of nested classes.
		 */
		private val BINARY_NAME = Regex("""(\p{javaJavaIdentifierStart}\p{javaJavaIdentifierPart}*\.)+"""
			+ """\p{javaJavaIdentifierStart}[\p{javaJavaIdentifierPart}&&[^$]]*""")

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
