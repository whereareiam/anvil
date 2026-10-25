package me.whereareiam.anvil.workbench.protocol.model.connection;

import org.jetbrains.annotations.NotNull;

/**
 * A connection a paired engine opens, which carries the token the workbench issued it.
 */
public sealed interface EngineConnection extends ConnectionHeader permits Control, Stdio, Stderr, Upload, Tunnel, Reverse {
	/**
	 * Returns the engine's token.
	 *
	 * @return token the workbench issued when pairing
	 */
	@NotNull String getToken();
}
