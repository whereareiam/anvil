package me.whereareiam.anvil.protocol.mcprotocol.catalog;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.mcprotocol.model.ReleaseDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolReleaseCatalogTest {
	private static final String SNAPSHOT = """
			[[release]]
			version = "1.21.11-20260512.221357-18"
			module = "org.geysermc.mcprotocollib:protocol:1.21.11-20260512.221357-18"
			protocol = 774
			minecraft = ["1.21.11"]
			verified = ["1.21.11"]
			java = 17
			features = ["ONLINE_AUTHENTICATION"]

			[[release.artifact]]
			module = "org.geysermc.mcprotocollib:protocol:1.21.11-20260512.221357-18"
			url = "https://repo.opencollab.dev/maven-snapshots/org/geysermc/mcprotocollib/protocol/1.21.11-SNAPSHOT/protocol-1.21.11-20260512.221357-18.jar"
			sha256 = "00d9ae3464dac8dcfe861303d728cb3361a9794d8ad0455aa340229ee83ee709"
			""";

	@TempDir
	Path temporary;

	@Test
	void loadsOneReleasePerMajorFromTheBuiltInData() {
		McProtocolReleaseCatalog catalog = McProtocolReleaseCatalog.load(null);

		Map<String, Integer> protocols = Map.of(
				"1.18.2", 758, "1.21.1", 767, "1.21.11", 774, "26.1.2", 775
		);
		List<ProtocolRelease> releases = catalog.releases();
		assertEquals(List.of("1.18.2", "1.21.1", "1.21.11", "26.1.2"),
				releases.stream().map(release -> release.version().toString()).toList());
		for (ProtocolRelease release : releases) {
			assertEquals(protocols.get(release.version().toString()), release.getProtocolNumber());
			assertEquals(SupportLevel.VERIFIED, release.support(release.version()));
			assertTrue(release.getFeatures().contains(ProtocolFeature.ONLINE_AUTHENTICATION));
			assertFalse(release.isAdditional());

			ReleaseDefinition definition = catalog.require(release);
			assertTrue(definition.getArtifacts().stream().anyMatch(artifact -> artifact.getModule().equals(definition.getModule())));
		}

		ProtocolRelease current = releases.getLast();
		assertEquals("26.1-20260708.090514-22", current.getLibraryVersion());
		assertEquals(SupportLevel.COMPATIBLE, current.support(MinecraftVersion.parse("26.1")));
		assertEquals(8, releases.getFirst().getJavaVersion());
		assertEquals(17, current.getJavaVersion());
		assertThrows(IllegalArgumentException.class, () -> catalog.require(current.toBuilder().libraryVersion("unknown").build()));
		assertThrows(UnsupportedOperationException.class, () -> catalog.releases().clear());
	}

	@Test
	void ignoresAnIdenticalAdditionalReleaseWithAnInfoLine() {
		List<String> notices = new ArrayList<>();
		ReleaseDefinition builtIn = read(SNAPSHOT, false).getFirst();
		McProtocolReleaseCatalog catalog = new McProtocolReleaseCatalog(List.of(builtIn), read(SNAPSHOT, true), notices::add);

		assertEquals(1, notices.size());
		assertTrue(notices.getFirst().startsWith("[Anvil] Info: additional MCProtocolLib release 1.21.11-20260512.221357-18"));
		assertSame(builtIn, catalog.require(builtIn.getRelease()));
		assertFalse(catalog.releases().getFirst().isAdditional());
	}

	@Test
	void ignoresAnIdenticalUnpinnedAdditionalReleaseAlthoughItsLaunchRefusalNamesAnotherRemedy() {
		List<String> notices = new ArrayList<>();
		String unpinned = SNAPSHOT.replace("00d9ae3464dac8dcfe861303d728cb3361a9794d8ad0455aa340229ee83ee709", "");
		ReleaseDefinition builtIn = read(unpinned, false).getFirst();
		List<ReleaseDefinition> copy = read(unpinned, true);
		assertNotEquals(builtIn.getRelease().getLaunchRefusal(), copy.getFirst().getRelease().getLaunchRefusal());

		McProtocolReleaseCatalog catalog = new McProtocolReleaseCatalog(List.of(builtIn), copy, notices::add);

		assertEquals(1, notices.size(), notices.toString());
		assertSame(builtIn, catalog.require(builtIn.getRelease()));
		assertTrue(catalog.releases().getFirst().getLaunchRefusal().endsWith("pinLibraryReleases`"),
				catalog.releases().getFirst().getLaunchRefusal());
	}

	@Test
	void loadsAFileCopyingABuiltInReleaseUnchanged() throws Exception {
		String builtIn;
		try (InputStream input = McProtocolReleaseCatalog.class.getClassLoader().getResourceAsStream(McProtocolReleaseCatalog.RESOURCE)) {
			builtIn = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		int first = builtIn.indexOf("[[release]]");
		Path file = temporary.resolve("copied-releases.toml");
		Files.writeString(file, builtIn.substring(first, builtIn.indexOf("[[release]]", first + 1)));

		McProtocolReleaseCatalog catalog = McProtocolReleaseCatalog.load(file);

		assertEquals(McProtocolReleaseCatalog.load(null).releases(), catalog.releases());
		assertTrue(catalog.releases().stream().noneMatch(ProtocolRelease::isAdditional));
	}

	@Test
	void refusesAConflictingAdditionalReleaseOfTheSameVersion() {
		ReleaseDefinition builtIn = read(SNAPSHOT, false).getFirst();
		List<ReleaseDefinition> conflicting = read(SNAPSHOT.replace("protocol = 774", "protocol = 773"), true);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new McProtocolReleaseCatalog(List.of(builtIn), conflicting, ignored -> { }));
		assertTrue(failure.getMessage().contains("conflicts with the built-in release"), failure.getMessage());
	}

	@Test
	void marksNewAdditionalReleasesUntestedAndKeepsEachMinecraftVersionInOneRelease() {
		ReleaseDefinition builtIn = read(SNAPSHOT, false).getFirst();
		String newer = SNAPSHOT.replace("1.21.11", "1.21.12").replace("protocol = 774", "protocol = 776");
		McProtocolReleaseCatalog catalog = new McProtocolReleaseCatalog(List.of(builtIn), read(newer, true), ignored -> { });

		ProtocolRelease additional = catalog.releases().getLast();
		assertTrue(additional.isAdditional());
		assertEquals(SupportLevel.UNTESTED, additional.support(MinecraftVersion.parse("1.21.12")));

		String overlapping = SNAPSHOT.replace("version = \"1.21.11-20260512.221357-18\"", "version = \"1.21.11-other\"");
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new McProtocolReleaseCatalog(List.of(builtIn), read(overlapping, true), ignored -> { }));
		assertTrue(failure.getMessage().contains("both list Minecraft 1.21.11"), failure.getMessage());
	}

	@Test
	void loadsAdditionalReleasesFromAFile() throws Exception {
		Path file = temporary.resolve("extra-releases.toml");
		Files.writeString(file, SNAPSHOT.replace("1.21.11", "1.21.12").replace("protocol = 774", "protocol = 776"));

		McProtocolReleaseCatalog catalog = McProtocolReleaseCatalog.load(file);
		assertEquals(5, catalog.releases().size());
		assertTrue(catalog.releases().stream().anyMatch(release -> release.isAdditional()
				&& release.version().equals(MinecraftVersion.parse("1.21.12"))));
		assertThrows(IllegalArgumentException.class, () -> McProtocolReleaseCatalog.load(temporary.resolve("missing.toml")));
	}

	private List<ReleaseDefinition> read(String text, boolean additional) {
		return new McProtocolReleaseReader().read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "test.toml", additional);
	}
}
