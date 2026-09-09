package me.whereareiam.anvil.testkit.tests.runtime.provider;

import me.whereareiam.anvil.capability.api.exception.CapabilityException;
import me.whereareiam.anvil.launcher.AnvilLauncher;
import me.whereareiam.anvil.api.model.EngineOptions;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.api.type.Platforms;
import me.whereareiam.anvil.protocol.api.provider.ProtocolProviderRegistry;
import me.whereareiam.anvil.testkit.support.FixtureArtifacts;
import me.whereareiam.anvil.testkit.support.TestExtensionLoader;
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
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), false);
		     var ignored1 = AnvilLauncher.create(options())) {
			assertEquals(List.of("fixture"), ProtocolProviderRegistry.discover().ids().stream().toList());
		}
		assertFalse(Files.exists(temporary.resolve("cache/fixture.lifecycle")));
	}

	@Test
	void rejectsAmbiguousBackendSelectionBeforeCreatingResources() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), true)) {
			var failure = assertThrows(IllegalStateException.class, () -> AnvilLauncher.create(options()));
			assertTrue(failure.getMessage().contains("Multiple protocol providers"));
			assertFalse(Files.exists(temporary.resolve("cache/fixture.lifecycle")));
		}
	}

	@Test
	void reportsMissingCapabilitiesBeforeCreatingTheBackend() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.brokenExtension(), false);
		     var engine = AnvilLauncher.create(options().toBuilder().eulaAccepted(true).build())) {
			var scenario = AnvilScenario.builder().name("missing-capability").entrypoint("server")
					.server(MinecraftServer.builder().name("server").platform(Platforms.PAPER)
							.distribution(Distribution.remote("1.21.11", "132")).build()).build();
			var failure = assertThrows(CapabilityException.class, () -> engine.start(scenario));
			assertTrue(failure.getMessage().contains("missing capabilities"));
			assertFalse(Files.exists(temporary.resolve("cache/fixture-broken.lifecycle")));
		}
	}

	@Test
	void offlineBackendHasNoAuthenticationRequirement() throws Exception {
		try (var ignored = new TestExtensionLoader(FixtureArtifacts.extension(), false)) {
			assertTrue(ProtocolProviderRegistry.discover().select(null).authentication(temporary).isEmpty());
		}
	}

	private EngineOptions options() {
		return EngineOptions.builder().cacheDirectory(temporary.resolve("cache")).build();
	}
}
