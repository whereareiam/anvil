package me.whereareiam.anvil.buildlogic.download

import java.io.IOException
import java.io.InputStream
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Reads files over HTTP for the tasks that pin what the build downloads, such as `pinLibraryReleases` and
 * `pinPacketData`: one client per task that follows redirects and the system's proxy. The ordinary build never reads
 * through it; it resolves pinned files through Gradle's repositories, which cache them and work offline.
 */
class HttpReader : AutoCloseable {
	private val connection = lazy {
		HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.proxy(ProxySelector.getDefault())
			.connectTimeout(Duration.ofSeconds(30))
			.build()
	}
	private val client: HttpClient by connection

	/**
	 * Reads [url] and hands its status and body to [read], which may consume the body.
	 *
	 * @param timeout how long the whole response may take
	 * @throws IOException when the request fails
	 */
	fun <T> get(url: URI, timeout: Duration, read: (status: Int, body: InputStream) -> T): T {
		val request = HttpRequest.newBuilder(url).timeout(timeout).GET().build()
		val response = try {
			client.send(request, HttpResponse.BodyHandlers.ofInputStream())
		} catch (exception: InterruptedException) {
			Thread.currentThread().interrupt()
			throw IOException("Interrupted while reading $url", exception)
		}

		return response.body().use { read(response.statusCode(), it) }
	}

	override fun close() {
		if (connection.isInitialized()) client.close()
	}
}
