package external.tooling.action;

import me.whereareiam.anvil.api.scenario.ScenarioAccess;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.ScenarioAction;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import org.jetbrains.annotations.NotNull;

/**
 * Reports the borrowed scenario identity.
 */
public final class InspectScenarioAction extends ScenarioAction {
	/**
	 * Declares the contribution without acquiring runtime resources.
	 */
	public InspectScenarioAction() {
		super(ActionDefinition.builder().id("external.scenario.inspect").displayName("Inspect scenario").build());
	}

	@Override
	public @NotNull ActionResult execute(
			@NotNull ScenarioAccess scenario,
			@NotNull ToolingArguments arguments
	) {
		return ActionResult.builder().message(scenario.definition().getName()).build();
	}
}
