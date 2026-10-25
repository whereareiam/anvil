package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Carries the standard output and input of a process.
 */
@Value
public class Stdio implements EngineConnection {
	/**
	 * The engine's token.
	 */
	@NotNull String token;

	/**
	 * Handle of the process.
	 */
	@NotNull String handle;
}
