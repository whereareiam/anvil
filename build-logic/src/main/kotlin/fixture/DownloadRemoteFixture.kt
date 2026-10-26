package me.whereareiam.anvil.buildlogic.fixture

import me.whereareiam.anvil.buildlogic.library.pin.Checksums
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import java.io.IOException
import java.net.ProxySelector
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration

/**
 * Downloads a third-party JAR that live tests run as a fixture, such as a server plugin, from its pinned URL and
 * checks it against its pinned SHA-256 before publishing it to [output]. A fixture fetched this way only runs in a
 * test's process workspace; no Anvil artifact carries it.
 */
@DisableCachingByDefault(because = "Downloading the pinned file is as fast as restoring it from the build cache")
abstract class DownloadRemoteFixture : DefaultTask() {
	/**
	 * Where the file is downloaded from.
	 */
	@get:Input
	abstract val url: Property<String>

	/**
	 * The SHA-256 the downloaded bytes must have, in lower-case hex.
	 */
	@get:Input
	abstract val sha256: Property<String>

	/**
	 * The verified file.
	 */
	@get:OutputFile
	abstract val output: RegularFileProperty

	@TaskAction
	fun download() {
		val target = output.get().asFile.toPath()
		val expected = sha256.get()
		Files.createDirectories(target.parent)
		val partial = Files.createTempFile(target.parent, target.fileName.toString(), ".part")
		try {
			fetch(URI(url.get()), partial)
			val served = Checksums.sha256(partial.toFile())
			if (served != expected)
				throw GradleException("${url.get()} serves bytes with sha256 $served, but the fixture pins $expected; "
					+ "pins never change silently, so check the release and change the pin only on purpose")

			Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
		} finally {
			Files.deleteIfExists(partial)
		}
	}

	private fun fetch(source: URI, destination: Path) {
		val request = HttpRequest.newBuilder(source).timeout(Duration.ofMinutes(5)).GET().build()
		val response = HttpClient.newBuilder()
			.followRedirects(HttpClient.Redirect.NORMAL)
			.proxy(ProxySelector.getDefault())
			.connectTimeout(Duration.ofSeconds(30))
			.build()
			.use { client ->
				try {
					client.send(request, HttpResponse.BodyHandlers.ofFile(destination))
				} catch (exception: IOException) {
					throw GradleException("Could not download the fixture $source: ${exception.message}", exception)
				} catch (exception: InterruptedException) {
					Thread.currentThread().interrupt()
					throw GradleException("Interrupted while downloading the fixture $source", exception)
				}
			}
		if (response.statusCode() != 200)
			throw GradleException("Could not download the fixture $source: the server answered ${response.statusCode()}")
	}
}
