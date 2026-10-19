package me.whereareiam.anvil.buildlogic.fixture

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException

/**
 * What the build needs of the physics suite's matrix (`physics-matrix.toml`): the Minecraft version of each
 * `[[release]]` row, in the file's order, which is oldest first, and the pinned downloads of the Grim and Sonar
 * plugins the suite runs as fixtures. The server test module registers one physics test task per release from it.
 *
 * @property releases Minecraft versions of the releases
 * @property grim the pinned GrimAC JAR
 * @property sonar the pinned Sonar JAR
 */
data class PhysicsMatrix(val releases: List<String>, val grim: Pin, val sonar: Pin) {
	/**
	 * A third-party JAR pinned by its URL and the SHA-256 of its bytes.
	 *
	 * @property url where the JAR is downloaded from
	 * @property sha256 SHA-256 of the JAR, in lower-case hex
	 */
	data class Pin(val url: String, val sha256: String)

	companion object {
		private val version = Regex("""\d+(\.\d+)+""")
		private val sha256 = Regex("[0-9a-f]{64}")

		/**
		 * Reads the matrix from the file's text.
		 *
		 * @param text contents of `physics-matrix.toml`
		 * @return the releases and the pins
		 * @throws GradleException when the text is not valid TOML, lists no release or a release twice, a release has
		 *         no valid version, or a pin lacks its URL or a valid SHA-256
		 */
		fun parse(text: String): PhysicsMatrix {
			val document = try {
				TomlMapper().readTree(text)
			} catch (exception: JacksonException) {
				throw GradleException("physics-matrix.toml is not valid TOML: ${exception.originalMessage}", exception)
			}
			val releases = document.path("release").map { row -> row.path("minecraft").asText() }
			if (releases.isEmpty()) throw GradleException("physics-matrix.toml lists no [[release]]")

			releases.firstOrNull { release -> !version.matches(release) }?.let { release ->
				throw GradleException("physics-matrix.toml lists release '$release', which is not a Minecraft version")
			}
			if (releases.toSet().size != releases.size) throw GradleException("physics-matrix.toml lists a release twice")

			return PhysicsMatrix(releases, pin(document, "grim"), pin(document, "sonar"))
		}

		private fun pin(document: JsonNode, table: String): Pin {
			val url = document.path(table).path("url").asText()
			val checksum = document.path(table).path("sha256").asText()
			if (!url.startsWith("https://"))
				throw GradleException("physics-matrix.toml needs an https url in [$table]")
			if (!sha256.matches(checksum))
				throw GradleException("physics-matrix.toml needs a lower-case hex sha256 in [$table]")

			return Pin(url, checksum)
		}
	}
}
