package me.whereareiam.anvil.capability.dialogs.model.click;

import lombok.Value;
import me.whereareiam.anvil.capability.commands.type.CommandCheck;
import org.jetbrains.annotations.NotNull;

/**
 * The screen on which the client asks the player whether to run a command a dialog button runs.
 */
@Value
public class CommandConfirmation {
	/**
	 * The command without its leading slash.
	 */
	@NotNull String command;
	/**
	 * Why the client asks: {@link CommandCheck#PARSE_ERRORS}, {@link CommandCheck#PERMISSIONS_REQUIRED} or
	 * {@link CommandCheck#SIGNATURE_REQUIRED}.
	 */
	@NotNull CommandCheck reason;
}
