package me.whereareiam.anvil.workbench.protocol;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Constants both ends of a workbench connection agree on. Every connection is a TLS socket whose first line is a
 * JSON header naming its {@link #KIND kind}; a control connection then carries JSON requests and responses, one per
 * line, and every other kind carries raw bytes.
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

	/**
	 * Header member naming the kind of connection.
	 */
	public static final String KIND = "kind";

	/**
	 * Header member carrying the paired engine's token.
	 */
	public static final String TOKEN = "token";

	/**
	 * A one-time pairing: the engine sends a pairing code and receives its token.
	 */
	public static final String PAIR = "pair";

	/**
	 * Requests and responses of one engine.
	 */
	public static final String CONTROL = "control";

	/**
	 * The standard streams of a started process or JVM, named by its handle.
	 */
	public static final String STDIO = "stdio";

	/**
	 * The standard error of a JVM started outside managed execution.
	 */
	public static final String STDERR = "stderr";

	/**
	 * A file or zipped directory the engine stores on the workbench.
	 */
	public static final String UPLOAD = "upload";

	/**
	 * A connection the engine makes to an address on the workbench.
	 */
	public static final String TUNNEL = "tunnel";

	/**
	 * A connection a workbench process made to an address the engine exposed, carried back to the engine.
	 */
	public static final String REVERSE = "reverse";
}
