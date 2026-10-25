package me.whereareiam.anvil.workbench.protocol.model.connection;

import lombok.Value;
import org.jetbrains.annotations.NotNull;

/**
 * The workbench refuses the connection.
 */
@Value
public class Refused implements ConnectionAnswer {
	/**
	 * Why.
	 */
	@NotNull String reason;
}
