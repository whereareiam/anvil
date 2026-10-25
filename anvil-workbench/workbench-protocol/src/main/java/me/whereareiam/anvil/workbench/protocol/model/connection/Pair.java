package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Pairs an engine through a one-time code.
 */
@Value
public class Pair implements ConnectionHeader {
	/**
	 * The pairing code.
	 */
	@NotNull String code;

	/**
	 * Name the engine gives itself.
	 */
	@NotNull String engine;
}
