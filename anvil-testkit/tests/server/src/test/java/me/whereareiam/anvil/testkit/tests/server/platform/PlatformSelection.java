package me.whereareiam.anvil.testkit.tests.server.platform;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.api.model.scenario.AnvilScenario;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The platforms the platform suite runs this time: the {@code anvil.platforms} system property, a comma-separated
 * list of platform IDs such as {@code velocity}, which {@code -PanvilPlatforms} sets, or every platform when it is
 * empty. A scenario belongs to the platform of its entrypoint: its proxy, or its only server.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class PlatformSelection {
	private static final String PROPERTY = "anvil.platforms";

	/**
	 * Keeps the scenarios of the selected platforms.
	 *
	 * @param scenarios candidate scenarios
	 * @return the selected scenarios, in order
	 */
	static @NotNull List<AnvilScenario> select(@NotNull List<AnvilScenario> scenarios) {
		Set<String> selected = Arrays.stream(System.getProperty(PROPERTY, "").split(","))
				.map(String::strip)
				.filter(platform -> !platform.isEmpty())
				.collect(Collectors.toUnmodifiableSet());
		if (selected.isEmpty()) return scenarios;

		return scenarios.stream().filter(scenario -> selected.contains(entrypointPlatform(scenario))).toList();
	}

	private static String entrypointPlatform(AnvilScenario scenario) {
		String entrypoint = scenario.getEntrypoint();
		return scenario.getProxies().stream()
				.filter(proxy -> proxy.getName().equals(entrypoint))
				.map(proxy -> proxy.getPlatform())
				.findFirst()
				.orElseGet(() -> scenario.getServers().stream()
						.filter(server -> server.getName().equals(entrypoint))
						.map(server -> server.getPlatform())
						.findFirst()
						.orElseThrow(() -> new IllegalStateException("Scenario " + scenario.getName()
								+ " has no entrypoint " + entrypoint)));
	}
}
