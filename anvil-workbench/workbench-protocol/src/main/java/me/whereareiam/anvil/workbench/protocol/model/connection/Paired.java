package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The workbench paired with the engine.
 */
@Value
public class Paired implements ConnectionAnswer {
	/**
	 * The engine's new token.
	 */
	@NotNull String token;

	/**
	 * Name of the workbench.
	 */
	@NotNull String name;
}
