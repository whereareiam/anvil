package me.whereareiam.anvil.protocol.api.model.text.click;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Runs a command, or sends chat, as the player.
 *
 * <pre>{@code
 * assertEquals(new RunCommandClick("/arbor help"), span.getStyle().getClick());
 * }</pre>
 */
@Value
public class RunCommandClick implements TextClick {
	/**
	 * The command with its slash, or the chat message.
	 */
	@NotNull String command;
}
