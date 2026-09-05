package me.whereareiam.anvil.engine.scenario.preflight;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScenarioPreflightTest {
	private final ScenarioPreflight validator = new ScenarioPreflight();
	private final Map<String, PlatformProvider> providers = Map.of(
			"server", new StubProvider("server", MinecraftServer.class),
			"proxy", new StubProvider("proxy", MinecraftProxy.class)
	);

	@Test
	void acceptsPinnedDirectScenario() {
		assertDoesNotThrow(() -> validator.plan(direct("1"), true, providers));
	}

	@Test
	void delegatesChecksumSelectionValidationToTheOwningProvider() {
		var server = server("server", "1").toBuilder()
				.distribution(Distribution.pinned("1.21.11", "a".repeat(64))).build();
		var scenario = direct("1").toBuilder().clearServers().server(server).build();
		assertThrows(ScenarioValidationException.class, () -> validator.plan(scenario, true, providers));
		AtomicBoolean validated = new AtomicBoolean();
		PlatformProvider supplier = new StubProvider("server", MinecraftServer.class) {
			@Override
			public void validateDistribution(@NotNull MinecraftProcess process) {
				validated.set(true);
			}
		};
		assertDoesNotThrow(() -> validator.plan(scenario, true, Map.of("server", supplier)));
		assertTrue(validated.get());
	}

	@Test
	void rejectsMissingEulaAndMovingBuildsForAutomatedScenarios() {
		assertThrows(ScenarioValidationException.class, () -> validator.plan(direct("1"), false, providers));
		assertThrows(ScenarioValidationException.class, () -> validator.plan(direct("latest"), true, providers));
		assertDoesNotThrow(() -> validator.plan(
				direct("latest").toBuilder().manual(true).build(),
				true,
				providers
		));
	}

	@Test
	void rejectsPlatformConfigurationMismatch() {
		MinecraftServer server = server("server", "1").toBuilder().platform("proxy").build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("mismatch")
				.entrypoint(server.getName())
				.server(server)
				.build();

		assertThrows(ScenarioValidationException.class, () -> validator.plan(scenario, true, providers));
	}

	@Test
	void validatesProxyServersAndDefaultServer() {
		MinecraftServer server = server("server", "1");
		MinecraftProxy proxy = MinecraftProxy.builder()
				.name("proxy")
				.platform("proxy")
				.distribution(Distribution.remote("proxy", "1"))
				.server(server.getName())
				.defaultServer("missing")
				.build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("proxy")
				.entrypoint(proxy.getName())
				.server(server)
				.proxy(proxy)
				.build();

		assertThrows(ScenarioValidationException.class, () -> validator.plan(scenario, true, providers));
		assertDoesNotThrow(() -> validator.plan(
				scenario.toBuilder()
						.clearProxies()
						.proxy(proxy.toBuilder().defaultServer(server.getName()).build())
						.build(),
				true,
				providers
		));
	}

	@Test
	void rejectsLanBindingWithoutManualOptIn() {
		AnvilScenario lan = direct("1").toBuilder().bindAddress("0.0.0.0").build();

		assertThrows(ScenarioValidationException.class, () -> validator.plan(lan, true, providers));
		assertDoesNotThrow(() -> validator.plan(
				lan.toBuilder().manual(true).allowLanBinding(true).build(),
				true,
				providers
		));
	}

	@Test
	void requiresNativeVersionForLocalAndArtifactServers() {
		MinecraftServer local = server("server", "1").toBuilder()
				.distribution(Distribution.local(Path.of("server.jar")))
				.minecraftVersion(null)
				.build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("local")
				.entrypoint(local.getName())
				.server(local)
				.build();

		assertThrows(ScenarioValidationException.class, () -> validator.plan(scenario, true, providers));
		assertDoesNotThrow(() -> validator.plan(
				scenario.toBuilder()
						.clearServers()
						.server(local.toBuilder().minecraftVersion("1.21.11").build())
						.build(),
				true,
				providers
		));

		MinecraftServer artifact = local.toBuilder()
				.distribution(Distribution.artifact("custom-server"))
				.minecraftVersion(null)
				.build();
		assertThrows(ScenarioValidationException.class, () -> validator.plan(
				scenario.toBuilder().clearServers().server(artifact).build(),
				true,
				providers
		));
	}

	private AnvilScenario direct(String build) {
		MinecraftServer server = server("server", build);
		return AnvilScenario.builder()
				.name("direct")
				.entrypoint(server.getName())
				.server(server)
				.build();
	}

	private MinecraftServer server(String name, String build) {
		return MinecraftServer.builder()
				.name(name)
				.platform("server")
				.distribution(Distribution.remote("1.21.11", build))
				.build();
	}

	private static class StubProvider implements PlatformProvider {
		private final String id;
		private final Class<? extends MinecraftProcess> type;

		private StubProvider(String id, Class<? extends MinecraftProcess> type) {
			this.id = id;
			this.type = type;
		}

		@Override
		public @NotNull String id() {
			return id;
		}

		@Override
		public @NotNull Class<? extends MinecraftProcess> configurationType() {
			return type;
		}

		@Override
		public @NotNull ResolvedDistribution resolve(MinecraftProcess process, PlatformContext context) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void configure(MinecraftProcess process, PlatformContext context) {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull Pattern readinessPattern() {
			return Pattern.compile("ready");
		}

		@Override
		public int minimumJavaVersion(MinecraftProcess process) {
			return 21;
		}
	}
}
