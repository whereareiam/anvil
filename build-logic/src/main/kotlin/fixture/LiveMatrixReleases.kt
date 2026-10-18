package me.whereareiam.anvil.buildlogic.fixture

import com.fasterxml.jackson.core.JacksonException
import com.fasterxml.jackson.dataformat.toml.TomlMapper
import org.gradle.api.GradleException

/**
 * Reads the releases of Anvil's live matrix (`live-matrix.toml`): the Minecraft version of each `[[release]]` row,
 * in the file's order, which is oldest first. The server test module registers one release test task per release
 * from it, and CI runs one job per release.
 */
object LiveMatrixReleases {
	private val version = Regex("""\d+(\.\d+)+""")

	/**
	 * Reads the releases from the file's text.
	 *
	 * @param text contents of `live-matrix.toml`
	 * @return Minecraft versions of the releases
	 * @throws GradleException when the text is not valid TOML, lists no release, or a release has no valid version
	 */
	fun parse(text: String): List<String> {
		val document = try {
			TomlMapper().readTree(text)
		} catch (exception: JacksonException) {
			throw GradleException("live-matrix.toml is not valid TOML: ${exception.originalMessage}", exception)
		}
		val releases = document.path("release").map { row -> row.path("minecraft").asText() }
		if (releases.isEmpty()) throw GradleException("live-matrix.toml lists no [[release]]")

		releases.firstOrNull { release -> !version.matches(release) }?.let { release ->
			throw GradleException("live-matrix.toml lists release '$release', which is not a Minecraft version")
		}
		if (releases.toSet().size != releases.size) throw GradleException("live-matrix.toml lists a release twice")

		return releases
	}
}
