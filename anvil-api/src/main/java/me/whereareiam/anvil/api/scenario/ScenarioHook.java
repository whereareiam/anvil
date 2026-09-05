package me.whereareiam.anvil.api.scenario;

import me.whereareiam.anvil.api.scenario.AnvilContext;
import org.jetbrains.annotations.NotNull;

/**
 * User-defined setup or reset logic executed against a running scenario.
 */
public interface ScenarioHook {
	/**
	 * Executes the hook.
	 *
	 * @param context running Anvil context
	 * @throws Exception when the scenario cannot be prepared or reset
	 */
	void execute(@NotNull AnvilContext context) throws Exception;
}
