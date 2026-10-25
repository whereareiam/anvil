package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Carries the standard error of a JVM started outside managed execution.
 */
@Value
public class Stderr implements EngineConnection {
	/**
	 * The engine's token.
	 */
	@NotNull String token;

	/**
	 * Handle of the JVM.
	 */
	@NotNull String handle;
}
