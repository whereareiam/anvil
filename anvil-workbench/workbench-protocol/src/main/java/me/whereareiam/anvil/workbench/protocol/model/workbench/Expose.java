package me.whereareiam.anvil.workbench.protocol.model.workbench;

import lombok.Value;
import me.whereareiam.anvil.workbench.protocol.model.Done;
import org.jetbrains.annotations.NotNull;

/**
 * Makes a service on the engine's loopback reachable from the workbench at the same port.
 */
@Value
public class Expose implements WorkbenchSetupRequest<Done> {
	/**
	 * Loopback port of the service.
	 */
	int port;

	@Override
	public @NotNull Class<Done> responseType() {
		return Done.class;
	}
}
