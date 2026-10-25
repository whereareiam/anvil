package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import me.whereareiam.anvil.environment.execution.api.model.process.ProcessRequest;
import org.jetbrains.annotations.NotNull;

/**
 * Prepares a process in an execution session.
 */
@Value
public class PrepareTarget implements ExecutionRequest<PreparedTarget> {
	/**
	 * Handle of the session.
	 */
	@NotNull String session;

	/**
	 * What to prepare.
	 */
	@NotNull ProcessRequest request;

	@Override
	public @NotNull Class<PreparedTarget> responseType() {
		return PreparedTarget.class;
	}
}
