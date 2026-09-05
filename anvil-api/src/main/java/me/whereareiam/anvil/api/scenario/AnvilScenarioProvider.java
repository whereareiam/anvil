package me.whereareiam.anvil.api.scenario;

import org.jetbrains.annotations.NotNull;

/**
 * Explicit catalog provider through which applications register scenarios and groups.
 *
 * <p>Providers are intended for Gradle discovery, generated matrices, and interactive scenario
 * groups. A JUnit test that runs one statically defined scenario can select an
 * {@link AnvilScenarioDefinition} class directly.</p>
 */
public interface AnvilScenarioProvider {
	/**
	 * Registers all scenarios owned by this provider.
	 *
	 * @param registry destination registry
	 */
	void register(@NotNull ScenarioRegistry registry);
}
