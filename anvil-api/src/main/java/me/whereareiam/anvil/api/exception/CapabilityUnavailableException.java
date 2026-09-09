package me.whereareiam.anvil.api.exception;

import org.jetbrains.annotations.NotNull;

import java.io.Serial;

/**
 * Reports that a player or process does not expose a requested capability.
 */
public class CapabilityUnavailableException extends IllegalStateException {
	@Serial
    private static final long serialVersionUID = 1L;

	/**
	 * Creates an unavailable-capability failure.
	 *
	 * @param message diagnostic message
	 */
	public CapabilityUnavailableException(@NotNull String message) {
		super(message);
	}
}
