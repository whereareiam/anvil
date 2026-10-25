package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Runs a command inside a container process.
 */
@Value
public class ExecuteInProcess implements ExecutionRequest<CommandOutput> {
	/**
	 * Handle of the running process.
	 */
	@NotNull String handle;

	/**
	 * Longest wait in milliseconds.
	 */
	long timeoutMillis;

	/**
	 * Command and its arguments.
	 */
	@NotNull List<String> command;

	@Override
	public @NotNull Class<CommandOutput> responseType() {
		return CommandOutput.class;
	}
}
