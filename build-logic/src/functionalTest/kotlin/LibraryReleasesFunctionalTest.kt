package me.whereareiam.anvil.buildlogic

import me.whereareiam.anvil.buildlogic.LibraryRepository.LibraryClass
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat

/**
 * Pins the release data of a generated `demo` library offline, from two file repositories inside the project, with
 * the `build-libraries` settings plugin, which owns the library repositories and checks the family root, and the
 * `module-library` convention on the family root.
 */
class LibraryReleasesFunctionalTest {
	@TempDir
	lateinit var directory: File

	private val releasesPath = "anvil-protocol/protocol-demo/demo-releases.toml"
	private val snapshot = "1.21.11-20260512.221357-18"

	@Test
	fun `pins every release closure with module, repository URL and checksum and keeps hand-written data`() {
		val project = TestProject(directory)
		writeProject(project)
		val legacy = "com/example/legacy/protocol/1.18.2-1/protocol-1.18.2-1.jar"
		val legacyChecksum = sha256(project.file("repository/$legacy"))
		project.file(releasesPath).apply { parentFile.mkdirs() }.writeText(releases(legacyChecksum))

		val pinned = project.run(":anvil-protocol:protocol-demo:pinLibraryReleases")
		assertTrue(pinned.output.contains("Pinned 5 JARs of 2 releases in demo-releases.toml; 4 pins are new"), pinned.output)

		val expected = listOf(
			legacyRelease,
			table(project, "com.example.legacy:protocol:1.18.2-1", "repository", legacy),
			table(project, "com.example:auth:1.0", "repository", "com/example/auth/1.0/auth-1.0.jar"),
			table(project, "com.example:native:1.0:linux-x86_64", "repository", "com/example/native/1.0/native-1.0-linux-x86_64.jar"),
			snapshotRelease,
			table(project, "com.example.modern:protocol:$snapshot", "repository", "com/example/modern/protocol/1.21.11-SNAPSHOT/protocol-$snapshot.jar"),
			table(project, "com.example:auth:2.0", "mirror", "com/example/auth/2.0/auth-2.0.jar"),
		).joinToString("\n\n") + "\n"
		assertEquals(expected, project.file(releasesPath).readText())

		val again = project.run(":anvil-protocol:protocol-demo:pinLibraryReleases")
		assertTrue(again.output.contains("Pinned 5 JARs of 2 releases in demo-releases.toml; 0 pins are new"), again.output)
		assertEquals(expected, project.file(releasesPath).readText())

		val wrong = "b".repeat(64)
		val authChecksum = sha256(project.file("mirror/com/example/auth/2.0/auth-2.0.jar"))
		val tampered = expected.replace(authChecksum, wrong)
		project.file(releasesPath).writeText(tampered)
		val refused = project.fail(":anvil-protocol:protocol-demo:pinLibraryReleases")
		assertTrue(refused.output.contains("com.example:auth:2.0 is pinned to $wrong, but resolves to $authChecksum"), refused.output)
		assertEquals(tampered, project.file(releasesPath).readText())

		project.write("anvil-protocol/protocol-demo/build.gradle.kts", "plugins {\n\tbase\n}\n")
		val unpinned = project.fail("help")
		assertTrue(unpinned.output.contains(
			":anvil-protocol:protocol-demo owns the release data of a protocol library and must apply id(\"module-library\")"
		), unpinned.output)
		project.write("anvil-protocol/protocol-demo/build.gradle.kts", "plugins {\n\tid(\"module-library\")\n}\n")

		project.write("other/build.gradle.kts", "plugins {\n\tid(\"module-library\")\n}\n")
		project.write("settings.gradle.kts", project.file("settings.gradle.kts").readText() + "include(\":other\")\n")
		val foreign = project.fail("help")
		assertTrue(foreign.output.contains(":other applies module-library, but only a library family root"), foreign.output)
	}

	private fun writeProject(project: TestProject) {
		val repository = LibraryRepository(project.file("repository"))
		repository.publish("com.example:auth:1.0", listOf(LibraryClass("com/example/auth/GameProfile", listOf("public <init>()V"))))
		repository.publish("com.example:auth:2.0", listOf(LibraryClass("com/example/auth/GameProfile", listOf("public <init>()V", "public getName()Ljava/lang/String;"))))
		repository.publish("com.example:native:1.0", listOf(LibraryClass("com/example/Native")))
		repository.publish("com.example:native:1.0:linux-x86_64", listOf(LibraryClass("com/example/NativeLinux")))
		repository.publish(
			"com.example.legacy:protocol:1.18.2-1",
			listOf(LibraryClass("com/example/legacy/MinecraftProtocol", listOf("public <init>()V"))),
			listOf("com.example:auth:1.0", "com.example:native:1.0:linux-x86_64"),
		)
		repository.publish(
			"com.example.modern:protocol:$snapshot",
			listOf(LibraryClass("com/example/modern/MinecraftProtocol", listOf("public <init>()V"))),
			listOf("com.example:auth:2.0"),
		)
		// The first repository serves only auth 2.0, so that JAR's URL points at it.
		project.file("repository/com/example/auth/2.0").copyRecursively(project.file("mirror/com/example/auth/2.0"))

		project.write("settings.gradle.kts", """
			plugins {
				id("build-libraries")
			}

			libraryRepositories {
				url(rootDir.resolve("mirror").toURI().toString())
				url(rootDir.resolve("repository").toURI().toString())
			}

			dependencyResolutionManagement {
				repositories {
					maven { url = uri("mirror") }
					maven { url = uri("repository") }
				}
			}

			rootProject.name = "release-consumer"
			include(":anvil-protocol:protocol-demo")
		""")
		project.write("anvil-protocol/protocol-demo/build.gradle.kts", "plugins {\n\tid(\"module-library\")\n}\n")
	}

	private val legacyRelease = """
		# Demo releases: a hand-written header.

		[[release]]
		version = "1.18.2-1"
		# A hand-written note about this release.
		module = "com.example.legacy:protocol:1.18.2-1"
		protocol = 758
		minecraft = ["1.18.2"]
		verified = ["1.18.2"]
		java = 8
		features = ["ONLINE_AUTHENTICATION"]
	""".trimIndent()

	private val snapshotRelease = """
		# Snapshot releases resolve from their -SNAPSHOT folder.
		[[release]]
		version = "$snapshot"
		module = "com.example.modern:protocol:$snapshot"
		protocol = 774
		minecraft = ["1.21.11"]
		verified = ["1.21.11"]
		java = 17
		features = ["ONLINE_AUTHENTICATION"]
	""".trimIndent()

	private fun releases(legacyChecksum: String): String = listOf(
		legacyRelease,
		staleTable("com.example.legacy:protocol:1.18.2-1", legacyChecksum),
		staleTable("com.example:gone:1.0", ""),
		snapshotRelease,
		staleTable("com.example.modern:protocol:$snapshot", ""),
	).joinToString("\n\n") + "\n"

	private fun staleTable(module: String, sha256: String): String =
		"[[release.artifact]]\nmodule = \"$module\"\nurl = \"https://stale.example/${module.replace(':', '-')}.jar\"\nsha256 = \"$sha256\""

	private fun table(project: TestProject, module: String, repository: String, path: String): String {
		val file = project.file("$repository/$path")
		return "[[release.artifact]]\nmodule = \"$module\"\nurl = \"${file.toURI()}\"\nsha256 = \"${sha256(file)}\""
	}

	private fun sha256(file: File): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
}
