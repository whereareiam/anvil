package me.whereareiam.anvil.buildlogic.adapter

import me.whereareiam.anvil.buildlogic.library.LibraryRelease
import me.whereareiam.anvil.buildlogic.library.MinecraftVersion
import me.whereareiam.anvil.buildlogic.library.ReleaseArtifact
import org.gradle.api.GradleException
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SegmentPlanTest {
	private val releases = listOf(
		release("1.16.5-2", "1.16.4", "1.16.5"),
		release("1.17.1-2", "1.17.1"),
		release("1.18.2-1", "1.18.2"),
		release("1.19.4-1", "1.19.4"),
		release("1.20.6-1", "1.20.5", "1.20.6"),
		release("26.1", "26.1", "26.1.1", "26.1.2"),
	)

	@Test
	fun `selects the segment with the greatest start not newer than the release key`() {
		val starts = listOf("1.16.5", "1.18.2", "1.20.6").map(MinecraftVersion::parse)

		assertEquals(MinecraftVersion.parse("1.16.5"), MinecraftVersion.floor(starts, { it }, MinecraftVersion.parse("1.17.1")))
		assertEquals(MinecraftVersion.parse("1.18.2"), MinecraftVersion.floor(starts, { it }, MinecraftVersion.parse("1.19.4")))
		assertEquals(MinecraftVersion.parse("1.20.6"), MinecraftVersion.floor(starts, { it }, MinecraftVersion.parse("26.1.2")))
		assertNull(MinecraftVersion.floor(starts, { it }, MinecraftVersion.parse("1.16.4")))
	}

	@Test
	fun `compiles against its start release and links with later releases until the next segment`() {
		val side = side("V1_16_5", "V1_18_2", "V1_20_6")

		val first = SegmentPlan.of(side, SegmentName.parse("V1_16_5"), releases)
		val middle = SegmentPlan.of(side, SegmentName.parse("V1_18_2"), releases)
		val last = SegmentPlan.of(side, SegmentName.parse("V1_20_6"), releases)

		assertEquals("1.16.5-2", first.release.version)
		assertEquals(listOf("1.17.1-2"), first.laterReleases.map(LibraryRelease::version))
		assertEquals(listOf("1.19.4-1"), middle.laterReleases.map(LibraryRelease::version))
		assertEquals(listOf("26.1"), last.laterReleases.map(LibraryRelease::version))
		assertEquals("messages-mcprotocol", last.owner)
		assertEquals("mcprotocol", last.library)
	}

	@Test
	fun `serves every later release when it is the only segment`() {
		val plan = SegmentPlan.of(side("V1_16_5"), SegmentName.parse("V1_16_5"), releases)

		assertEquals(listOf("1.17.1-2", "1.18.2-1", "1.19.4-1", "1.20.6-1", "26.1"), plan.laterReleases.map(LibraryRelease::version))
	}

	@Test
	fun `must start at a release key rather than any served version`() {
		val failure = assertThrows<GradleException> { SegmentPlan.of(side("V1_20_5"), SegmentName.parse("V1_20_5"), releases) }

		assertTrue(failure.message!!.contains("must start at the key version of a mcprotocol release"), failure.message)
	}

	private fun side(vararg segments: String) = LibrarySide("messages-mcprotocol", "mcprotocol", segments.map(SegmentName::parse))

	private fun release(version: String, vararg minecraft: String): LibraryRelease {
		val module = "demo:protocol:$version"
		return LibraryRelease(
			version = version,
			module = module,
			protocol = 1,
			minecraft = minecraft.map(MinecraftVersion::parse),
			verified = emptyList(),
			java = 17,
			features = emptyList(),
			artifacts = listOf(ReleaseArtifact(module, "https://repository.example/$version.jar", "")),
		)
	}
}
