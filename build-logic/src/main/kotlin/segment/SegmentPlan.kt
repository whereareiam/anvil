package me.whereareiam.anvil.buildlogic.segment

import me.whereareiam.anvil.buildlogic.library.LibraryRelease
import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.gradle.api.GradleException
import java.io.Serializable

/**
 * What one segment compiles against and must keep linking with.
 *
 * A segment starts at the key version of one library release and serves every later release until a sibling
 * segment starts, which is the same floor rule the worker uses to select a segment for a release.
 *
 * @property library registered library id
 * @property owner the library side folder holding the segment
 * @property segment the segment's name and start version
 * @property release the release the segment compiles against, whose key is the segment's start version
 * @property laterReleases releases the segment also serves, ordered by key
 */
data class SegmentPlan(
	val library: String,
	val owner: String,
	val segment: SegmentName,
	val release: LibraryRelease,
	val laterReleases: List<LibraryRelease>,
) : Serializable {
	companion object {
		private const val serialVersionUID = 1L

		/**
		 * Plans [segment] of [side] against the library's [releases].
		 *
		 * @throws GradleException when the segment does not start at the key version of a release
		 */
		fun of(side: LibrarySide, segment: SegmentName, releases: List<LibraryRelease>): SegmentPlan {
			val release = releases.singleOrNull { it.key == segment.since } ?: throw GradleException(
				"Segment ${side.name}/${segment.projectName} must start at the key version of a ${side.library} release; "
					+ "keys: ${releases.map { it.key }}"
			)
			val starts = side.segments.map(SegmentName::since)
			val later = releases
				.filter { it.key > segment.since && MinecraftVersion.floor(starts, { start -> start }, it.key) == segment.since }
				.sortedBy(LibraryRelease::key)
			return SegmentPlan(side.library, side.name, segment, release, later)
		}
	}
}
