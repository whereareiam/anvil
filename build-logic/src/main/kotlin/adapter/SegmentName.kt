package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.library.LibraryLayout
import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import org.gradle.api.GradleException
import java.io.Serializable

/**
 * The name of a segment: its folder and project are named `V<major>_<minor>[_<patch>]` after the release key
 * from which the segment applies, such as `V1_16_5` or `V26_1`, and its classes live in a package ending
 * in the lower-case form, such as `.v1_16_5`.
 *
 * @property since the Minecraft version from which the segment applies
 */
data class SegmentName(val since: MinecraftVersion) : Serializable {
	/**
	 * Canonical project and folder name.
	 */
	val projectName: String
		get() = "V" + since.format("_")

	/**
	 * Last component of the package that holds the segment's classes.
	 */
	val packageName: String
		get() = "v" + since.format("_")

	companion object {
		private const val serialVersionUID = 1L

		/**
		 * Reads a segment project name.
		 *
		 * @throws GradleException when the name is not a segment name or not written canonically
		 */
		fun parse(name: String): SegmentName {
			if (!LibraryLayout.segmentName.matches(name))
				throw GradleException("Segment project '$name' must be named V<major>_<minor>[_<patch>], for example V1_16_5")
			val segment = SegmentName(MinecraftVersion.parse(name.drop(1).replace('_', '.')))
			if (segment.projectName != name)
				throw GradleException("Segment project '$name' must be named ${segment.projectName}")
			return segment
		}
	}
}
