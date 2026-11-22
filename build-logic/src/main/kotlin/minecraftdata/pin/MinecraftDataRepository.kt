package me.whereareiam.anvil.buildlogic.minecraftdata.pin

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.whereareiam.anvil.buildlogic.download.Checksums
import me.whereareiam.anvil.buildlogic.download.HttpReader
import org.gradle.api.GradleException
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.time.Duration

/**
 * Reads the PrismarineJS minecraft-data repository on GitHub for the pin tasks of minecraft-data pins (`pinPacketData`): the commit of a release tag, and
 * files at one commit. The ordinary build never reads through it; it resolves the pinned files through Gradle.
 *
 * Files come from [contentRoot] as `<root>/<repository>/<commit>/<path>`, tags from the GitHub REST API at
 * [apiRoot]. Both default to GitHub's hosts; tests point them at other servers.
 *
 * @param repository the GitHub repository, `owner/name`
 */
class MinecraftDataRepository(
	private val repository: String,
	private val contentRoot: URI = URI("https://raw.githubusercontent.com/"),
	private val apiRoot: URI = URI("https://api.github.com/"),
) : AutoCloseable {
	private val json = ObjectMapper()
	private val http = HttpReader()

	/**
	 * Resolves the commit that release tag [version] points to, following an annotated tag to its commit.
	 *
	 * @throws GradleException when the repository has no such tag
	 */
	fun commitOf(version: String): String {
		var reference = json(apiRoot.resolve("repos/$repository/git/ref/tags/$version"), "tag $version").path("object")
		while (reference.path("type").asText() == "tag")
			reference = json(URI(reference.path("url").asText()), "tag $version").path("object")
		if (reference.path("type").asText() != "commit")
			throw GradleException("minecraft-data tag $version does not point to a commit")

		return reference.path("sha").asText()
	}

	/**
	 * Reads [path] at [commit] as JSON.
	 *
	 * @throws GradleException when the repository does not serve the file
	 */
	fun json(commit: String, path: String): JsonNode = json(contentRoot.resolve("$repository/$commit/$path"), path)

	/**
	 * Returns the sha256 of [path] at [commit].
	 *
	 * @throws GradleException when the repository does not serve the file
	 */
	fun sha256(commit: String, path: String): String =
		read(contentRoot.resolve("$repository/$commit/$path"), path) { Checksums.sha256(it) }

	override fun close() = http.close()

	private fun json(url: URI, subject: String): JsonNode = read(url, subject) { json.readTree(it) }

	private fun <T> read(url: URI, subject: String, read: (InputStream) -> T): T = try {
		http.get(url, Duration.ofMinutes(2)) { status, body ->
			if (status != 200) throw GradleException("minecraft-data does not serve $subject: $url answered $status")
			read(body)
		}
	} catch (exception: IOException) {
		throw GradleException("Could not read minecraft-data $subject from $url: ${exception.message}", exception)
	}
}
