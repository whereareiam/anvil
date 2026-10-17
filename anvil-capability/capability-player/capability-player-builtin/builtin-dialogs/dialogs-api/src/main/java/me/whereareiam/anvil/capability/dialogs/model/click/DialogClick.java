package me.whereareiam.anvil.capability.dialogs.model.click;

import lombok.Value;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * What the client sends when a dialog button is pressed: a command, or a custom click.
 */
@Value
public class DialogClick {
	/**
	 * Command to run without its leading slash, or {@code null} for a custom click.
	 */
	@Nullable String command;
	/**
	 * ID of a custom click, or {@code null} for a command.
	 */
	@Nullable String customId;
	/**
	 * SNBT compound the custom click's payload starts from, or {@code null} for none.
	 */
	@Nullable String payload;
	/**
	 * Input values a dynamic custom click puts into its payload, each replacing an entry with the same key.
	 */
	@NotNull List<DialogValue> values;

	/**
	 * Creates a click that runs a command.
	 *
	 * @param commandWithoutSlash command and arguments without the leading slash
	 * @return command click
	 */
	public static @NotNull DialogClick command(@NotNull String commandWithoutSlash) {
		return new DialogClick(commandWithoutSlash, null, null, List.of());
	}

	/**
	 * Creates a custom click.
	 *
	 * @param id custom click ID, such as {@code example:submit}
	 * @param payload SNBT compound the payload starts from, or {@code null} for none
	 * @param values input values put into the payload
	 * @return custom click
	 */
	public static @NotNull DialogClick custom(@NotNull String id, @Nullable String payload, @NotNull List<DialogValue> values) {
		return new DialogClick(null, id, payload, List.copyOf(values));
	}
}
