package me.whereareiam.anvil.engine;

/**
 * Runtime failure raised while validating, provisioning, or running a scenario.
 */
public class AnvilException extends RuntimeException {
	/**
	 * Creates a failure with a diagnostic message.
	 *
	 * @param message diagnostic message
	 */
	public AnvilException(String message) {
		super(message);
	}

	/**
	 * Creates a failure with its root cause.
	 *
	 * @param message diagnostic message
	 * @param cause root cause
	 */
	public AnvilException(String message, Throwable cause) {
		super(message, cause);
	}
}
