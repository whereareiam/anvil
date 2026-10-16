package me.whereareiam.anvil.protocol.mcprotocol;

import me.whereareiam.anvil.api.model.Edition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.worker.catalog.ReleaseCatalog;
import me.whereareiam.anvil.protocol.worker.model.ReleaseDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McProtocolReleasesTest {
	private static final Edition JAVA = Edition.of("java");

	private static final String SNAPSHOT = """
			[[release]]
			version = "1.21.11-20260512.221357-18"
			edition = "java"
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
		ReleaseCatalog catalog = ReleaseCatalog.load(McProtocolWorkerLibrary.LIBRARY, null);

		Map<String, Integer> protocols = Map.of(
				"1.18.2", 758, "1.21.1", 767, "1.21.11", 774, "26.1.2", 775
		);
		List<ProtocolRelease> releases = catalog.releases();
		assertEquals(List.of("1.18.2", "1.21.1", "1.21.11", "26.1.2"),
				releases.stream().map(release -> release.version().toString()).toList());
		for (ProtocolRelease release : releases) {
			assertEquals(JAVA, release.getEdition());
			for (MinecraftVersion version : release.getMinecraftVersions())
				assertEquals(protocols.get(release.version().toString()), release.protocolNumber(version));
			assertEquals(SupportLevel.VERIFIED, release.support(release.version()));
			assertTrue(release.getFeatures().contains(ProtocolFeature.ONLINE_AUTHENTICATION));
			assertFalse(release.isAdditional());

			ReleaseDefinition definition = catalog.require(JAVA, release.version());
			assertTrue(definition.getArtifacts().stream().anyMatch(artifact -> artifact.getModule().equals(definition.getModule())));
		}

		ProtocolRelease current = releases.getLast();
		assertEquals("26.1-20260708.090514-22", current.getLibraryVersion());
		assertEquals(SupportLevel.COMPATIBLE, current.support(MinecraftVersion.parse("26.1")));
		assertEquals(8, releases.getFirst().getJavaVersion());
		assertEquals(17, current.getJavaVersion());
		assertThrows(IllegalArgumentException.class, () -> catalog.require(JAVA, MinecraftVersion.parse("1.20.4")));
		assertThrows(UnsupportedOperationException.class, () -> catalog.releases().clear());
	}

	@Test
	void loadsAFileCopyingABuiltInReleaseUnchanged() throws Exception {
		String builtIn;
		try (InputStream input = McProtocolReleasesTest.class.getClassLoader().getResourceAsStream(McProtocolWorkerLibrary.LIBRARY.getReleases())) {
			builtIn = new String(input.readAllBytes(), StandardCharsets.UTF_8);
		}
		int first = builtIn.indexOf("[[release]]");
		Path file = temporary.resolve("copied-releases.toml");
		Files.writeString(file, builtIn.substring(first, builtIn.indexOf("[[release]]", first + 1)));

		ReleaseCatalog catalog = ReleaseCatalog.load(McProtocolWorkerLibrary.LIBRARY, file);

		assertEquals(ReleaseCatalog.load(McProtocolWorkerLibrary.LIBRARY, null).releases(), catalog.releases());
		assertTrue(catalog.releases().stream().noneMatch(ProtocolRelease::isAdditional));
	}

	@Test
	void loadsAdditionalReleasesFromAFile() throws Exception {
		Path file = temporary.resolve("extra-releases.toml");
		Files.writeString(file, SNAPSHOT.replace("1.21.11", "1.21.12").replace("protocol = 774", "protocol = 776"));

		ReleaseCatalog catalog = ReleaseCatalog.load(McProtocolWorkerLibrary.LIBRARY, file);
		assertEquals(5, catalog.releases().size());
		assertTrue(catalog.releases().stream().anyMatch(release -> release.isAdditional()
				&& release.version().equals(MinecraftVersion.parse("1.21.12"))));
		assertThrows(IllegalArgumentException.class, () -> ReleaseCatalog.load(McProtocolWorkerLibrary.LIBRARY, temporary.resolve("missing.toml")));
	}
}
