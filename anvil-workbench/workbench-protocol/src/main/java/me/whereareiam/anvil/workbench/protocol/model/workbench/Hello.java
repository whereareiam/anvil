package me.whereareiam.anvil.workbench.protocol.model.workbench;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Opens a session; the workbench refuses an engine that speaks another protocol version.
 */
@Value
public class Hello implements WorkbenchSetupRequest<Greeting> {
	/**
	 * Protocol version the engine speaks.
	 */
	int version;

	@Override
	public @NotNull Class<Greeting> responseType() {
		return Greeting.class;
	}
}
