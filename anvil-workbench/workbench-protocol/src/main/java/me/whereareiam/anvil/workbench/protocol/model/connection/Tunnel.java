package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Carries a connection to a port on the workbench's loopback.
 */
@Value
public class Tunnel implements EngineConnection {
	/**
	 * The engine's token.
	 */
	@NotNull String token;

	/**
	 * The port.
	 */
	int port;
}
