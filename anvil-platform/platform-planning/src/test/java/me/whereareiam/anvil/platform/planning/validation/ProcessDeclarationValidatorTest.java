package me.whereareiam.anvil.platform.planning.validation;

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

class ProcessDeclarationValidatorTest {
	private final ProcessDeclarationValidator validator = new ProcessDeclarationValidator();
	private final Map<String, PlatformProvider> providers = Map.of(
			"server", new StubProvider("server", MinecraftServer.class),
			"proxy", new StubProvider("proxy", MinecraftProxy.class)
	);

	@Test
	void acceptsPinnedDirectScenario() {
		assertDoesNotThrow(() -> validator.validate(direct("1"), providers));
	}

	@Test
	void delegatesChecksumSelectionValidationToTheOwningProvider() {
		var server = server("server", "1").toBuilder()
				.distribution(Distribution.pinned("1.21.11", "a".repeat(64))).build();
		var scenario = direct("1").toBuilder().clearServers().server(server).build();
		assertThrows(ScenarioValidationException.class, () -> validator.validate(scenario, providers));
		AtomicBoolean validated = new AtomicBoolean();
		PlatformProvider supplier = new StubProvider("server", MinecraftServer.class) {
			@Override
			public void validateDistribution(@NotNull MinecraftProcess process) {
				validated.set(true);
			}
		};
		assertDoesNotThrow(() -> validator.validate(scenario, Map.of("server", supplier)));
		assertTrue(validated.get());
	}

	@Test
	void rejectsPlatformConfigurationMismatch() {
		MinecraftServer server = server("server", "1").toBuilder().platform("proxy").build();
		AnvilScenario scenario = AnvilScenario.builder()
				.name("mismatch")
				.entrypoint(server.getName())
				.server(server)
				.build();

		assertThrows(ScenarioValidationException.class, () -> validator.validate(scenario, providers));
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

		assertThrows(ScenarioValidationException.class, () -> validator.validate(scenario, providers));
		assertDoesNotThrow(() -> validator.validate(
				scenario.toBuilder()
						.clearServers()
						.server(local.toBuilder().minecraftVersion("1.21.11").build())
						.build(),
				providers
		));

		MinecraftServer artifact = local.toBuilder()
				.distribution(Distribution.artifact("custom-server"))
				.minecraftVersion(null)
				.build();
		assertThrows(ScenarioValidationException.class, () -> validator.validate(
				scenario.toBuilder().clearServers().server(artifact).build(),
				providers
		));
	}

	@Test
	void rejectsMovingBuildsForAutomatedScenarios() {
		assertThrows(ScenarioValidationException.class, () -> validator.validate(direct("latest"), providers));
		assertDoesNotThrow(() -> validator.validate(direct("latest").toBuilder().manual(true).build(), providers));
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
