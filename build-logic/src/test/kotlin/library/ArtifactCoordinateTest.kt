package me.whereareiam.anvil.buildlogic.library

import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class ArtifactCoordinateTest {
	@Test
	fun `describes a plain JAR`() {
		val coordinate = ArtifactCoordinate.of("io.netty", "netty-all", "4.1.56.Final", "netty-all-4.1.56.Final.jar")

		assertEquals("io.netty:netty-all:4.1.56.Final", coordinate.module)
		assertEquals("io/netty/netty-all/4.1.56.Final/netty-all-4.1.56.Final.jar", coordinate.repositoryPath)
	}

	@Test
	fun `keeps the classifier of a secondary JAR`() {
		val coordinate = ArtifactCoordinate.of(
			"io.netty", "netty-transport-native-epoll", "4.2.1.Final", "netty-transport-native-epoll-4.2.1.Final-linux-x86_64.jar"
		)

		assertEquals("io.netty:netty-transport-native-epoll:4.2.1.Final:linux-x86_64", coordinate.module)
		assertEquals(
			"io/netty/netty-transport-native-epoll/4.2.1.Final/netty-transport-native-epoll-4.2.1.Final-linux-x86_64.jar",
			coordinate.repositoryPath,
		)
	}

	@Test
	fun `places a unique snapshot in its snapshot folder whatever name Gradle caches it under`() {
		listOf("protocol-26.1-20260708.090514-22.jar", "protocol-26.1-SNAPSHOT.jar").forEach { fileName ->
			val coordinate = ArtifactCoordinate.of("org.geysermc.mcprotocollib", "protocol", "26.1-20260708.090514-22", fileName)

			assertEquals("org.geysermc.mcprotocollib:protocol:26.1-20260708.090514-22", coordinate.module)
			assertEquals(
				"org/geysermc/mcprotocollib/protocol/26.1-SNAPSHOT/protocol-26.1-20260708.090514-22.jar",
				coordinate.repositoryPath,
			)
		}
	}

	@Test
	fun `refuses changing snapshots and files that are not JARs of the module`() {
		val snapshot = assertThrows<GradleException> { ArtifactCoordinate.of("demo", "lib", "1.0-SNAPSHOT", "lib-1.0-SNAPSHOT.jar") }
		val foreign = assertThrows<GradleException> { ArtifactCoordinate.of("demo", "lib", "1.0", "other-1.0.jar") }
		val archive = assertThrows<GradleException> { ArtifactCoordinate.of("demo", "lib", "1.0", "lib-1.0.zip") }

		assertTrue(snapshot.message!!.contains("changing snapshot"), snapshot.message)
		assertTrue(foreign.message!!.contains("is not a JAR of demo:lib:1.0"), foreign.message)
		assertTrue(archive.message!!.contains("is not a JAR of demo:lib:1.0"), archive.message)
	}
}
