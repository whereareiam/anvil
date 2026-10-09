package me.whereareiam.anvil.buildlogic.library

import java.io.Serializable

/**
 * One JAR of a library release's worker runtime closure.
 *
 * @property module Maven coordinate `group:name:version`, with a `:classifier` for classified JARs
 * @property url download location
 * @property sha256 lower-case hex checksum, or empty while the artifact is not pinned yet
 */
data class ReleaseArtifact(val module: String, val url: String, val sha256: String) : Serializable {
	private companion object {
		private const val serialVersionUID = 1L
	}
}
