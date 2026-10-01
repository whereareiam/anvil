package me.whereareiam.anvil.tooling.builtin;

import me.whereareiam.anvil.api.player.SimulatedPlayer;
import me.whereareiam.anvil.capability.messages.Messages;
import me.whereareiam.anvil.capability.session.Session;
import me.whereareiam.anvil.tooling.api.model.action.binding.ActionAvailability;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionDefinition;
import me.whereareiam.anvil.tooling.api.model.action.definition.ActionInput;
import me.whereareiam.anvil.tooling.api.model.action.invocation.ActionResult;
import me.whereareiam.anvil.tooling.api.type.action.ActionInputType;
import me.whereareiam.anvil.tooling.extension.api.action.scoped.player.PlayerCapabilityAction;
import me.whereareiam.anvil.tooling.extension.api.model.ToolingArguments;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.function.BiConsumer;

/**
 * Declares and validates one line submitted through the player's Messages capability.
 */
final class PlayerMessageAction extends PlayerCapabilityAction<Messages> {
	private final BiConsumer<Messages, String> operation;

	PlayerMessageAction(
			String id,
			String label,
			String inputLabel,
			BiConsumer<Messages, String> operation
	) {
		super(Messages.class, ActionDefinition.builder().id(id).displayName(label)
				.inputs(List.of(ActionInput.builder().name("text").displayName(inputLabel).type(ActionInputType.TEXT).build()))
				.build());
		this.operation = operation;
	}

	@Override
	public @NotNull ActionAvailability availability(@NotNull SimulatedPlayer player, @NotNull Messages messages) {
		boolean connected = !player.hasCapability(Session.class) || player.capability(Session.class).state().connected();
		return ActionAvailability.builder().enabled(connected).reason(connected ? null : "Player is disconnected").build();
	}

	@Override
	public @NotNull ActionResult execute(
			@NotNull SimulatedPlayer player,
			@NotNull Messages messages,
			@NotNull ToolingArguments arguments
	) {
		String text = arguments.text("text");
		if (text.isBlank() || text.indexOf('\n') >= 0 || text.indexOf('\r') >= 0)
			throw new IllegalArgumentException("Supply one non-blank line");

		operation.accept(messages, text);
		return ActionResult.builder().message("Submitted").build();
	}
}
