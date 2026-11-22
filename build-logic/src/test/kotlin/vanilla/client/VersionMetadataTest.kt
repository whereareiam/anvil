package me.whereareiam.anvil.buildlogic.vanilla.client

import com.fasterxml.jackson.databind.ObjectMapper
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VersionMetadataTest {
	private val macArm = ClientPlatform("osx", "arm64")
	private val macIntel = ClientPlatform("osx", "x86_64")

	/**
	 * The layout until 1.18.2: natives named per system in `natives`, built for x86_64 only, and libraries that the
	 * rules give macOS in another version.
	 */
	private val legacy = metadata("""
		{
		  "id": "1.16.5",
		  "downloads": {
		    "client": { "url": "https://example.com/client.jar", "sha1": "${sha("c")}" },
		    "client_mappings": { "url": "https://example.com/client.txt", "sha1": "${sha("m")}" }
		  },
		  "libraries": [
		    { "name": "com.mojang:brigadier:1.0.17", "downloads": { "artifact": ${artifact("com/mojang/brigadier/1.0.17/brigadier-1.0.17.jar")} } },
		    { "name": "org.lwjgl:lwjgl:3.2.1", "rules": [{ "action": "allow", "os": { "name": "osx" } }],
		      "downloads": { "artifact": ${artifact("org/lwjgl/lwjgl/3.2.1/lwjgl-3.2.1.jar")} } },
		    { "name": "org.lwjgl:lwjgl:3.2.2", "rules": [{ "action": "allow" }, { "action": "disallow", "os": { "name": "osx" } }],
		      "downloads": { "artifact": ${artifact("org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2.jar")} } },
		    { "name": "org.lwjgl:lwjgl:3.2.2", "rules": [{ "action": "allow" }, { "action": "disallow", "os": { "name": "osx" } }],
		      "natives": { "linux": "natives-linux", "windows": "natives-windows" },
		      "downloads": {
		        "artifact": ${artifact("org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2.jar")},
		        "classifiers": {
		          "natives-linux": ${artifact("org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-linux.jar")},
		          "natives-windows": ${artifact("org/lwjgl/lwjgl/3.2.2/lwjgl-3.2.2-natives-windows.jar")}
		        }
		      } },
		    { "name": "org.lwjgl:lwjgl:3.2.1", "rules": [{ "action": "allow", "os": { "name": "osx" } }],
		      "natives": { "osx": "natives-macos" },
		      "downloads": { "classifiers": { "natives-macos": ${artifact("org/lwjgl/lwjgl/3.2.1/lwjgl-3.2.1-natives-macos.jar")} } } }
		  ]
		}
	""")

	/**
	 * The layout from 1.19: every natives JAR a library of its own, its classifier naming system and architecture.
	 */
	private val current = metadata("""
		{
		  "id": "26.3",
		  "downloads": { "client": { "url": "https://example.com/client.jar", "sha1": "${sha("c")}" } },
		  "libraries": [
		    { "name": "org.lwjgl:lwjgl:3.4.3", "downloads": { "artifact": ${artifact("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3.jar")} } },
		    { "name": "org.lwjgl:lwjgl:3.4.3:natives-macos", "rules": [{ "action": "allow", "os": { "name": "osx" } }],
		      "downloads": { "artifact": ${artifact("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos.jar")} } },
		    { "name": "org.lwjgl:lwjgl:3.4.3:natives-macos-arm64", "rules": [{ "action": "allow", "os": { "name": "osx" } }],
		      "downloads": { "artifact": ${artifact("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-macos-arm64.jar")} } },
		    { "name": "org.lwjgl:lwjgl:3.4.3:natives-linux", "rules": [{ "action": "allow", "os": { "name": "linux" } }],
		      "downloads": { "artifact": ${artifact("org/lwjgl/lwjgl/3.4.3/lwjgl-3.4.3-natives-linux.jar")} } },
		    { "name": "ca.weblite:java-objc-bridge:1.1", "rules": [{ "action": "allow", "os": { "name": "osx" } }],
		      "downloads": { "artifact": ${artifact("ca/weblite/java-objc-bridge/1.1/java-objc-bridge-1.1.jar")} } }
		  ]
		}
	""")

	@Test
	fun `reads the client and its mappings`() {
		assertEquals("1.16.5", legacy.minecraft)
		assertEquals(VersionMetadata.Download("client.jar", "https://example.com/client.jar", sha("c")), legacy.client)
		assertEquals("client.txt", legacy.mappings!!.path)
		assertNull(current.mappings)
	}

	@Test
	fun `chooses the legacy libraries and natives per system`() {
		assertEquals(
			listOf("brigadier-1.0.17.jar", "lwjgl-3.2.2.jar", "lwjgl-3.2.2-natives-linux.jar"),
			legacy.libraries(ClientPlatform.CONTAINER).map { it.path.substringAfterLast('/') },
		)
		assertEquals(
			listOf("brigadier-1.0.17.jar", "lwjgl-3.2.1.jar", "lwjgl-3.2.1-natives-macos.jar"),
			legacy.libraries(macIntel).map { it.path.substringAfterLast('/') },
		)
	}

	@Test
	fun `ships legacy natives for x86_64 only`() {
		assertTrue(legacy.runsOn(ClientPlatform.CONTAINER))
		assertTrue(legacy.runsOn(macIntel))
		assertFalse(legacy.runsOn(macArm))
	}

	@Test
	fun `chooses natives by the architecture their classifier names`() {
		assertEquals(
			listOf("lwjgl-3.4.3.jar", "java-objc-bridge-1.1.jar", "lwjgl-3.4.3-natives-macos-arm64.jar"),
			current.libraries(macArm).map { it.path.substringAfterLast('/') },
		)
		assertEquals(
			listOf("lwjgl-3.4.3.jar", "lwjgl-3.4.3-natives-linux.jar"),
			current.libraries(ClientPlatform.CONTAINER).map { it.path.substringAfterLast('/') },
		)
		assertTrue(current.runsOn(macArm))
		assertFalse(current.runsOn(ClientPlatform("windows", "x86_64")))
	}

	@Test
	fun `refuses metadata without a client`() {
		assertThrows<GradleException> { metadata("""{ "id": "1.21.11", "downloads": {}, "libraries": [] }""") }
	}

	@Test
	fun `maps the JVM's system and architecture`() {
		assertEquals(macArm, ClientPlatform.of("Mac OS X", "aarch64"))
		assertEquals(ClientPlatform.CONTAINER, ClientPlatform.of("Linux", "amd64"))
		assertThrows<IllegalStateException> { ClientPlatform.of("FreeBSD", "amd64") }
	}

	private fun metadata(json: String) = VersionMetadata.parse(ObjectMapper().readTree(json.trimIndent()), "test.json")

	private fun artifact(path: String) = """{ "path": "$path", "url": "https://libraries.minecraft.net/$path", "sha1": "${sha(path)}" }"""

	private fun sha(seed: String) = seed.hashCode().toUInt().toString(16).padStart(8, '0').repeat(5)
}
