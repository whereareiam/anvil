package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Waits a while for a process to end.
 */
@Value
public class AwaitProcess implements ExecutionRequest<ProcessEnd> {
	/**
	 * Handle of the running process.
	 */
	@NotNull String handle;

	/**
	 * Longest wait in milliseconds.
	 */
	long timeoutMillis;

	@Override
	public @NotNull Class<ProcessEnd> responseType() {
		return ProcessEnd.class;
	}
}
