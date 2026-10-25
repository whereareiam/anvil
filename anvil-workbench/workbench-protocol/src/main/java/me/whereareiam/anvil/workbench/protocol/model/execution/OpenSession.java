package me.whereareiam.anvil.workbench.protocol.model.execution;

import lombok.Value;
import me.whereareiam.anvil.environment.execution.api.model.ExecutionContext;
import me.whereareiam.anvil.workbench.protocol.model.Handle;
import org.jetbrains.annotations.NotNull;

/**
 * Opens an execution session with one of the workbench's providers.
 */
@Value
public class OpenSession implements ExecutionRequest<Handle> {
	/**
	 * Identifier of the provider.
	 */
	@NotNull String provider;

	/**
	 * The engine's decisions about the session.
	 */
	@NotNull ExecutionContext context;

	@Override
	public @NotNull Class<Handle> responseType() {
		return Handle.class;
	}
}
