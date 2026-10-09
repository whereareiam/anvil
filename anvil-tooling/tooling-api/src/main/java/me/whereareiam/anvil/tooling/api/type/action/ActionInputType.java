package me.whereareiam.anvil.tooling.api.type.action;

/**
 * Scalar input types rendered by tooling clients and validated before invocation.
 */
public enum ActionInputType {
	/**
	 * Plain text; required values must contain a non-whitespace character.
	 */
	TEXT,
	/**
	 * A signed 64-bit integer.
	 */
	INTEGER,
	/**
	 * An arbitrary-precision decimal number.
	 */
	DECIMAL,
	/**
	 * True or false, accepted without case sensitivity.
	 */
	BOOLEAN,
	/**
	 * An exact member of the input's declared choices.
	 */
	CHOICE
}
