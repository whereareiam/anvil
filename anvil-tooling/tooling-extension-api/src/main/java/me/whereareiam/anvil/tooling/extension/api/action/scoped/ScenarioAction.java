package me.whereareiam.anvil.tooling.extension.api.action.scoped;

import me.whereareiam.anvil.api.scenario.ScenarioAccess;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.extension.api.action.ToolingAction;
import org.jetbrains.annotations.NotNull;

/**
 * An action evaluated against a current scenario target.
 */
public abstract class ScenarioAction extends ToolingAction<ScenarioAccess> {
	/**
	 * Declares this scenario contribution.
	 *
	 * @param definition portable identity and presentation
	 */
	protected ScenarioAction(@NotNull ActionDefinition definition) {
		super(definition);
	}
}
