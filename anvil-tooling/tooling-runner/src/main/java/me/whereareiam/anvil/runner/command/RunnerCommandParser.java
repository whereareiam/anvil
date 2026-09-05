package me.whereareiam.anvil.runner.command;

import me.whereareiam.anvil.runner.model.command.RunnerCommand;
import me.whereareiam.anvil.runner.type.RunnerCommandType;
import org.jetbrains.annotations.NotNull;

/**
 * Parses one line from the interactive foreground session.
 */
public final class RunnerCommandParser {
	/**
	 * Parses a command line while preserving the complete command argument for {@code send}.
	 *
	 * @param line raw input line
	 * @return parsed command
	 */
	public static @NotNull RunnerCommand parse(@NotNull String line) {
		if (line.isBlank()) throw new IllegalArgumentException("Command line must not be blank");

		String[] parts = line.trim().split("\\s+", 3);
		RunnerCommand.RunnerCommandBuilder command = RunnerCommand.builder()
				.type(RunnerCommandType.fromName(parts[0]))
				.token(parts[0]);

		if (parts.length > 1) command.argument(parts[1]);
		if (parts.length > 2) command.argument(parts[2]);

		return command.build();
	}
}
