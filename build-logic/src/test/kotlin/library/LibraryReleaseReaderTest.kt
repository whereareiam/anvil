package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.File

class LibraryReleaseReaderTest {
	@TempDir
	lateinit var directory: File

	@Test
	fun `reads releases ordered by key with the highest Minecraft version as key`() {
		val releases = read(release("1.20.6-1", "1.20.5\", \"1.20.6", protocol = 766) + release("1.16.5-2", "1.16.4\", \"1.16.5"))

		assertEquals(listOf("1.16.5-2", "1.20.6-1"), releases.map(LibraryRelease::version))
		val modern = releases.last()
		assertEquals(MinecraftVersion.parse("1.20.6"), modern.key)
		assertEquals(766, modern.protocol)
		assertEquals(listOf(MinecraftVersion.parse("1.20.5"), MinecraftVersion.parse("1.20.6")), modern.minecraft)
		assertEquals(listOf(MinecraftVersion.parse("1.20.6")), modern.verified)
		assertEquals(17, modern.java)
		assertEquals(listOf("ONLINE_AUTHENTICATION"), modern.features)
		assertEquals(ReleaseArtifact("demo:protocol:1.20.6-1", "https://repository.example/protocol-1.20.6-1.jar", ""), modern.artifacts.single())
	}

	@Test
	fun `accepts pinned checksums and omitted features`() {
		val checksum = "a".repeat(64)
		val releases = read(release("1.18.2-1", "1.18.2", sha256 = checksum).replace("features = [\"ONLINE_AUTHENTICATION\"]\n", ""))

		assertEquals(checksum, releases.single().artifacts.single().sha256)
		assertEquals(emptyList<String>(), releases.single().features)
	}

	@Test
	fun `accepts classifiers on closure artifacts only`() {
		val native = "demo:transport:1.0:linux-x86_64"
		val releases = read(release("1.18.2-1", "1.18.2") + "\n[[release.artifact]]\nmodule = \"$native\"\nurl = \"https://repository.example/transport.jar\"\nsha256 = \"\"\n")

		assertEquals(native, releases.single().artifacts.last().module)
		assertFailure("must be a group:name:version coordinate", release("1.18.2-1", "1.18.2").replace("module = \"demo:protocol:1.18.2-1\"\nprotocol", "module = \"$native\"\nprotocol"))
	}

	@Test
	fun `rejects duplicate release versions`() {
		assertFailure("declares release 1.18.2-1 more than once", release("1.18.2-1", "1.18.1") + release("1.18.2-1", "1.18.2"))
	}

	@Test
	fun `rejects a Minecraft version served by two releases`() {
		assertFailure("assigns Minecraft 1.20.5 to more than one release", release("1.20.5-1", "1.20.5") + release("1.20.6-1", "1.20.5\", \"1.20.6"))
	}

	@Test
	fun `rejects a verified version the release does not list`() {
		assertFailure("verifies Minecraft 1.20.6, which it does not list", release("1.20.4-1", "1.20.4", verified = "1.20.6"))
	}

	@Test
	fun `rejects a release without a runtime closure`() {
		val row = release("1.18.2-1", "1.18.2").substringBefore("[[release.artifact]]")

		assertFailure("must declare its runtime closure", row)
	}

	@Test
	fun `rejects a closure without the release module`() {
		assertFailure("must include its module demo:protocol:1.18.2-1", release("1.18.2-1", "1.18.2", artifactModule = "demo:other:1.0"))
	}

	@Test
	fun `accepts classified artifacts but only plain release modules`() {
		val native = "\n[[release.artifact]]\nmodule = \"io.netty:netty-transport-native-epoll:4.2.1.Final:linux-x86_64\"\n" +
			"url = \"https://repository.example/epoll.jar\"\nsha256 = \"\"\n"

		val release = read(release("1.18.2-1", "1.18.2") + native).single()

		assertEquals("io.netty:netty-transport-native-epoll:4.2.1.Final:linux-x86_64", release.artifacts.last().module)
		assertFailure("module 'demo:protocol:1.18.2-1:linux' must be a group:name:version coordinate",
			release("1.18.2-1", "1.18.2", artifactModule = "demo:protocol:1.18.2-1:linux")
				.replace("module = \"demo:protocol:1.18.2-1\"\nprotocol", "module = \"demo:protocol:1.18.2-1:linux\"\nprotocol"))
	}

	@Test
	fun `rejects malformed checksums, coordinates, versions and fields`() {
		assertFailure("64-digit lower-case hex sha256", release("1.18.2-1", "1.18.2", sha256 = "ABC"))
		assertFailure("must be a group:name:version coordinate", release("1.18.2-1", "1.18.2").replace("module = \"demo:protocol:1.18.2-1\"\nprotocol", "module = \"demo-protocol\"\nprotocol"))
		assertFailure("'1.19-pre1' is not a Minecraft release version", release("1.19-1", "1.19-pre1"))
		assertFailure("unknown fields [versoin]", release("1.18.2-1", "1.18.2") + "versoin = 1\n")
		assertFailure("java as an integer of at least 8", release("1.18.2-1", "1.18.2").replace("java = 17", "java = 7"))
		assertFailure("at least one [[release]]", "")
		assertFailure("is not valid TOML", "[[release]\n")
	}

	private fun read(text: String): List<LibraryRelease> {
		val file = File(directory, "demo-releases.toml")
		file.writeText(text)
		return LibraryReleaseReader.read(file)
	}

	private fun assertFailure(message: String, text: String) {
		val failure = assertThrows<GradleException> { read(text) }

		assertTrue(failure.message!!.contains(message), failure.message)
	}

	private fun release(
		version: String,
		minecraft: String,
		protocol: Int = 758,
		verified: String = minecraft.substringAfterLast('"'),
		sha256: String = "",
		artifactModule: String = "demo:protocol:$version",
	): String = """
		[[release]]
		version = "$version"
		module = "demo:protocol:$version"
		protocol = $protocol
		minecraft = ["$minecraft"]
		verified = ["$verified"]
		java = 17
		features = ["ONLINE_AUTHENTICATION"]

		[[release.artifact]]
		module = "$artifactModule"
		url = "https://repository.example/protocol-$version.jar"
		sha256 = "$sha256"

	""".trimIndent() + "\n"
}
