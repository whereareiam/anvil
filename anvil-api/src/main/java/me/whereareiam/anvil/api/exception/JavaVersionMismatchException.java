package me.whereareiam.anvil.api.exception;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * Reports that a Java runtime is a different feature version than the process requires. Planning selects one
 * exact feature version per process, and execution never runs a process on another one.
 *
 * <p>Whoever chose the runtime names the remedy, because only it knows where the runtime came from: a local
 * Java source, an archive, or an image of an execution provider. Other provisioning failures, such as output
 * that cannot be read or a distribution mismatch, stay plain {@link ProvisioningException}s.</p>
 *
 * <pre>{@code
 * try {
 *     validator.validate(probe, request);
 * } catch (JavaVersionMismatchException mismatch) {
 *     throw new ProvisioningException(mismatch.getMessage() + "; map temurin:21 to an image that ships Java 21", mismatch);
 * }
 * }</pre>
 */
public class JavaVersionMismatchException extends ProvisioningException {
	@Serial
	private static final long serialVersionUID = 1L;

	/**
	 * Creates a mismatch failure.
	 *
	 * @param message diagnostic message naming the runtime and the required feature version
	 */
	public JavaVersionMismatchException(@NotNull String message) {
		super(message);
	}
}
