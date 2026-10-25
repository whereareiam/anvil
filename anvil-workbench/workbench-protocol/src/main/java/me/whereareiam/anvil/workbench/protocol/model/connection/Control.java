package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * Opens the engine's control connection, which carries requests and answers.
 */
@Value
public class Control implements EngineConnection {
	/**
	 * The engine's token.
	 */
	@NotNull String token;
}
