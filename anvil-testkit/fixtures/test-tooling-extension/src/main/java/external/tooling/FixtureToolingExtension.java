package external.tooling;

import external.tooling.action.IncrementCounterAction;
import external.tooling.action.InspectProcessAction;
import external.tooling.action.InspectScenarioAction;
import external.tooling.observation.CounterValueObservation;
import external.tooling.observation.PlayerCountObservation;
import me.whereareiam.anvil.tooling.extension.api.ToolingExtension;
import me.whereareiam.anvil.tooling.extension.api.ToolingRegistration;
import org.jetbrains.annotations.NotNull;

/**
 * Contributes typed controls at every scope using the public tooling extension contract.
 */
public final class FixtureToolingExtension implements ToolingExtension {
	@Override
	public void register(@NotNull ToolingRegistration registration) {
		registration.action(new IncrementCounterAction());
		registration.action(new InspectProcessAction());
		registration.action(new InspectScenarioAction());
		registration.observation(new CounterValueObservation());
		registration.observation(new PlayerCountObservation());
	}
}
