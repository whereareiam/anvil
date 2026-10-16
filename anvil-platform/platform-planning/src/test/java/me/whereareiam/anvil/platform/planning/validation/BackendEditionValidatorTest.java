package me.whereareiam.anvil.platform.planning.validation;

import me.whereareiam.anvil.api.exception.scenario.ScenarioValidationException;
import me.whereareiam.anvil.api.model.process.Distribution;
import me.whereareiam.anvil.api.model.process.MinecraftProcess;
import me.whereareiam.anvil.api.model.process.MinecraftProxy;
import me.whereareiam.anvil.api.model.process.MinecraftServer;
import me.whereareiam.anvil.platform.api.PlatformProvider;
import me.whereareiam.anvil.platform.api.model.PlatformContext;
import me.whereareiam.anvil.platform.api.model.ResolvedDistribution;
import me.whereareiam.anvil.platform.planning.version.ProviderVersions;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.Test;

import java.net.URL;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BackendEditionValidatorTest {
	private final BackendEditionValidator validator = new BackendEditionValidator(new ProviderVersions());
	private final Map<String, PlatformProvider> providers = Map.of(
			"first", new StubProvider("first", "test-versions.toml"),
			"same", new StubProvider("same", "paper-versions.toml"),
			"other", new StubProvider("other", "other-edition-versions.toml"),
			"proxy", new StubProvider("proxy", "other-edition-versions.toml")
	);

	@Test
	void acceptsAProxyWhoseServersSpeakOneEditionWhateverTheProxySpeaks() {
		assertDoesNotThrow(() -> validator.validate(processes("first", "same"), providers));
	}

	@Test
	void refusesAProxyWhoseServersSpeakDifferentEditions() {
		ScenarioValidationException refused = assertThrows(ScenarioValidationException.class,
				() -> validator.validate(processes("first", "other"), providers));

		assertEquals("Proxy 'proxy' has servers of different editions: 'lobby' (platform 'first') speaks java and "
				+ "'game' (platform 'other') speaks other; a player reaches every server of a proxy with one client, "
				+ "so they must speak one edition", refused.getMessage());
	}

	@Test
	void ignoresServersThatNoProxyReaches() {
		Map<String, MinecraftProcess> processes = new LinkedHashMap<>();
		processes.put("lobby", server("lobby", "first"));
		processes.put("game", server("game", "other"));

		assertDoesNotThrow(() -> validator.validate(processes, providers));
	}

	private static Map<String, MinecraftProcess> processes(String lobbyPlatform, String gamePlatform) {
		Map<String, MinecraftProcess> processes = new LinkedHashMap<>();
		processes.put("lobby", server("lobby", lobbyPlatform));
		processes.put("game", server("game", gamePlatform));
		processes.put("proxy", MinecraftProxy.builder()
				.name("proxy")
				.platform("proxy")
				.distribution(Distribution.remote("1.21.11", "1"))
				.server("lobby")
				.server("game")
				.defaultServer("lobby")
				.build());
		return processes;
	}

	private static MinecraftServer server(String name, String platform) {
		return MinecraftServer.builder()
				.name(name)
				.platform(platform)
				.distribution(Distribution.remote("1.21.11", "1"))
				.build();
	}

	private record StubProvider(String id, String versions) implements PlatformProvider {
		@Override
		public @NotNull Class<? extends MinecraftProcess> configurationType() {
			return MinecraftProcess.class;
		}

		@Override
		public @NotNull ResolvedDistribution resolve(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			throw new UnsupportedOperationException();
		}

		@Override
		public void configure(@NotNull MinecraftProcess process, @NotNull PlatformContext context) {
			throw new UnsupportedOperationException();
		}

		@Override
		public @NotNull Pattern readinessPattern() {
			return Pattern.compile("ready");
		}

		@Override
		public @NotNull URL versionData() {
			return BackendEditionValidatorTest.class.getResource("/me/whereareiam/anvil/platform/planning/" + versions);
		}
	}
}
