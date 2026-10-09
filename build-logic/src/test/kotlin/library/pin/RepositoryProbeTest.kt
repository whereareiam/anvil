package me.whereareiam.anvil.buildlogic.library.pin

import com.sun.net.httpserver.HttpServer
import org.gradle.api.GradleException
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI

class RepositoryProbeTest {
	@TempDir
	lateinit var directory: File

	private lateinit var server: HttpServer
	private val path = "demo/lib/1.0/lib-1.0.jar"
	private val jar = "jar bytes".toByteArray()
	private val sha256 = Checksums.sha256(jar.inputStream())

	@BeforeEach
	fun serveRepository() {
		server = HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0)
		server.createContext("/maven/") { exchange ->
			exchange.use {
				if (exchange.requestURI.path != "/maven/$path") {
					exchange.sendResponseHeaders(404, -1)
					return@use
				}
				exchange.sendResponseHeaders(200, jar.size.toLong())
				exchange.responseBody.write(jar)
			}
		}
		server.start()
	}

	@AfterEach
	fun stopRepository() {
		server.stop(0)
	}

	@Test
	fun `returns the first repository in order that serves the JAR`() {
		val empty = File(directory, "empty").apply { mkdirs() }
		val http = URI("http://${server.address.hostString}:${server.address.port}/maven")

		val url = RepositoryProbe(listOf(empty.toURI(), http)).use { it.locate(path, sha256) }

		assertEquals("$http/$path", url)
	}

	@Test
	fun `serves file repositories`() {
		val repository = File(directory, "repository")
		File(repository, path).apply {
			parentFile.mkdirs()
			writeBytes(jar)
		}

		val url = RepositoryProbe(listOf(repository.toURI())).use { it.locate(path, sha256) }

		assertEquals(File(repository, path).toURI().toString(), url)
	}

	@Test
	fun `skips a repository that serves other bytes, such as an error page, for a later one that matches`() {
		val http = URI("http://${server.address.hostString}:${server.address.port}/maven")
		val other = File(directory, "other")
		File(other, path).apply {
			parentFile.mkdirs()
			writeText("<html>not the JAR</html>")
		}

		val url = RepositoryProbe(listOf(other.toURI(), http)).use { it.locate(path, sha256) }

		assertEquals("$http/$path", url)
	}

	@Test
	fun `fails when no library repository serves the bytes Gradle resolved and lists where it looked`() {
		val http = URI("http://${server.address.hostString}:${server.address.port}/maven/")
		val empty = File(directory, "empty").apply { mkdirs() }

		val mismatch = assertThrows<GradleException> { RepositoryProbe(listOf(http)).use { it.locate(path, "b".repeat(64)) } }
		val missing = assertThrows<GradleException> { RepositoryProbe(listOf(empty.toURI(), http)).use { it.locate("demo/other/1.0/other-1.0.jar", sha256) } }

		assertTrue(mismatch.message!!.contains("No library repository serves $path with the sha256 ${"b".repeat(64)}"), mismatch.message)
		assertTrue(mismatch.message!!.contains("$http$path (serves sha256 $sha256)"), mismatch.message)
		assertTrue(missing.message!!.contains("No library repository serves demo/other/1.0/other-1.0.jar"), missing.message)
		assertTrue(missing.message!!.contains("(HTTP 404)") && missing.message!!.contains("(not found)"), missing.message)
	}
}
