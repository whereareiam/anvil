package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Carries a connection a workbench process made to an exposed port back to the engine.
 */
@Value
public class Reverse implements EngineConnection {
	/**
	 * The engine's token.
	 */
	@NotNull String token;

	/**
	 * The waiting connection.
	 */
	@NotNull String id;
}
