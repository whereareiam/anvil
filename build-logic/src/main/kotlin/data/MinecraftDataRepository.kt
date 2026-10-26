package me.whereareiam.anvil.buildlogic.data

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import me.whereareiam.anvil.buildlogic.library.pin.Checksums
import org.gradle.api.GradleException
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Reads files of the PrismarineJS minecraft-data repository on GitHub at one commit, and resolves the commit of a
 * release tag.
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
	private val client = HttpClient.newBuilder()
		.followRedirects(HttpClient.Redirect.NORMAL)
		.proxy(ProxySelector.getDefault())
		.connectTimeout(Duration.ofSeconds(30))
		.build()

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
	 * Reads [path] at [commit].
	 *
	 * @throws GradleException when the repository does not serve the file
	 */
	fun read(commit: String, path: String): ByteArray = get(contentRoot.resolve("$repository/$commit/$path"), path)

	/**
	 * Reads [file] at [commit] and checks its bytes against the file's pin.
	 *
	 * @throws GradleException when the repository does not serve the file, or serves other bytes
	 */
	fun read(commit: String, file: BlockDataPins.PinnedFile): ByteArray {
		val bytes = read(commit, file.path)
		val served = Checksums.sha256(bytes.inputStream())
		if (served != file.sha256)
			throw GradleException("minecraft-data serves ${file.path} at $commit with sha256 $served, but block-data.toml pins "
				+ "${file.sha256}; pins never change silently, so check the file and run pinBlockData only on purpose")

		return bytes
	}

	/**
	 * Reads [path] at [commit] as JSON.
	 */
	fun json(commit: String, path: String): JsonNode = json.readTree(read(commit, path))

	override fun close() = client.close()

	private fun json(url: URI, subject: String): JsonNode = json.readTree(get(url, subject))

	private fun get(url: URI, subject: String): ByteArray {
		val request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(2)).GET().build()
		val response = try {
			client.send(request, HttpResponse.BodyHandlers.ofByteArray())
		} catch (exception: IOException) {
			throw GradleException("Could not read minecraft-data $subject from $url: ${exception.message}", exception)
		} catch (exception: InterruptedException) {
			Thread.currentThread().interrupt()
			throw GradleException("Interrupted while reading minecraft-data $subject from $url", exception)
		}
		if (response.statusCode() != 200)
			throw GradleException("minecraft-data does not serve $subject: $url answered ${response.statusCode()}")

		return response.body()
	}
}
