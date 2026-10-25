package me.whereareiam.anvil.workbench.protocol.model.message;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * A workbench process connected to an exposed port; the engine opens a reverse connection for it.
 */
@Value
public class ReverseConnection implements ServerMessage {
	/**
	 * The waiting connection.
	 */
	@NotNull String id;

	/**
	 * The exposed port.
	 */
	int port;
}
