package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Releases a prepared process.
 */
@Value
public class CloseTarget implements ExecutionRequest<Done> {
	/**
	 * Handle of the target.
	 */
	@NotNull String handle;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
