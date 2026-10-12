package me.whereareiam.anvil.testkit.tests.runtime.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.dataformat.toml.TomlMapper;
import me.whereareiam.anvil.api.model.MinecraftVersion;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.protocol.api.library.ProtocolLibraryRegistry;
import me.whereareiam.anvil.protocol.api.model.ProtocolLibraryContext;
import me.whereareiam.anvil.protocol.api.model.ProtocolRelease;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps Lumi's known versions reachable by Bedrock players: each is a version the CloudburstMC Protocol release
 * names with the same spelling, and both speak it with the same protocol, the one protocol a Lumi server of that
 * version allows.
 */
class LumiVersionAlignmentIntegrationTest {
	@TempDir
	Path directory;

	@Test
	void everyKnownLumiVersionIsACloudburstVersionOfTheSameProtocol() throws IOException {
		JsonNode lumi = lumiVersionData();
		ProtocolRelease cloudburst = cloudburstRelease();

		for (JsonNode known : lumi.path("known")) {
			String version = known.asText();
			MinecraftVersion parsed = MinecraftVersion.parse(version);
			assertTrue(cloudburst.getMinecraftVersions().contains(parsed), "CloudburstMC Protocol does not name Lumi version " + version);
			assertEquals(lumi.path("lumi").path("protocol").path(version).asInt(), cloudburst.protocolNumber(parsed),
					"Protocol of " + version);
		}
	}

	private static JsonNode lumiVersionData() throws IOException {
		PlatformProvider provider = ServiceLoader.load(PlatformProvider.class).stream()
				.map(ServiceLoader.Provider::get)
				.filter(candidate -> candidate.id().equals(Platforms.LUMI))
				.findFirst()
				.orElseThrow();
		try (InputStream data = provider.versionData().openStream()) {
			return new TomlMapper().readTree(data);
		}
	}

	private ProtocolRelease cloudburstRelease() {
		ProtocolLibraryContext context = ProtocolLibraryContext.builder()
				.cacheDirectory(directory)
				.accountsDirectory(directory.resolve("accounts"))
				.artifacts((artifact, destination, sha256) -> {
					throw new AssertionError("Reading release data must not download artifacts");
				})
				.build();
		List<ProtocolRelease> releases = ProtocolLibraryRegistry.discover().require("cloudburst").releases(context);
		assertEquals(1, releases.size(), "CloudburstMC Protocol releases");

		return releases.getFirst();
	}
}
