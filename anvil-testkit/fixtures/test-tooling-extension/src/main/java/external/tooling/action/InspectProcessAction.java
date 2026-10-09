package external.tooling.action;

import me.whereareiam.anvil.api.process.RunningProcess;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.process.ProcessAction;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import org.jetbrains.annotations.NotNull;

/**
 * Reports the selected process identity and current state.
 */
public final class InspectProcessAction extends ProcessAction {
	/**
	 * Declares the contribution without acquiring runtime resources.
	 */
	public InspectProcessAction() {
		super(ActionDefinition.builder().id("external.process.inspect").displayName("Inspect process").build());
	}

	@Override
	public @NotNull ActionResult execute(
			@NotNull RunningProcess process,
			@NotNull ToolingArguments arguments
	) {
		return ActionResult.builder().message(process.name() + ":" + process.state()).build();
	}
}
