package me.whereareiam.anvil.integration.intellij.scenario;

import com.intellij.openapi.actionSystem.DataKey;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import me.whereareiam.anvil.integration.intellij.model.source.ScenarioSource;
import me.whereareiam.anvil.integration.intellij.scenario.environment.EnvironmentSession;
import me.whereareiam.anvil.tooling.api.model.scenario.ScenarioDescriptor;

/**
 * What a scenario view shows, offered to actions through the IDE's data context.
 * <p>
 * Views offer these keys together with {@code ScenarioSource.DATA_KEY}; the scenario actions read them, so the
 * same action works in the catalog, in an environment tab, and in any view added later.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ScenarioDataKeys {
	/**
	 * The selected scenario, or the scenario of the selected process.
	 */
	public static final DataKey<ScenarioDescriptor> SCENARIO = DataKey.create("anvil.scenario");

	/**
	 * Name of the selected process; absent when the scenario itself is selected.
	 */
	public static final DataKey<String> PROCESS = DataKey.create("anvil.process");

	/**
	 * The environment session the view controls: its own session in an environment tab, the active one elsewhere.
	 */
	public static final DataKey<EnvironmentSession> SESSION = DataKey.create("anvil.session");
}
