package external.tooling.action;

import external.tooling.Counter;
import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionInput;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.player.PlayerCapabilityAction;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Increments an external player capability through its typed tooling action.
 */
public final class IncrementCounterAction extends PlayerCapabilityAction<Counter> {
	/**
	 * Declares the contribution without acquiring runtime resources.
	 */
	public IncrementCounterAction() {
		super(Counter.class, ActionDefinition.builder().id("external.counter.add").displayName("Increment counter")
				.inputs(List.of(ActionInput.builder().name("amount").displayName("Amount").type(ActionInputType.INTEGER).defaultValue("1").build())).build());
	}

	@Override
	public @NotNull ActionResult execute(
			@NotNull SimulatedPlayer player,
			@NotNull Counter counter,
			@NotNull ToolingArguments arguments
	) {
		return ActionResult.builder().columns(List.of("Player", "Value"))
				.rows(List.of(List.of(player.name(), Long.toString(counter.add(arguments.integer("amount")))))).build();
	}
}
