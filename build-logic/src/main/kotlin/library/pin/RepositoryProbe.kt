package me.whereareiam.anvil.buildlogic.library.pin

import org.gradle.api.GradleException
import java.io.File
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Finds the download URL of a resolved JAR: the first of the library repositories, in their declared order, that
 * serves exactly the bytes Gradle resolved at the JAR's repository path.
 *
 * Only the library repositories the settings declare are searched, never every repository the build resolves from,
 * so a pin always names a library repository. A repository that does not serve the path, or serves other bytes,
 * such as an HTML error page or a mirror with a different build, is skipped and reported if no repository matches.
 * Files and HTTP(S) repositories are supported; other repository kinds are skipped.
 *
 * @param repositories library repository root URLs in search order
 */
class RepositoryProbe(repositories: List<URI>) : AutoCloseable {
	private val roots = repositories.map { URI(it.toString().removeSuffix("/") + "/") }
	private val located = mutableMapOf<String, String>()
	private val connection = lazy {
		HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.proxy(ProxySelector.getDefault())
			.connectTimeout(Duration.ofSeconds(30))
			.build()
	}
	private val client: HttpClient by connection

	/**
	 * Returns the URL of the first library repository that serves [path] with checksum [sha256].
	 *
	 * @param sha256 checksum of the JAR Gradle resolved
	 * @throws GradleException when no library repository serves those bytes at the path
	 */
	fun locate(path: String, sha256: String): String = located.getOrPut(path) {
		val misses = mutableListOf<String>()
		for (root in roots) {
			val url = root.resolve(path)
			val served = when (url.scheme) {
				"file" -> fileChecksum(url, misses)
				"http", "https" -> httpChecksum(url, misses)
				else -> {
					misses += "$url (unsupported repository)"
					null
				}
			}
			if (served == sha256) return@getOrPut url.toString()
			if (served != null) misses += "$url (serves sha256 $served)"
		}
		throw GradleException("No library repository serves $path with the sha256 $sha256 that Gradle resolved; Gradle may "
			+ "have used a local copy, such as one in Maven Local, or a repository that is not a library repository:\n"
			+ misses.joinToString("\n") { "  $it" })
	}

	override fun close() {
		if (connection.isInitialized()) client.close()
	}

	private fun fileChecksum(url: URI, misses: MutableList<String>): String? {
		val file = File(url)
		if (file.isFile) return Checksums.sha256(file)

		misses += "$url (not found)"
		return null
	}

	private fun httpChecksum(url: URI, misses: MutableList<String>): String? {
		val request = HttpRequest.newBuilder(url).timeout(Duration.ofMinutes(5)).GET().build()
		try {
			val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
			return response.body().use { body ->
				if (response.statusCode() == 200) return@use Checksums.sha256(body)
				misses += "$url (HTTP ${response.statusCode()})"
				null
			}
		} catch (exception: IOException) {
			misses += "$url (${exception.message})"
			return null
		}
	}
}
