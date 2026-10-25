package me.whereareiam.anvil.workbench.protocol;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Constants both ends of a workbench connection agree on. Every connection is a TLS socket whose first line is a
 * {@link me.whereareiam.anvil.workbench.protocol.model.connection.ConnectionHeader header} naming what it is for; a
 * control connection then carries requests and answers, one per line, and every other connection raw bytes.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class WireProtocol {
	/**
	 * Version both ends must speak; a workbench refuses an engine of another version.
	 */
	public static final int VERSION = 1;

	/**
	 * Port a workbench listens on unless its operator chooses another.
	 */
	public static final int DEFAULT_PORT = 7400;
}
