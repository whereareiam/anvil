package me.whereareiam.anvil.capability.dialogs.model.click;

import lombok.Value;
import me.whereareiam.anvil.capability.dialogs.model.dialog.DialogAction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the client sends when a dialog button is pressed: a command, or the custom click of a custom action. A custom
 * click names the pressed action by the handle {@link DialogAction#getHandle()} returns; the player's worker sends
 * the ID and payload the action declared, with the values put in.
 */
@Value
public class DialogClick {
	/**
	 * Command to run without its leading slash, or {@code null} for a custom click.
	 */
	@Nullable String command;
	/**
	 * Handle of the pressed custom action, or {@code null} for a command.
	 */
	@Nullable Long action;
	/**
	 * Input values a dynamic custom click puts into its payload, each replacing an entry with the same key; empty for
	 * a command or a static custom click.
	 */
	@NotNull List<DialogValue> values;

	/**
	 * Creates a click that runs a command.
	 *
	 * @param commandWithoutSlash command and arguments without the leading slash
	 * @return command click
	 */
	public static @NotNull DialogClick command(@NotNull String commandWithoutSlash) {
		return new DialogClick(commandWithoutSlash, null, List.of());
	}

	/**
	 * Creates the custom click of a custom action.
	 *
	 * @param action the pressed action's handle
	 * @param values input values put into the payload, empty for a static custom action
	 * @return custom click
	 */
	public static @NotNull DialogClick custom(long action, @NotNull List<DialogValue> values) {
		return new DialogClick(null, action, List.copyOf(values));
	}
}
