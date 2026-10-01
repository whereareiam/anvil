package me.whereareiam.anvil.tooling.extension.api.action.scoped.process;

import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.extension.api.action.ToolingAction;
import org.jetbrains.annotations.NotNull;

/**
 * An action evaluated against a current process target.
 */
public abstract class ProcessAction extends ToolingAction<RunningProcess> {
	/**
	 * Declares this process contribution.
	 *
	 * @param definition portable identity and presentation
	 */
	protected ProcessAction(@NotNull ActionDefinition definition) {
		super(definition);
	}
}
