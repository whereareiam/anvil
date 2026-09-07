package me.whereareiam.anvil.api.scenario;

import org.jetbrains.annotations.NotNull;

/**
 * User-defined setup or reset logic executed against a running scenario.
 */
public interface ScenarioHook {
	/**
	 * Executes the hook.
	 *
	 * @param context running scenario access
	 * @throws Exception when the scenario cannot be prepared or reset
	 */
	void execute(@NotNull ScenarioAccess context) throws Exception;
}
