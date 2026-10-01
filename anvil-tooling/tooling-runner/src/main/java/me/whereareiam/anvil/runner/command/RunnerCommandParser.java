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

		String token = line.trim().split("\\s+", 2)[0];
		String[] parts = line.trim().split("\\s+", RunnerCommandType.fromName(token) == RunnerCommandType.ACTION ? 5 : 3);
		RunnerCommand.RunnerCommandBuilder command = RunnerCommand.builder()
				.type(RunnerCommandType.fromName(parts[0]))
				.token(parts[0]);

		for (int index = 1; index < parts.length; index++) command.argument(parts[index]);

		return command.build();
	}
}
