package me.whereareiam.anvil.protocol.cloudburst;

import me.whereareiam.anvil.api.model.MinecraftEdition;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.SupportLevel;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import me.whereareiam.anvil.protocol.api.type.ProtocolFeature;
import me.whereareiam.anvil.protocol.worker.catalog.ReleaseCatalog;
import me.whereareiam.anvil.protocol.worker.model.ReleaseDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudburstReleasesTest {
	private static final MinecraftEdition BEDROCK = MinecraftEdition.of("bedrock");

	@Test
	void loadsOneBedrockReleaseThatMapsEachVersionToItsOwnProtocol() {
		ReleaseCatalog catalog = ReleaseCatalog.load(CloudburstWorkerLibrary.LIBRARY, null);

		List<ProtocolRelease> releases = catalog.releases();
		assertEquals(1, releases.size());
		ProtocolRelease release = releases.getFirst();
		assertEquals("3.0.0.Beta14-20261009.172637-6", release.getLibraryVersion());
		assertEquals(BEDROCK, release.getEdition());
		assertEquals(MinecraftVersion.parse("1.26.60"), release.version());
		assertEquals(32, release.getMinecraftVersions().size());
		assertEquals(589, release.protocolNumber(MinecraftVersion.parse("1.20.0")));
		assertEquals(898, release.protocolNumber(MinecraftVersion.parse("1.21.130")));
		assertEquals(2169, release.protocolNumber(MinecraftVersion.parse("1.26.45")));
		assertEquals(2225, release.protocolNumber(release.version()));
		assertEquals(SupportLevel.COMPATIBLE, release.support(MinecraftVersion.parse("1.21.50")));
		assertTrue(release.getFeatures().contains(ProtocolFeature.ONLINE_AUTHENTICATION));
		assertTrue(release.isLaunchable(), release.getLaunchRefusal());
		assertFalse(release.isAdditional());

		ReleaseDefinition definition = catalog.require(BEDROCK, MinecraftVersion.parse("1.21.50"));
		assertTrue(definition.getArtifacts().stream().anyMatch(artifact -> artifact.getModule().equals(definition.getModule())));
	}

	@Test
	void servesNoJavaServerThatNumbersAVersionAlike() {
		ReleaseCatalog catalog = ReleaseCatalog.load(CloudburstWorkerLibrary.LIBRARY, null);

		assertThrows(IllegalArgumentException.class, () -> catalog.require(MinecraftEdition.of("java"), MinecraftVersion.parse("1.21.0")));
	}
}
