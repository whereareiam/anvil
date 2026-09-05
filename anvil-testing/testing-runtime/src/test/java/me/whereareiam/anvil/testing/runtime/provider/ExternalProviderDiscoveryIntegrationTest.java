package me.whereareiam.anvil.testing.runtime.provider;

import me.whereareiam.anvil.capability.api.CapabilityException;
import me.whereareiam.anvil.engine.AnvilEngine;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry;
import me.whereareiam.anvil.testing.fixture.extension.ExternalExtensionFixture;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies external provider selection and capability validation without launching Minecraft.
 */
class ExternalProviderDiscoveryIntegrationTest {
	@TempDir
	Path temporary;

	@Test
	void selectsTheSoleBackendBeforeFilteringInstalledDefaultCapabilities() throws Exception {
		try (var fixture = new ExternalExtensionFixture(temporary, "FixtureProtocolProvider", false);
			 var engine = new AnvilEngine(options())) {
			assertEquals(List.of("fixture"), ProtocolProviderRegistry.discover().ids().stream().toList());
		}
		assertFalse(Files.exists(temporary.resolve("cache/fixture.lifecycle")));
	}

	@Test
	void rejectsAmbiguousBackendSelectionBeforeCreatingResources() throws Exception {
		try (var fixture = new ExternalExtensionFixture(temporary, "FixtureProtocolProvider", true)) {
			var failure = assertThrows(IllegalStateException.class, () -> new AnvilEngine(options()));
			assertTrue(failure.getMessage().contains("Multiple protocol providers"));
			assertFalse(Files.exists(temporary.resolve("cache/fixture.lifecycle")));
		}
	}

	@Test
	void reportsMissingCapabilitiesBeforeCreatingTheBackend() throws Exception {
		try (var fixture = new ExternalExtensionFixture(temporary, "BrokenProtocolProvider", false)) {
			var failure = assertThrows(CapabilityException.class, () -> new AnvilEngine(options()));
			assertTrue(failure.getMessage().contains("missing capabilities"));
			assertFalse(Files.exists(temporary.resolve("cache/fixture-broken.lifecycle")));
		}
	}

	@Test
	void offlineBackendHasNoAuthenticationRequirement() throws Exception {
		try (var fixture = new ExternalExtensionFixture(temporary, "FixtureProtocolProvider", false)) {
			assertTrue(ProtocolProviderRegistry.discover().select(null).authentication(temporary).isEmpty());
		}
	}

	private EngineOptions options() {
		return EngineOptions.builder().cacheDirectory(temporary.resolve("cache")).build();
	}
}
