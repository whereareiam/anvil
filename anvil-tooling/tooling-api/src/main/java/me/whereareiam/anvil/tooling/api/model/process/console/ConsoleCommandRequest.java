package me.whereareiam.anvil.tooling.api.model.process.console;

import lombok.Builder;
import lombok.Value;
import lombok.extern.jackson.Jacksonized;
import org.jetbrains.annotations.NotNull;

/**
 * Submits a command to one process console.
 */
@Value
@Builder
@Jacksonized
public class ConsoleCommandRequest {
	/**
	 * Stable process name receiving the command.
	 */
	@NotNull String target;

	/**
	 * Command text submitted to the process console.
	 */
	@NotNull String text;
}
