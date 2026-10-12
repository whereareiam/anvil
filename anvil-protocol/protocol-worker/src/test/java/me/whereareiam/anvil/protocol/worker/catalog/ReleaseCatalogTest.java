package me.whereareiam.anvil.protocol.worker.catalog;

import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.worker.fixture.ProbeLibrary;
import me.whereareiam.anvil.protocol.worker.model.ReleaseDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReleaseCatalogTest {
	private static final String SNAPSHOT = """
			[[release]]
			version = "1.21.11-20260512.221357-18"
			module = "example.probe:protocol:1.21.11-20260512.221357-18"
			protocol = 774
			minecraft = ["1.21.11"]
			verified = ["1.21.11"]
			java = 17
			features = ["ONLINE_AUTHENTICATION"]

			[[release.artifact]]
			module = "example.probe:protocol:1.21.11-20260512.221357-18"
			url = "https://repository.example/example/probe/protocol/1.21.11-SNAPSHOT/protocol-1.21.11-20260512.221357-18.jar"
			sha256 = "00d9ae3464dac8dcfe861303d728cb3361a9794d8ad0455aa340229ee83ee709"
			""";

	@TempDir
	Path temporary;

	@Test
	void loadsTheBuiltInReleaseDataOfTheLibrary() {
		ReleaseCatalog catalog = ReleaseCatalog.load(ProbeLibrary.LIBRARY, null);

		List<ProtocolRelease> releases = catalog.releases();
		assertEquals(List.of("1.20.4", "1.21.11"), releases.stream().map(release -> release.version().toString()).toList());
		assertTrue(releases.stream().noneMatch(ProtocolRelease::isAdditional));
		assertEquals("ProbeLib release 1.20.4-1 has no pinned checksum for example.probe:protocol:1.20.4-1; run `./gradlew pinProbeReleases`",
				releases.getFirst().getLaunchRefusal());
		IllegalArgumentException missing = assertThrows(IllegalArgumentException.class, () -> catalog.require(MinecraftVersion.parse("1.19.4")));
		assertTrue(missing.getMessage().startsWith("No ProbeLib release speaks Minecraft 1.19.4"), missing.getMessage());
		assertThrows(UnsupportedOperationException.class, () -> catalog.releases().clear());
	}

	@Test
	void ignoresAnIdenticalAdditionalReleaseWithAnInfoLine() {
		List<String> notices = new ArrayList<>();
		ReleaseDefinition builtIn = read(SNAPSHOT, false).getFirst();
		ReleaseCatalog catalog = new ReleaseCatalog(ProbeLibrary.LIBRARY, List.of(builtIn), read(SNAPSHOT, true), notices::add);

		assertEquals(1, notices.size());
		assertTrue(notices.getFirst().startsWith("[Anvil] Info: additional ProbeLib release 1.21.11-20260512.221357-18"));
		assertSame(builtIn, catalog.require(builtIn.getRelease().version()));
		assertFalse(catalog.releases().getFirst().isAdditional());
	}

	@Test
	void ignoresAnIdenticalUnpinnedAdditionalReleaseAlthoughItsLaunchRefusalNamesAnotherRemedy() {
		List<String> notices = new ArrayList<>();
		String unpinned = SNAPSHOT.replace("00d9ae3464dac8dcfe861303d728cb3361a9794d8ad0455aa340229ee83ee709", "");
		ReleaseDefinition builtIn = read(unpinned, false).getFirst();
		List<ReleaseDefinition> copy = read(unpinned, true);
		assertNotEquals(builtIn.getRelease().getLaunchRefusal(), copy.getFirst().getRelease().getLaunchRefusal());

		ReleaseCatalog catalog = new ReleaseCatalog(ProbeLibrary.LIBRARY, List.of(builtIn), copy, notices::add);

		assertEquals(1, notices.size(), notices.toString());
		assertSame(builtIn, catalog.require(builtIn.getRelease().version()));
		assertTrue(catalog.releases().getFirst().getLaunchRefusal().endsWith("pinProbeReleases`"),
				catalog.releases().getFirst().getLaunchRefusal());
	}

	@Test
	void refusesAConflictingAdditionalReleaseOfTheSameVersion() {
		ReleaseDefinition builtIn = read(SNAPSHOT, false).getFirst();
		List<ReleaseDefinition> conflicting = read(SNAPSHOT.replace("protocol = 774", "protocol = 773"), true);

		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new ReleaseCatalog(ProbeLibrary.LIBRARY, List.of(builtIn), conflicting, ignored -> { }));
		assertTrue(failure.getMessage().contains("Additional ProbeLib release"), failure.getMessage());
		assertTrue(failure.getMessage().contains("conflicts with the built-in release"), failure.getMessage());
	}

	@Test
	void marksNewAdditionalReleasesUntestedAndKeepsEachMinecraftVersionInOneRelease() {
		ReleaseDefinition builtIn = read(SNAPSHOT, false).getFirst();
		String newer = SNAPSHOT.replace("1.21.11", "1.21.12").replace("protocol = 774", "protocol = 776");
		ReleaseCatalog catalog = new ReleaseCatalog(ProbeLibrary.LIBRARY, List.of(builtIn), read(newer, true), ignored -> { });

		ProtocolRelease additional = catalog.releases().getLast();
		assertTrue(additional.isAdditional());
		assertEquals(SupportLevel.UNTESTED, additional.support(MinecraftVersion.parse("1.21.12")));

		String overlapping = SNAPSHOT.replace("version = \"1.21.11-20260512.221357-18\"", "version = \"1.21.11-other\"");
		IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
				() -> new ReleaseCatalog(ProbeLibrary.LIBRARY, List.of(builtIn), read(overlapping, true), ignored -> { }));
		assertTrue(failure.getMessage().contains("both list Minecraft 1.21.11"), failure.getMessage());
	}

	@Test
	void loadsAdditionalReleasesFromAFile() throws Exception {
		Path file = temporary.resolve("extra-releases.toml");
		Files.writeString(file, SNAPSHOT.replace("1.21.11", "1.21.12").replace("protocol = 774", "protocol = 776"));

		ReleaseCatalog catalog = ReleaseCatalog.load(ProbeLibrary.LIBRARY, file);
		assertEquals(3, catalog.releases().size());
		assertTrue(catalog.releases().stream().anyMatch(release -> release.isAdditional()
				&& release.version().equals(MinecraftVersion.parse("1.21.12"))));
		IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
				() -> ReleaseCatalog.load(ProbeLibrary.LIBRARY, temporary.resolve("missing.toml")));
		assertTrue(missing.getMessage().startsWith("Additional ProbeLib release file does not exist"), missing.getMessage());
	}

	private List<ReleaseDefinition> read(String text, boolean additional) {
		return new ReleaseReader(ProbeLibrary.LIBRARY).read(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)), "test.toml", additional);
	}
}
