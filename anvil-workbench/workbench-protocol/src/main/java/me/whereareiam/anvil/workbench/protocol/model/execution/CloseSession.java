package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Closes an execution session.
 */
@Value
public class CloseSession implements ExecutionRequest<Done> {
	/**
	 * Handle of the session.
	 */
	@NotNull String handle;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
